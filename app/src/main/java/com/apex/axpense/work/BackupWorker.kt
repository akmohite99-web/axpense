package com.apex.axpense.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apex.axpense.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uploads unsynced rows. Runs hourly and right after local changes. */
class BackupWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            applicationContext.container.repository.backupAllData()
            Result.success()
        } catch (e: Exception) {
            Log.w("BackupWorker", "Backup failed, will retry", e)
            Result.retry()
        }
    }
}
