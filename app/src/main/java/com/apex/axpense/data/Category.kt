package com.apex.axpense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val parentCategory: String? = null,
    val userId: String = "",
    // A category is identified by (parent, name), so the same category created on
    // two devices maps to the same Firestore document.
    @ColumnInfo(defaultValue = "''") val remoteId: String = key(name, parentCategory),
    @ColumnInfo(defaultValue = "0") val isSynced: Boolean = false
) {
    companion object {
        /** Expenses from deleted categories move here. It can't be deleted. */
        const val FALLBACK = "Others"

        val DEFAULTS = listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", FALLBACK)

        /** Must match the SQL in AxpenseDatabase.MIGRATION_3_4. */
        fun key(name: String, parent: String?): String =
            "cat|${(parent ?: "").replace('/', '_')}|${name.replace('/', '_')}"
    }
}
