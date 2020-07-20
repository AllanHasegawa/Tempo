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
import io.reactivex.Flowable
import io.reactivex.processors.ReplayProcessor
import io.reactivex.schedulers.Schedulers
import io.tempo.device_clocks.AndroidDeviceClocks
import io.tempo.internal.TempoInstance
import io.tempo.schedulers.NoOpScheduler
import io.tempo.storage.SharedPrefStorage
import io.tempo.time_sources.SlackSntpTimeSource
import java.util.concurrent.TimeUnit


object Tempo {
    private val instanceLock = Any()
    private var instance: TempoInstance? = null
    private val eventsSubject = ReplayProcessor.createWithTime<TempoEvent>(
            1000, TimeUnit.MILLISECONDS, Schedulers.io())

    @JvmOverloads
    fun initialize(
            application: Application,
            timeSources: List<TimeSource> = listOf(SlackSntpTimeSource()),
            config: TempoConfig = TempoConfig(),
            storage: Storage = SharedPrefStorage(application),
            deviceClocks: DeviceClocks = AndroidDeviceClocks(application),
            scheduler: Scheduler = NoOpScheduler()) {

        synchronized(instanceLock) {
            require(instance == null) {
                "Don't call Tempo::initialize more than once per process."
            }

            instance = TempoInstance(timeSources, config, storage, deviceClocks, scheduler)
            instance!!.observeEvents().subscribe(eventsSubject)
        }
    }

    val initialized get() = instance?.initialized ?: false
    val config get() = instance?.config
    fun observeEvents(): Flowable<TempoEvent> = eventsSubject.onBackpressureLatest()
    fun now() = instance?.now()
    fun syncFlow(): Flowable<TempoEvent>? = instance?.syncFlow()
    fun activeTimeWrapper() = instance?.activeTimeWrapper()
}
