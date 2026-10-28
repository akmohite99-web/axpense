package com.apex.axpense

import android.app.Application
import androidx.work.*
import com.apex.axpense.work.BackupWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleHourlyBackup()
    }

    private fun scheduleHourlyBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag("hourly_backup")
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "HourlyBackup",
                ExistingPeriodicWorkPolicy.KEEP,
                backupRequest
            )
    }
}
