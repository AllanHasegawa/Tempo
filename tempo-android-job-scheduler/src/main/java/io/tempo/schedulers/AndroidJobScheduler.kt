/*
 * Copyright 2017 Allan Yoshio Hasegawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.tempo.schedulers

import android.app.Application
import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import com.evernote.android.job.util.support.PersistableBundleCompat
import io.tempo.Scheduler
import io.tempo.Tempo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AndroidJobScheduler(val application: Application,
                          val periodicIntervalMinutes: Long = 60L) : Scheduler {
    companion object {
        private val JOB_TAG = "tempo-android-job-scheduler"
        private val PKEY_INTERVAL_MIN = "tempo-interval"

        private fun schedule(intervalMinutes: Long, updateCurrent: Boolean) {
            val startMinutes = intervalMinutes
            val endMinutes = intervalMinutes * 2

            val toMs = { minutes: Long -> minutes * 60L * 1000L }
            JobRequest.Builder(JOB_TAG)
                    .setExecutionWindow(toMs(startMinutes), toMs(endMinutes))
                    .setPersisted(true)
                    .setUpdateCurrent(updateCurrent)
                    .setExtras(PersistableBundleCompat().apply {
                        putLong(PKEY_INTERVAL_MIN, intervalMinutes)
                    })
                    .build()
                    .schedule()
        }
    }

    override fun setup() {
        JobManager.create(application).addJobCreator { tag ->
            when (tag) {
                JOB_TAG -> SyncJob()
                else -> null
            }
        }

        schedule(periodicIntervalMinutes, true)
    }

    private class SyncJob : Job() {
        override fun onRunJob(params: Params): Result {
            val intervalMin = params.extras.getLong(PKEY_INTERVAL_MIN, 15L)

            try {
                val syncTimeoutMs = Tempo.config?.syncTimeoutMs
                val syncFlow = Tempo.syncFlow()

                return if (syncTimeoutMs != null && syncFlow != null) {
                    val latch = CountDownLatch(1)

                    val disposable = syncFlow.subscribe({}, {}, { latch.countDown() })
                    try {
                        // We will await for the duration of an interval.
                        // "syncFlow" implements its own timeout mechanism.
                        latch.await(intervalMin, TimeUnit.MINUTES)
                    } catch (e: Exception) {
                        // If we timeout, let it succeed. The "syncFlow" already handles retries.
                    } finally {
                        disposable.dispose()
                    }

                    return Result.SUCCESS
                } else {
                    Result.FAILURE
                }
            } catch (e: Exception) {
                return Result.FAILURE
            } finally {
                schedule(intervalMin, false)
            }
        }
    }
}