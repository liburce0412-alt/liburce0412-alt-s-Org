package com.campusai.core.health.mifitness

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.UUID

class MiFitnessStepsSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val outcome = MiFitnessStepsSyncService(applicationContext).syncToday()
        return outcome.fold(
            onSuccess = {
                Log.i(LOG_TAG, "result=success")
                Result.success()
            },
            onFailure = { error ->
                val data = failureData(error)
                Log.i(LOG_TAG, "result=failure code=${data.getString(KEY_ERROR_CODE)}")
                Result.failure(data)
            },
        )
    }

    companion object {
        internal const val KEY_ERROR_CODE = "error_code"
        private const val LOG_TAG = "MiFitnessSync"
        private val safeErrorCodes = setOf(
            "credentials_missing",
            "authentication_failed",
            "network_failed",
            "rate_limited",
            "server_unavailable",
            "no_cloud_data",
            "response_invalid",
            "record_out_of_window",
            "record_limit",
            "cursor_missing",
            "cursor_limit",
            "cursor_repeated",
            "page_limit",
            "aggregate_conflict",
            "cache_write_failed",
            "credential_write_failed",
            "sync_failed",
        )

        internal fun failureData(error: Throwable): Data {
            val code = (error as? MiFitnessStepsSyncException)
                ?.code
                ?.takeIf(safeErrorCodes::contains)
                ?: "sync_failed"
            return workDataOf(KEY_ERROR_CODE to code)
        }
    }
}

internal fun interface MiFitnessUniqueWorkEnqueuer {
    fun enqueue(
        name: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    )
}

object MiFitnessStepsSyncScheduler {
    const val UNIQUE_WORK = "campusai-mi-fitness-cn-steps-manual"

    fun enqueue(context: Context): UUID {
        val manager = WorkManager.getInstance(context.applicationContext)
        return enqueue(MiFitnessUniqueWorkEnqueuer(manager::enqueueUniqueWork))
    }

    internal fun enqueue(enqueuer: MiFitnessUniqueWorkEnqueuer): UUID {
        val request = OneTimeWorkRequestBuilder<MiFitnessStepsSyncWorker>()
            .build()
        enqueuer.enqueue(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }
}
