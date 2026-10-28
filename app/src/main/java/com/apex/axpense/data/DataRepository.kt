package com.apex.axpense.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.apex.axpense.work.BackupWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Room is the source of truth. Local changes are marked `isSynced = false` and uploaded
 * by [BackupWorker] to Firestore documents keyed by `remoteId`, so uploads are idempotent.
 */
class DataRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AxpenseDatabase.getDatabase(appContext)
    private val expenseDao = db.expenseDao()
    private val categoryDao = db.categoryDao()
    private val prefs = appContext.getSharedPreferences("axpense_sync", Context.MODE_PRIVATE)

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val expensesCol get() = firestore.collection("expenses")
    private val categoriesCol get() = firestore.collection("categories")

    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val totalSpent: Flow<Double?> = expenseDao.getTotalSpent()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun addExpense(expense: Expense) {
        expenseDao.insert(expense.copy(userId = auth.currentUser?.uid ?: expense.userId, isSynced = false))
        requestSync()
    }

    suspend fun addCategory(category: Category) {
        categoryDao.insert(category.copy(userId = auth.currentUser?.uid ?: category.userId, isSynced = false))
        requestSync()
    }

    /** How many expenses would be moved if this category were deleted. */
    suspend fun countExpensesIn(name: String, parent: String?): Int =
        if (parent == null) expenseDao.countInCategory(name)
        else expenseDao.countInSubCategory(parent, name)

    /**
     * Deletes a main category (and its sub-categories), moving its expenses to "Others",
     * or deletes one sub-category, clearing it from its expenses.
     */
    suspend fun deleteCategory(name: String, parent: String?) {
        if (parent == null && name == Category.FALLBACK) return

        val removed = db.withTransaction {
            if (parent == null) {
                val rows = categoryDao.getWithChildren(name)
                expenseDao.moveToFallback(name)
                categoryDao.deleteWithChildren(name)
                rows
            } else {
                val rows = categoryDao.getSub(name, parent)
                expenseDao.clearSubCategory(parent, name)
                categoryDao.deleteSub(name, parent)
                rows
            }
        }
        requestSync() // re-uploads the moved expenses

        val uid = auth.currentUser?.uid ?: return
        try {
            // Firestore queues these writes offline, so they don't need to be awaited.
            removed.forEach { categoriesCol.document(it.remoteId).delete() }
            // Old-format copies (random doc IDs) would otherwise come back on restore.
            categoriesCol.whereEqualTo("userId", uid).whereEqualTo("name", name).get().await()
                .documents
                .filter { it.getString("parentCategory") == parent }
                .forEach { it.reference.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud category delete failed", e)
        }
    }

    /** Uploads everything not yet synced. Throws on failure so the worker retries. */
    suspend fun backupAllData() {
        val uid = auth.currentUser?.uid ?: return

        val categories = categoryDao.getUnsynced()
        commitInChunks(categories.map { categoriesCol.document(it.remoteId) to it.toFirestore(uid) }) { ids ->
            categoryDao.markSynced(ids)
        }
        val expenses = expenseDao.getUnsynced()
        commitInChunks(expenses.map { expensesCol.document(it.remoteId) to it.toFirestore(uid) }) { ids ->
            expenseDao.markSynced(ids)
        }

        removeLegacyCloudDuplicates(uid)
    }

    /** Firestore batches allow at most 500 writes. */
    private suspend fun commitInChunks(
        writes: List<Pair<DocumentReference, Map<String, Any?>>>,
        onCommitted: suspend (List<String>) -> Unit
    ) {
        writes.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
            onCommitted(chunk.map { it.first.id })
        }
    }

    /**
     * One-time cleanup of the duplicates the old hourly backup created (a new random doc
     * every hour). A legacy doc is only deleted once its data is safely uploaded under its
     * stable ID.
     */
    private suspend fun removeLegacyCloudDuplicates(uid: String) {
        val flag = "legacy_cleanup_done_$uid"
        if (prefs.getBoolean(flag, false)) return
        // Restore must have pulled the legacy docs into Room first, so nothing is lost.
        if (!prefs.getBoolean("restored_$uid", false)) return

        val syncedExpenses = expenseDao.getSyncedRemoteIds().toHashSet()
        val syncedCategories = categoryDao.getSyncedRemoteIds().toHashSet()

        val expenseDocs = expensesCol.whereEqualTo("userId", uid).get().await().documents
        val categoryDocs = categoriesCol.whereEqualTo("userId", uid).get().await().documents

        val toDelete = expenseDocs.filter { doc ->
            doc.isLegacyDoc() && doc.toExpense()?.remoteId in syncedExpenses
        } + categoryDocs.filter { doc ->
            doc.isLegacyDoc() && doc.toCategory()?.remoteId in syncedCategories
        }

        toDelete.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        if (toDelete.isNotEmpty()) Log.i(TAG, "Removed ${toDelete.size} duplicate cloud docs")

        prefs.edit { putBoolean(flag, true) }
    }

    /** Pulls the user's data from Firestore once per sign-in. Local rows are never overwritten. */
    suspend fun restoreFromCloudIfNeeded() {
        val uid = auth.currentUser?.uid ?: return
        val flag = "restored_$uid"
        if (prefs.getBoolean(flag, false)) return
        try {
            val categoryDocs = categoriesCol.whereEqualTo("userId", uid).get().await().documents
            // Current-format docs first, so they win over old duplicates with the same ID.
            categoryDao.insertIfAbsent(
                categoryDocs.sortedBy { it.isLegacyDoc() }.mapNotNull { it.toCategory() }
            )

            val expenseDocs = expensesCol.whereEqualTo("userId", uid).get().await().documents
            expenseDao.insertIfAbsent(
                expenseDocs.sortedBy { it.isLegacyDoc() }.mapNotNull { it.toExpense() }
            )

            prefs.edit { putBoolean(flag, true) }
            requestSync() // re-upload any legacy-only rows under their stable IDs
        } catch (e: Exception) {
            Log.w(TAG, "Restore from cloud failed; will retry next launch", e)
        }
    }

    suspend fun unsyncedCount(): Int = expenseDao.countUnsynced()

    /** Removes everything stored locally for the signed-in user. */
    suspend fun clearLocalData() {
        WorkManager.getInstance(appContext).cancelUniqueWork(SYNC_WORK)
        db.withTransaction {
            expenseDao.deleteAll()
            categoryDao.deleteAll()
            categoryDao.insertIfAbsent(Category.DEFAULTS.map { Category(name = it) })
        }
        prefs.edit { clear() }
    }

    fun requestSync() {
        if (auth.currentUser == null) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        private const val TAG = "DataRepository"
        private const val BATCH_SIZE = 450
        private const val SYNC_WORK = "SyncNow"
    }
}
