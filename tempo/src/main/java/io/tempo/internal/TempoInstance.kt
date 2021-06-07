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

package io.tempo.internal

import io.tempo.TempoEvent
import io.tempo.internal.data.TempoEventLogger
import io.tempo.internal.domain.TimeSourceWrapper
import io.tempo.internal.domain.useCases.GetBestAvailableTimeSourceUC
import io.tempo.internal.domain.useCases.GetTimeNowUC
import io.tempo.internal.domain.useCases.PeriodicallySyncUC
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

internal class TempoInstance(
    private val periodicallySyncUC: PeriodicallySyncUC,
    private val getBestAvailableTimeSourceUC: GetBestAvailableTimeSourceUC,
    private val getTimeNowUC: GetTimeNowUC,
    private val eventLogger: TempoEventLogger
) {
    val initialized: Boolean
        get() = activeTimeWrapper != null

    private var activeTimeWrapper: TimeSourceWrapper? = null
    private var coroutineScope: CoroutineScope? = null

    private val manualSyncTriggerChannel = BroadcastChannel<Unit>(BUFFERED)

    fun start() {
        require(coroutineScope == null) { "Tempo is already running" }

        coroutineScope =
            CoroutineScope(
                context = Dispatchers.Default + CoroutineName("Tempo's Main Scope")
            ).apply {
                launch {
                    async {
                        periodicallySyncUC(manualSyncTriggerChannel.asFlow())
                    }

                    getBestAvailableTimeSourceUC()
                        .collect { bestWrapper ->
                            if (activeTimeWrapper == null) TempoEvent.Initialized().log(eventLogger)
                            activeTimeWrapper = bestWrapper
                        }
                }
            }
    }

    fun stop() {
        coroutineScope?.cancel()
        coroutineScope = null
    }

    fun triggerManualActionNow() {
        manualSyncTriggerChannel.safeOffer(Unit)
    }

    fun nowOrNull(): Long? = activeTimeWrapper?.let(getTimeNowUC::invoke)

    fun now(): Flow<Long> = getBestAvailableTimeSourceUC().map(getTimeNowUC::invoke)

    fun activeTimeSourceId(): String? = activeTimeWrapper?.timeSource?.config?.id
}