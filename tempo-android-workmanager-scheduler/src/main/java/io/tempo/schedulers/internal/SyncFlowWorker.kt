package io.tempo.schedulers.internal

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.tempo.Tempo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
            val syncFlow = Tempo.syncFlow()

            if (syncFlow != null) {
                val latch = CountDownLatch(1)

                val disposable = syncFlow.subscribe({}, { latch.countDown() }, { latch.countDown() })
                try {
                    // We will await for the duration of an interval.
                    // "syncFlow" implements its own timeout mechanism.
                    latch.await(intervalInMinutes, TimeUnit.MINUTES)
                } catch (_: Throwable) {
                    // If we timeout, let it succeed. The "syncFlow" already handles retries.
                } finally {
                    disposable.dispose()
                }

                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Throwable) {
            Result.retry()
        }
}