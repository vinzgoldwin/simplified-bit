package com.kego.simplifiedfit.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kego.simplifiedfit.SimplifiedFitApplication
import com.kego.simplifiedfit.data.CoachClient
import com.kego.simplifiedfit.data.CoachEvent
import com.kego.simplifiedfit.data.CoachProgress
import com.kego.simplifiedfit.data.CoachProvider
import com.kego.simplifiedfit.data.CoachRequest
import com.kego.simplifiedfit.data.OpenRouterCoachClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class CoachRequestWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SimplifiedFitApplication
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val (provider, request) = app.secureStore.coachJob(jobId) ?: return Result.failure()
        val backend = when (provider) {
            CoachProvider.CODEX -> app.secureStore.coachConnection()?.let(::CoachClient)
            CoachProvider.OPENROUTER -> app.secureStore.openRouterApiKey()?.let(::OpenRouterCoachClient)
        } ?: return Result.failure(workDataOf(KEY_ERROR to "Coach is not configured"))

        return try {
            var complete: CoachEvent.Complete? = null
            backend.ask(request).collect { event ->
                when (event) {
                    is CoachEvent.Progress -> setProgress(workDataOf(KEY_PHASE to event.stage.name))
                    is CoachEvent.ResponseDelta -> Unit
                    is CoachEvent.Complete -> complete = event
                }
            }
            val result = complete ?: error("Coach returned no answer")
            app.secureStore.saveCoachJobResult(jobId, result.answer, result.durationMs)
            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error.isNetworkInterruption() && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Coach unavailable")))
            }
        }
    }

    private fun Throwable.isNetworkInterruption(): Boolean =
        generateSequence(this) { it.cause }.any { it is IOException }

    companion object {
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        private const val KEY_JOB_ID = "job_id"
        private const val UNIQUE_WORK = "coach-request"
        private const val MAX_RETRIES = 3

        fun enqueue(context: Context, provider: CoachProvider, request: CoachRequest): UUID {
            val id = UUID.randomUUID()
            val app = context.applicationContext as SimplifiedFitApplication
            app.secureStore.saveCoachJob(id.toString(), provider, request)
            val work = OneTimeWorkRequestBuilder<CoachRequestWorker>()
                .setId(id)
                .setInputData(workDataOf(KEY_JOB_ID to id.toString()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, work)
            return id
        }
    }
}
