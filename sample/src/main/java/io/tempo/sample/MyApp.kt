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

package io.tempo.sample

import android.app.Application
import android.util.Log
import io.tempo.AutoSyncConfig
import io.tempo.Tempo
import io.tempo.schedulers.WorkManagerScheduler
import io.tempo.timeSources.AndroidGPSTimeSource
import io.tempo.timeSources.SlackSntpTimeSource

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Tempo.addEventsListener { event -> Log.d("TempoEvent", event.toString()) }

        Tempo.initialize(
            application = this,
            timeSources = listOf(SlackSntpTimeSource(), AndroidGPSTimeSource(this)),
            scheduler = WorkManagerScheduler(periodicIntervalMinutes = 15L),
            autoSyncConfig = AutoSyncConfig.ConstantInterval(
                intervalDurationMs = 30_000L
            )
        )
    }
}