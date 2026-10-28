package com.apex.axpense.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Expense::class, Category::class], version = 5, exportSchema = true)
abstract class AxpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AxpenseDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add subCategory to expenses
                db.execSQL("ALTER TABLE expenses ADD COLUMN subCategory TEXT")

                // Create categories table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`parentCategory` TEXT, " +
                    "`userId` TEXT NOT NULL)"
                )

                // Insert default categories
                val defaultCategories = listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Other")
                for (cat in defaultCategories) {
                    db.execSQL("INSERT INTO categories (name, userId) VALUES ('$cat', '')")
                }
            }
        }

        /**
         * Adds stable remoteId + isSynced columns and renames "Other" to "Others".
         * The remoteId backfill must match Expense.legacyKey() and Category.key().
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val fallback = Category.FALLBACK

                // --- "Other" -> "Others" ---
                db.execSQL("UPDATE expenses SET category = '$fallback' WHERE category = 'Other'")
                db.execSQL("UPDATE categories SET parentCategory = '$fallback' WHERE parentCategory = 'Other'")
                db.execSQL(
                    "DELETE FROM categories WHERE name = 'Other' AND parentCategory IS NULL " +
                    "AND EXISTS (SELECT 1 FROM categories WHERE name = '$fallback' AND parentCategory IS NULL)"
                )
                db.execSQL("UPDATE categories SET name = '$fallback' WHERE name = 'Other' AND parentCategory IS NULL")
                db.execSQL(
                    "INSERT INTO categories (name, parentCategory, userId) SELECT '$fallback', NULL, '' " +
                    "WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '$fallback' AND parentCategory IS NULL)"
                )

                // --- expenses: remoteId + isSynced ---
                db.execSQL("ALTER TABLE expenses ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE expenses SET remoteId = " +
                    "'legacy-' || timestamp || '-' || CAST(ROUND(amount * 100) AS INTEGER)"
                )
                // Two rows with the same time and amount: keep them both, make the IDs unique.
                db.execSQL(
                    "UPDATE expenses SET remoteId = remoteId || '-' || id " +
                    "WHERE id NOT IN (SELECT MIN(id) FROM expenses GROUP BY remoteId)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_remoteId` ON `expenses` (`remoteId`)")

                // --- categories: drop duplicates, then remoteId + isSynced ---
                db.execSQL(
                    "DELETE FROM categories WHERE id NOT IN " +
                    "(SELECT MIN(id) FROM categories GROUP BY name, COALESCE(parentCategory, ''))"
                )
                db.execSQL("ALTER TABLE categories ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE categories ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE categories SET remoteId = 'cat|' || " +
                    "REPLACE(COALESCE(parentCategory, ''), '/', '_') || '|' || REPLACE(name, '/', '_')"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_remoteId` ON `categories` (`remoteId`)")
            }
        }

        /** Adds the review status; every existing expense stays "confirmed". */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN status TEXT NOT NULL DEFAULT 'confirmed'")
            }
        }

        private val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                Category.DEFAULTS.forEach { name ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO categories (name, parentCategory, userId, remoteId, isSynced) VALUES (?, NULL, '', ?, 0)",
                        arrayOf(name, Category.key(name, null))
                    )
                }
            }
        }

        fun getDatabase(context: Context): AxpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AxpenseDatabase::class.java,
                    "axpense_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(seedCallback)
                    // Only the very first schema (no migration was ever written) may be wiped.
                    // Any future version bump must add a Migration, or the app will crash
                    // instead of silently deleting the user's expenses.
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
