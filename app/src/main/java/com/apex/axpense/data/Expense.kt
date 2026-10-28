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
    @ColumnInfo(defaultValue = "0") val isSynced: Boolean = false,
    // CONFIRMED rows are real expenses. PENDING rows were detected from an SMS and wait for
    // review; DISMISSED rows were rejected (kept so the same SMS isn't offered again).
    // Only CONFIRMED rows are counted and uploaded.
    @ColumnInfo(defaultValue = "'confirmed'") val status: String = CONFIRMED
) {
    companion object {
        const val CONFIRMED = "confirmed"
        const val PENDING = "pending"
        const val DISMISSED = "dismissed"

        /** Stable ID for an SMS, so the same message is never added twice (notification + inbox scan). */
        fun smsKey(body: String): String {
            val normalized = body.trim().replace(Regex("\\s+"), " ")
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
            return "sms-" + digest.joinToString("") { "%02x".format(it) }.take(24)
        }

        /**
         * ID for expenses created before remoteId existed. Must match the SQL in
         * AxpenseDatabase.MIGRATION_3_4 so old cloud copies collapse onto local rows.
         */
        fun legacyKey(timestamp: Long, amount: Double): String =
            "legacy-$timestamp-${Math.round(amount * 100)}"
    }
}
