package com.apex.axpense.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Expense::class, Category::class], version = 3, exportSchema = false)
abstract class AxpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AxpenseDatabase? = null
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add subCategory to expenses
                database.execSQL("ALTER TABLE expenses ADD COLUMN subCategory TEXT")
                
                // Create categories table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`parentCategory` TEXT, " +
                    "`userId` TEXT NOT NULL)"
                )
                
                // Insert default categories
                val defaultCategories = listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Other")
                for (cat in defaultCategories) {
                    database.execSQL("INSERT INTO categories (name, userId) VALUES ('$cat', '')")
                }
            }
        }

        fun getDatabase(context: Context): AxpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AxpenseDatabase::class.java,
                    "axpense_database"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
