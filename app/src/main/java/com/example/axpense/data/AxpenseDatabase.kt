package com.example.axpense.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Expense::class], version = 1, exportSchema = false)
abstract class AxpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AxpenseDatabase? = null

        fun getDatabase(context: Context): AxpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AxpenseDatabase::class.java,
                    "axpense_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
