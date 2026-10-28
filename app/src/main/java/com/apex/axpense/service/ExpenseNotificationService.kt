package com.apex.axpense.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.apex.axpense.MainActivity
import com.apex.axpense.data.Category
import com.apex.axpense.container
import com.apex.axpense.data.Expense
import com.apex.axpense.util.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExpenseNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (!isSmsApp(packageName)) return

        val notification = sbn.notification
        // The group summary repeats the text of the individual messages.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val txn = ExpenseParser.parse(text) ?: return
        val postedAt = sbn.postTime

        scope.launch {
            try {
                val saved = applicationContext.container.repository
                    .recordDetected(txn.amount, txn.merchant, text, postedAt, packageName)
                    ?: return@launch // already known (e.g. the notification was updated)
                showExpenseNotification(saved, txn.currency)
            } catch (e: Exception) {
                Log.w(TAG, "Could not record detected expense", e)
            }
        }
    }

    private fun isSmsApp(packageName: String): Boolean =
        packageName == "com.google.android.apps.messaging" ||
            packageName == "com.samsung.android.messaging" ||
            packageName == "com.android.mms" ||
            packageName.contains("sms", ignoreCase = true) ||
            packageName.contains("mms", ignoreCase = true) ||
            packageName.contains("messaging", ignoreCase = true)

    private fun showExpenseNotification(expense: Expense, currency: String?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Expense Detections",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null) }
            manager.createNotificationChannel(channel)
        }

        val notificationId = expense.remoteId.hashCode()

        // Opens Add Expense filled in; saving it confirms the pending expense.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_AMOUNT, expense.amount)
            putExtra(MainActivity.EXTRA_DESC, expense.description)
            putExtra(MainActivity.EXTRA_TIMESTAMP, expense.timestamp)
            putExtra(MainActivity.EXTRA_SOURCE, expense.sourceApp)
            putExtra(MainActivity.EXTRA_REMOTE_ID, expense.remoteId)
            putExtra(MainActivity.EXTRA_CATEGORY, expense.category)
            putExtra(MainActivity.EXTRA_SUB_CATEGORY, expense.subCategory)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Expense detected: ${formatMoney(expense.amount, currency)}")
            .setContentText(
                if (expense.category == Category.FALLBACK) "Tap to categorize this expense."
                else "${expense.description} · ${expense.category}. Tap to review."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(null)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    private companion object {
        const val TAG = "ExpenseNotifService"
        const val CHANNEL_ID = "axpense_notifications_silent"
    }
}
