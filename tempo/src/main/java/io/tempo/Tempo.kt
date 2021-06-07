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

package io.tempo

import android.app.Application
import io.tempo.internal.TempoInstance
import io.tempo.internal.data.AndroidDeviceClocks
import io.tempo.internal.data.SharedPrefStorage
import io.tempo.internal.data.TempoEventLogger
import io.tempo.internal.domain.useCases.*
import io.tempo.internal.log
import io.tempo.schedulers.NoOpScheduler
import io.tempo.timeSources.SlackSntpTimeSource
import kotlinx.coroutines.flow.Flow

public object Tempo {
    private var instance: TempoInstance? = null
    private val instanceLock = Any()

    private val tempoEventLooper = TempoEventLogger()

    @JvmOverloads
    @JvmStatic
    public fun initialize(
        application: Application,
        timeSources: List<TimeSource> = listOf(SlackSntpTimeSource()),
        autoSyncConfig: AutoSyncConfig = AutoSyncConfig.ConstantInterval(),
        scheduler: Scheduler = NoOpScheduler,
        autoStart: Boolean = true
    ) {
        synchronized(instanceLock) {
            require(instance == null) {
                "Don't call Tempo::initialize more than once per process."
            }
            require(timeSources.isNotEmpty()) {
                "'timeSources' must not be empty."
            }
            require(timeSources.map { it.config.id }.distinct().size == timeSources.size) {
                "Duplicate ids in 'timeSources' aren't allowed."
            }

            TempoEvent.Initializing().log(tempoEventLooper)

            setupScheduler(scheduler)

            // Data layer
            val deviceClocks = AndroidDeviceClocks(application)
            val storage = SharedPrefStorage(application, "tempo-bucket", tempoEventLooper)

            // UseCases
            val syncTimeSourcesUC = SyncTimeSourcesUC(
                timeSources, storage, deviceClocks, tempoEventLooper
            )
            val periodicallySyncUC = PeriodicallySyncUC(autoSyncConfig, syncTimeSourcesUC)
            val checkCacheValidityUC = CheckCacheValidityUC(deviceClocks)
            val getBestAvailableTimeSourceUC =
                GetBestAvailableTimeSourceUC(timeSources, storage, checkCacheValidityUC)
            val getTimeNowUC = GetTimeNowUC(deviceClocks)

            instance =
                TempoInstance(
                    periodicallySyncUC,
                    getBestAvailableTimeSourceUC,
                    getTimeNowUC,
                    tempoEventLooper
                )

            if (autoStart) start()
        }
    }

    @JvmStatic
    public fun isInitialized(): Boolean = instance?.initialized == true

    /**
     * Get the current time. This should be called only after Tempo
     * is initialized.
     *
     * This function will return the current time if available, otherwise, it'll wait for Tempo
     * to finish initializing before returning a result.
     */
    public fun now(): Flow<Long> = requireInstance().now()

    /**
     * Get the current time with a non-blocking operation. This should be called only after Tempo
     * is initialized.
     *
     * If this function is called before a successfully sync operation,
     * or if the cache is outdated, it'll return null.
     *
     * Use it with cautious. Prefer using [Tempo::now] for a safer option.
     */
    @JvmStatic
    public fun nowOrNull(): Long? = requireInstance().nowOrNull()

    @JvmStatic
    public fun start(): Unit = requireInstance().start()

    @JvmStatic
    public fun stop(): Unit = requireInstance().stop()

    @JvmStatic
    public fun triggerManualSyncNow(): Unit = requireInstance().triggerManualActionNow()

    @JvmStatic
    public fun activeTimeSourceId(): String? = requireInstance().activeTimeSourceId()

    @JvmStatic
    public fun addEventsListener(listener: TempoEventsListener): Unit =
        tempoEventLooper.addEventsListener(listener)

    @JvmStatic
    public fun removeEventsListener(listener: TempoEventsListener): Unit =
        tempoEventLooper.removeEventsListener(listener)

    private fun setupScheduler(scheduler: Scheduler) {
        when (scheduler) {
            is NoOpScheduler -> {
                TempoEvent.SchedulerSetupSkip().log(tempoEventLooper)
            }
            else -> {
                TempoEvent.SchedulerSetupStart().log(tempoEventLooper)
                try {
                    scheduler.setup()
                    TempoEvent.SchedulerSetupComplete().log(tempoEventLooper)
                } catch (t: Throwable) {
                    TempoEvent.SchedulerSetupFailure(t).log(tempoEventLooper)
                }
            }
        }
    }

    private fun requireInstance(): TempoInstance =
        try {
            instance!!
        } catch (_: Throwable) {
            throw IllegalStateException("Tempo needs to be initialized")
        }
}