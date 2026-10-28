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
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Room is the source of truth. Local changes are marked `isSynced = false` and uploaded
 * by [BackupWorker] to Firestore documents keyed by `remoteId`, so uploads are idempotent.
 */
class DataRepository(
    context: Context,
    private val db: AxpenseDatabase,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private val appContext = context.applicationContext
    private val expenseDao = db.expenseDao()
    private val categoryDao = db.categoryDao()
    private val prefs = appContext.getSharedPreferences("axpense_sync", Context.MODE_PRIVATE)

    private val expensesCol get() = firestore.collection("expenses")
    private val categoriesCol get() = firestore.collection("categories")

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    // The Home screen's numbers are computed by SQLite for the chosen [start, end) range.
    fun expensesBetween(start: Long, end: Long): Flow<List<Expense>> = expenseDao.getBetween(start, end)
    fun totalBetween(start: Long, end: Long): Flow<Double> = expenseDao.totalBetween(start, end)
    fun categoryTotals(start: Long, end: Long): Flow<List<CategoryTotal>> = expenseDao.categoryTotals(start, end)
    fun subCategoryTotals(category: String, start: Long, end: Long): Flow<List<CategoryTotal>> =
        expenseDao.subCategoryTotals(category, start, end)
    val earliestTimestamp: Flow<Long?> = expenseDao.earliestTimestamp()
    val pendingExpenses: Flow<List<Expense>> = expenseDao.getPending()

    /**
     * True if this SMS (or an expense with the same amount at about the same time) is
     * already stored, whether confirmed, pending or dismissed.
     */
    suspend fun isKnownSms(body: String, amount: Double, timestamp: Long): Boolean =
        expenseDao.countByRemoteId(Expense.smsKey(body)) > 0 ||
            expenseDao.countNear(amount, timestamp - DUPLICATE_WINDOW_MS, timestamp + DUPLICATE_WINDOW_MS) > 0

    /** Category used last time for this merchant, if any. */
    suspend fun guessCategory(merchant: String?): CategoryGuess? =
        merchant?.let { expenseDao.categoryForMerchant(it) }

    /**
     * Stores an expense found in a notification as PENDING so it isn't lost if the
     * notification is swiped away. Returns null if it was already known.
     */
    suspend fun recordDetected(
        amount: Double,
        merchant: String?,
        body: String,
        timestamp: Long,
        sourceApp: String
    ): Expense? {
        if (isKnownSms(body, amount, timestamp)) return null
        val guess = guessCategory(merchant)
        val expense = Expense(
            amount = amount,
            description = merchant ?: snippet(body),
            category = guess?.category ?: Category.FALLBACK,
            subCategory = guess?.subCategory,
            timestamp = timestamp,
            sourceApp = sourceApp,
            userId = auth.currentUser?.uid ?: "",
            remoteId = Expense.smsKey(body),
            status = Expense.PENDING
        )
        return if (expenseDao.insertDetected(expense) == -1L) null else expense
    }

    suspend fun dismissPending(remoteId: String) = expenseDao.dismiss(remoteId)

    /** Deletes an expense here and in the cloud. Undo by passing the same expense to [addExpense]. */
    suspend fun deleteExpense(remoteId: String) {
        expenseDao.deleteByRemoteId(remoteId)
        if (auth.currentUser != null) {
            // Not awaited: Firestore keeps the delete queued and sends it once back online.
            expensesCol.document(remoteId).delete()
                .addOnFailureListener { Log.w(TAG, "Cloud delete failed for $remoteId", it) }
        }
    }

    suspend fun addExpense(expense: Expense) {
        expenseDao.insert(
            expense.copy(
                userId = auth.currentUser?.uid ?: expense.userId,
                isSynced = false,
                status = Expense.CONFIRMED
            )
        )
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

    /** What a restore found in the cloud and what it added to this device. */
    data class RestoreResult(val cloudExpenses: Int = 0, val added: Int = 0, val error: String? = null)

    /**
     * Pulls the user's data from Firestore once per sign-in (or on demand with [force]).
     * Local rows are never overwritten. Returns null if there was nothing to do.
     */
    suspend fun restoreFromCloudIfNeeded(force: Boolean = false): RestoreResult? {
        val uid = auth.currentUser?.uid ?: return null
        val flag = "restored_$uid"
        if (!force && prefs.getBoolean(flag, false)) return null
        return try {
            val categoryDocs = categoriesCol.whereEqualTo("userId", uid).get().await().documents
            // Current-format docs first, so they win over old duplicates with the same ID.
            categoryDao.insertIfAbsent(
                categoryDocs.sortedBy { it.isLegacyDoc() }.mapNotNull { it.toCategory() }
            )

            val expenseDocs = expensesCol.whereEqualTo("userId", uid).get().await().documents
            val inserted = expenseDao.insertIfAbsent(
                expenseDocs.sortedBy { it.isLegacyDoc() }.mapNotNull { it.toExpense() }
            )
            Log.i(TAG, "Restore: ${expenseDocs.size} cloud expenses, ${inserted.count { it != -1L }} added")

            prefs.edit { putBoolean(flag, true) }
            requestSync() // re-upload any legacy-only rows under their stable IDs
            RestoreResult(expenseDocs.size, inserted.count { it != -1L })
        } catch (e: Exception) {
            Log.w(TAG, "Restore from cloud failed; will retry next launch", e)
            val code = (e as? FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            RestoreResult(error = code)
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
        /** The same SMS reaches us as a notification and in the inbox a moment apart. */
        private const val DUPLICATE_WINDOW_MS = 2 * 60 * 1000L

        /** Short description for an SMS where no merchant could be found. */
        fun snippet(body: String): String =
            body.replace(Regex("\\s+"), " ").trim().take(60)

        private const val TAG = "DataRepository"
        private const val BATCH_SIZE = 450
        private const val SYNC_WORK = "SyncNow"
    }
}
