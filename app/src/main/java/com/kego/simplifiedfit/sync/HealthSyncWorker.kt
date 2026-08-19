package com.kego.simplifiedfit.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kego.simplifiedfit.SimplifiedFitApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class HealthSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as SimplifiedFitApplication
        if (app.secureStore.googleCredentials() == null) return@withContext Result.success()
        runCatching { app.healthRepository.sync() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { error ->
                    if (inputData.getBoolean(KEY_MANUAL, false)) {
                        Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Could not sync Google Health")))
                    } else {
                        Result.retry()
                    }
                },
            )
    }

    companion object {
        const val KEY_ERROR = "error"
        const val MANUAL_WORK = "google-health-manual-sync"
        private const val KEY_MANUAL = "manual"
        private const val PERIODIC_WORK = "google-health-sync"

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInputData(workDataOf(KEY_MANUAL to true))
                .setConstraints(connectedNetwork())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_WORK,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(connectedNetwork())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun connectedNetwork() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
