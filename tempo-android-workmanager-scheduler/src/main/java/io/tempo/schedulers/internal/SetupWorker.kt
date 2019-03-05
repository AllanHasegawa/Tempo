package io.tempo.schedulers.internal

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

internal class SetupWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {
    companion object {
        private const val WORK_NAME_PKEY = "tag-pkey"
        private const val INTERNAL_MINUTES_PKEY = "interval-minutes-pkey"

        fun params(workName: String, intervalInMinutes: Long) =
            Data.Builder()
                .putString(WORK_NAME_PKEY, workName)
                .putLong(INTERNAL_MINUTES_PKEY, intervalInMinutes)
                .build()
    }

    override fun doWork(): Result {
        val workName = inputData.getString(WORK_NAME_PKEY)!!
        val intervalInMinutes = inputData.getLong(INTERNAL_MINUTES_PKEY, 60)

        val request = PeriodicWorkRequest
            .Builder(
                SyncFlowWorker::class.java,
                intervalInMinutes,
                TimeUnit.MINUTES
            )
            .setInputData(
                SyncFlowWorker.params(intervalInMinutes)
            )
            .build()

        WorkManager.getInstance().enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        return Result.success()
    }
}