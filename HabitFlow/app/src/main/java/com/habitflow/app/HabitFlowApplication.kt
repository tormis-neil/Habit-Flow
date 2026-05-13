package com.habitflow.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Constraints
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.habitflow.app.worker.HabitSyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * FEATURE A — Architecture & Structure
 *
 * HabitFlowApplication is the entry point of the app.
 * Android creates one instance of this class when the app starts,
 * and it lives for the entire lifetime of the app.
 */
@HiltAndroidApp
class HabitFlowApplication : Application(), Configuration.Provider {

    @Inject
    lateinit val workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleSyncWorker()
    }

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Minimum periodic interval is 15 minutes in WorkManager
        val syncRequest = PeriodicWorkRequestBuilder<HabitSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HabitSyncWorker",
            ExistingPeriodicWorkPolicy.KEEP, // KEEP means if it's already scheduled, don't replace it
            syncRequest
        )
    }
}
