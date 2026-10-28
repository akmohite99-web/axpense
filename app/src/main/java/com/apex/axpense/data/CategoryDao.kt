package com.apex.axpense.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    /** Adding a category that already exists (same parent + name) just replaces it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(categories: List<Category>)

    @Query("SELECT * FROM categories WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Category>

    @Query("SELECT remoteId FROM categories WHERE isSynced = 1")
    suspend fun getSyncedRemoteIds(): List<String>

    @Query("UPDATE categories SET isSynced = 1 WHERE remoteId IN (:remoteIds)")
    suspend fun markSynced(remoteIds: List<String>)

    /** A main category plus all of its sub-categories. */
    @Query("SELECT * FROM categories WHERE (name = :name AND parentCategory IS NULL) OR parentCategory = :name")
    suspend fun getWithChildren(name: String): List<Category>

    @Query("SELECT * FROM categories WHERE name = :name AND parentCategory = :parent")
    suspend fun getSub(name: String, parent: String): List<Category>

    @Query("DELETE FROM categories WHERE (name = :name AND parentCategory IS NULL) OR parentCategory = :name")
    suspend fun deleteWithChildren(name: String)

    @Query("DELETE FROM categories WHERE name = :name AND parentCategory = :parent")
    suspend fun deleteSub(name: String, parent: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
