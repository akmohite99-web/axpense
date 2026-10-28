package com.apex.axpense.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The category an earlier expense from the same merchant was filed under. */
data class CategoryGuess(val category: String, val subCategory: String?)

/** A category (or sub-category) name with the total spent in it. */
data class CategoryTotal(val name: String, val total: Double)

@Dao
interface ExpenseDao {
    // ---- Home screen: everything for a period [start, end) in epoch millis ----

    @Query("SELECT * FROM expenses WHERE status = 'confirmed' AND timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun getBetween(start: Long, end: Long): Flow<List<Expense>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE status = 'confirmed' AND timestamp >= :start AND timestamp < :end")
    fun totalBetween(start: Long, end: Long): Flow<Double>

    @Query(
        "SELECT category AS name, SUM(amount) AS total FROM expenses " +
            "WHERE status = 'confirmed' AND timestamp >= :start AND timestamp < :end " +
            "GROUP BY category ORDER BY total DESC"
    )
    fun categoryTotals(start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query(
        "SELECT COALESCE(subCategory, 'Uncategorized') AS name, SUM(amount) AS total FROM expenses " +
            "WHERE status = 'confirmed' AND category = :category AND timestamp >= :start AND timestamp < :end " +
            "GROUP BY COALESCE(subCategory, 'Uncategorized') ORDER BY total DESC"
    )
    fun subCategoryTotals(category: String, start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query("SELECT MIN(timestamp) FROM expenses WHERE status = 'confirmed'")
    fun earliestTimestamp(): Flow<Long?>

    // ---- Writing and syncing ----

    @Query("SELECT * FROM expenses WHERE status = 'pending' ORDER BY timestamp DESC")
    fun getPending(): Flow<List<Expense>>

    /** Saving an expense with an existing remoteId (e.g. confirming a pending one) replaces it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense)

    /** Returns -1 if a row with this remoteId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDetected(expense: Expense): Long

    /** Used by cloud restore: rows that already exist locally (same remoteId) are kept as-is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(expenses: List<Expense>): List<Long>

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: String)

    @Query("SELECT * FROM expenses WHERE isSynced = 0 AND status = 'confirmed'")
    suspend fun getUnsynced(): List<Expense>

    @Query("SELECT COUNT(*) FROM expenses WHERE isSynced = 0 AND status = 'confirmed'")
    suspend fun countUnsynced(): Int

    @Query("SELECT remoteId FROM expenses WHERE isSynced = 1")
    suspend fun getSyncedRemoteIds(): List<String>

    @Query("UPDATE expenses SET isSynced = 1 WHERE remoteId IN (:remoteIds)")
    suspend fun markSynced(remoteIds: List<String>)

    @Query("SELECT COUNT(*) FROM expenses WHERE category = :category AND status = 'confirmed'")
    suspend fun countInCategory(category: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE category = :parent AND subCategory = :subCategory AND status = 'confirmed'")
    suspend fun countInSubCategory(parent: String, subCategory: String): Int

    @Query("UPDATE expenses SET category = '${Category.FALLBACK}', subCategory = NULL, isSynced = 0 WHERE category = :category")
    suspend fun moveToFallback(category: String)

    @Query("UPDATE expenses SET subCategory = NULL, isSynced = 0 WHERE category = :parent AND subCategory = :subCategory")
    suspend fun clearSubCategory(parent: String, subCategory: String)

    @Query("UPDATE expenses SET status = 'dismissed' WHERE remoteId = :remoteId AND status = 'pending'")
    suspend fun dismiss(remoteId: String)

    // ---- Duplicate detection (any status) ----

    @Query("SELECT COUNT(*) FROM expenses WHERE remoteId = :remoteId")
    suspend fun countByRemoteId(remoteId: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE ABS(amount - :amount) < 0.005 AND timestamp BETWEEN :from AND :to")
    suspend fun countNear(amount: Double, from: Long, to: Long): Int

    // ---- Category guessing ----

    @Query(
        "SELECT category, subCategory FROM expenses " +
            "WHERE status = 'confirmed' AND description = :merchant COLLATE NOCASE " +
            "ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun categoryForMerchant(merchant: String): CategoryGuess?

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}
