package io.tempo.schedulers.internal

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.tempo.Tempo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

internal class SyncFlowWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {
    companion object {
        private const val INTERNAL_MINUTES_PKEY = "interval-minutes-pkey"

        fun params(intervalInMinutes: Long) =
            Data.Builder()
                .putLong(INTERNAL_MINUTES_PKEY, intervalInMinutes)
                .build()
    }

    override fun doWork(): Result =
        try {
            val intervalInMinutes = inputData.getLong(INTERNAL_MINUTES_PKEY, 60)
            val intervalInMs = intervalInMinutes * 60L * 1000L

            try {
                Tempo.start()
            } catch (_: Throwable) {
            }
            Tempo.triggerManualSyncNow()

            runBlocking {
                withTimeoutOrNull(intervalInMs) {
                    while (isActive) {
                        if (Tempo.isInitialized()) return@withTimeoutOrNull Result.success()
                        else delay(1_000L)
                    }
                    Result.retry()
                } ?: Result.retry()
            }
        } catch (_: Throwable) {
            Result.retry()
        }
}