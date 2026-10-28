package com.apex.axpense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val description: String,
    val category: String,
    val subCategory: String? = null,
    val timestamp: Long,
    val sourceApp: String, // e.g. "manual", "sms_scan", or the SMS app's package name
    val userId: String = "",
    // Stable ID shared with Firestore (used as the document ID), so backups never duplicate.
    @ColumnInfo(defaultValue = "''") val remoteId: String = UUID.randomUUID().toString(),
    // False until this row has been uploaded to Firestore.
    @ColumnInfo(defaultValue = "0") val isSynced: Boolean = false
) {
    companion object {
        /**
         * ID for expenses created before remoteId existed. Must match the SQL in
         * AxpenseDatabase.MIGRATION_3_4 so old cloud copies collapse onto local rows.
         */
        fun legacyKey(timestamp: Long, amount: Double): String =
            "legacy-$timestamp-${Math.round(amount * 100)}"
    }
}
