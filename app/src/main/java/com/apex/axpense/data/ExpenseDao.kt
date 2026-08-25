package com.apex.axpense.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
    
    @Query("SELECT SUM(amount) FROM expenses")
    fun getTotalSpent(): Flow<Double?>

    @Query("SELECT * FROM expenses")
    fun getAllExpensesOnce(): List<Expense>

    @Query("UPDATE expenses SET category = 'Others', subCategory = null WHERE category = :categoryName")
    suspend fun updateCategoryToOthers(categoryName: String)

    @Query("UPDATE expenses SET subCategory = 'Others' WHERE subCategory = :subCategoryName")
    suspend fun updateSubCategoryToOthers(subCategoryName: String)
}
