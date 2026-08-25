package com.apex.axpense.work
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apex.axpense.data.AxpenseDatabase
import com.apex.axpense.data.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AxpenseDatabase.getDatabase(applicationContext)
            val repository = DataRepository(db.expenseDao(), db.categoryDao())
            repository.backupAllData()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
