package com.apex.axpense.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert
    suspend fun insert(expense: Expense)

    /** Used by cloud restore: rows that already exist locally (same remoteId) are kept as-is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(expenses: List<Expense>)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT SUM(amount) FROM expenses")
    fun getTotalSpent(): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Expense>

    @Query("SELECT COUNT(*) FROM expenses WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("SELECT remoteId FROM expenses WHERE isSynced = 1")
    suspend fun getSyncedRemoteIds(): List<String>

    @Query("UPDATE expenses SET isSynced = 1 WHERE remoteId IN (:remoteIds)")
    suspend fun markSynced(remoteIds: List<String>)

    @Query("SELECT COUNT(*) FROM expenses WHERE category = :category")
    suspend fun countInCategory(category: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE category = :parent AND subCategory = :subCategory")
    suspend fun countInSubCategory(parent: String, subCategory: String): Int

    @Query("UPDATE expenses SET category = '${Category.FALLBACK}', subCategory = NULL, isSynced = 0 WHERE category = :category")
    suspend fun moveToFallback(category: String)

    @Query("UPDATE expenses SET subCategory = NULL, isSynced = 0 WHERE category = :parent AND subCategory = :subCategory")
    suspend fun clearSubCategory(parent: String, subCategory: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}
