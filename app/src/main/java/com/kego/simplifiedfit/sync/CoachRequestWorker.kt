package com.kego.simplifiedfit.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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
import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

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
            val partialResponse = StringBuilder()
            var publishedLength = 0
            backend.ask(request).collect { event ->
                when (event) {
                    is CoachEvent.Progress -> setProgress(
                        workDataOf(
                            KEY_PHASE to event.stage.name,
                            KEY_PARTIAL_RESPONSE to partialResponse.toString(),
                        ),
                    )
                    is CoachEvent.ResponseDelta -> {
                        partialResponse.append(event.text)
                        if (publishedLength == 0 || partialResponse.length - publishedLength >= PARTIAL_UPDATE_CHARS) {
                            setProgress(
                                workDataOf(
                                    KEY_PHASE to CoachProgress.WRITING.name,
                                    KEY_PARTIAL_RESPONSE to partialResponse.toString(),
                                ),
                            )
                            publishedLength = partialResponse.length
                        }
                    }
                    is CoachEvent.Complete -> complete = event
                }
            }
            val result = complete ?: error("Coach returned no answer")
            app.secureStore.saveCoachJobResult(jobId, result.answer, result.durationMs)
            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(workDataOf(KEY_ERROR to error.coachMessage()))
        }
    }

    private fun Throwable.coachMessage(): String {
        val causes = generateSequence(this) { it.cause }.toList()
        return when {
            causes.any { it is SocketTimeoutException } -> "Coach took too long to respond. Try again."
            causes.any { it is IOException } -> "Connection to Coach was interrupted. Try again."
            causes.any { it is JSONException } -> "Coach returned an invalid response. Try again."
            else -> message ?: "Coach unavailable"
        }
    }

    companion object {
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        const val KEY_PARTIAL_RESPONSE = "partial_response"
        private const val KEY_JOB_ID = "job_id"
        private const val UNIQUE_WORK = "coach-request"
        private const val PARTIAL_UPDATE_CHARS = 48

        fun enqueue(context: Context, provider: CoachProvider, request: CoachRequest): UUID {
            val id = UUID.randomUUID()
            val app = context.applicationContext as SimplifiedFitApplication
            app.secureStore.saveCoachJob(id.toString(), provider, request)
            val work = OneTimeWorkRequestBuilder<CoachRequestWorker>()
                .setId(id)
                .setInputData(workDataOf(KEY_JOB_ID to id.toString()))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, work)
            return id
        }
    }
}
