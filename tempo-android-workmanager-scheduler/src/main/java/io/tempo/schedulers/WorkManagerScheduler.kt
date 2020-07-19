package io.tempo.schedulers

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.tempo.Scheduler
import io.tempo.schedulers.internal.SetupWorker
import java.util.concurrent.TimeUnit

public class WorkManagerScheduler(
    private val workName: String = "tempo-workmanager",
    private val periodicIntervalMinutes: Long = 60L
) : Scheduler {

    init {
        require(periodicIntervalMinutes >= 15) {
            "WorkManager requires a 'periodicIntervalMinutes' greater than 15"
        }
    }

    override fun setup() {
        val request = OneTimeWorkRequest
            .Builder(SetupWorker::class.java)
            .setInputData(SetupWorker.params(workName, periodicIntervalMinutes))
            .setInitialDelay(periodicIntervalMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance().enqueue(request)
    }
}