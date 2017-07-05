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

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.processors.ReplayProcessor
import io.reactivex.schedulers.Schedulers
import io.tempo.DeviceClocks
import io.tempo.Scheduler
import io.tempo.Storage
import io.tempo.SyncRetryStrategy
import io.tempo.TempoConfig
import io.tempo.TempoEvent
import io.tempo.TimeSource
import io.tempo.TimeSourceCache
import io.tempo.TimeSourceWrapper
import io.tempo.schedulers.NoOpScheduler
import java.util.concurrent.TimeUnit

class TempoInstance(
        val timeSources: List<TimeSource>,
        val config: TempoConfig,
        private val storage: Storage,
        private val deviceClocks: DeviceClocks,
        private val scheduler: Scheduler) {

    val initialized get() = activeTimeWrapper() != null

    private var activeTimeSourceName: String? = null
    private val timeWrappers = mutableMapOf<String, TimeSourceWrapper>()

    private val eventsSubject = ReplayProcessor.createWithTime<TempoEvent>(
            1000, TimeUnit.MILLISECONDS, Schedulers.io())

    init {
        require(timeSources.isNotEmpty()) {
            "'timeSources' must not be empty."
        }
        require(timeSources.map { it.config().id }.distinct().size == timeSources.size) {
            "Duplicate ids in 'timeSources' aren't allowed."
        }

        Flowable.just(timeSources)
                .doOnNext { eventsSubject.onNext(TempoEvent.Initializing()) }
                .observeOn(Schedulers.io())
                .doOnNext { restoreCache() }
                .doOnNext { setupScheduler() }
                .flatMap { syncFlow() }
                .subscribe({}, {}, {})
    }

    fun observeEvents(): Flowable<TempoEvent> = eventsSubject.onBackpressureLatest()

    fun now(): Long? = activeTimeWrapper()?.nowFromCache(deviceClocks.uptime())

    fun activeTimeWrapper(): TimeSourceWrapper? = activeTimeSourceName?.let { timeWrappers[it] }

    fun syncFlow(): Flowable<TempoEvent> {
        return Flowable.fromIterable(timeSources)
                .observeOn(Schedulers.io())
                .flatMap { source ->
                    requestTime(source)
                            .timeout(config.syncTimeoutMs, TimeUnit.MILLISECONDS)
                            .toFlowable()
                            .map { wrapper -> TempoEvent.TSSyncSuccess(wrapper) as TempoEvent }
                            .startWith(TempoEvent.TSSyncRequest(source) as TempoEvent)
                            .onErrorReturn { error ->
                                val defaultMsg = "Error requesting time to '${source.config().id}'"
                                TempoEvent.TSSyncFailure(source, error, error.message ?: defaultMsg)
                            }
                }
                .publish { flow ->
                    val endFlow = flow
                            .buffer(timeSources.size * 2)
                            .take(1)
                            .map { it.filter { it is TempoEvent.TSSyncSuccess }.isNotEmpty() }
                            .map { hasSuccess ->
                                val activeTw = activeTimeWrapper()
                                if (hasSuccess && activeTw != null) {
                                    TempoEvent.SyncSuccess(activeTw)
                                } else {
                                    TempoEvent.SyncFail()
                                }
                            }
                    val initializedFlow = Flowable.fromCallable { initialized }
                            .flatMap {
                                when (it) {
                                    true -> Flowable.just(TempoEvent.Initialized())
                                    else -> Flowable.empty()
                                }
                            }
                    flow.mergeWith(endFlow).concatWith(initializedFlow)
                }
                .startWith(TempoEvent.SyncStart())
                .doOnNext { event ->
                    eventsSubject.onNext(event)
                    if (event is TempoEvent.TSSyncSuccess) {
                        val name = event.wrapper.timeSource.config().id
                        val cache = event.wrapper.cache
                        synchronized(timeWrappers) {
                            storage.putCache(cache)
                            timeWrappers[name] = event.wrapper
                            updateActiveTimeWrapper()
                        }
                        eventsSubject.onNext(TempoEvent.CacheSaved(cache))
                    }
                }
                .repeatWhen { completed ->
                    val retryFlow = syncRetryStratFlow(config.syncRetryStrategy)
                    completed.zipWith(retryFlow, BiFunction { _: Any, _: Any -> initialized })
                            .takeWhile { !it }
                }
    }

    private fun requestTime(timeSource: TimeSource): Single<TimeSourceWrapper> =
            timeSource.requestTime()
                    .observeOn(Schedulers.io())
                    .map { reqTime ->
                        val cache = TimeSourceCache(
                                timeSourceId = timeSource.config().id,
                                estimatedBootTime = deviceClocks.estimatedBootTime(),
                                requestDeviceUptime = deviceClocks.uptime(),
                                requestTime = reqTime)
                        TimeSourceWrapper(timeSource, cache)
                    }

    private fun updateActiveTimeWrapper() {
        val topPriority = timeWrappers.values.maxBy { it.timeSource.config().priority }
        activeTimeSourceName = topPriority?.timeSource?.config()?.id
    }

    private fun restoreCache() {
        fun isCacheValid(cache: TimeSourceCache): Boolean {
            val estimatedBootTime = deviceClocks.estimatedBootTime()
            val cacheEstimatedBootTime = cache.estimatedBootTime
            return Math.abs(cacheEstimatedBootTime - estimatedBootTime) <= 5000L
        }

        timeSources
                .map { source -> source to storage.getCache(source.config().id) }
                .filter { it.second?.let(::isCacheValid) ?: false }
                .onEach { it.second?.let { eventsSubject.onNext(TempoEvent.CacheRestored(it)) } }
                .map { it.first.config().id to TimeSourceWrapper(it.first, it.second!!) }
                .also {
                    synchronized(timeWrappers) {
                        timeWrappers.putAll(it)
                        updateActiveTimeWrapper()
                    }
                }
    }

    private fun setupScheduler() {
        if (scheduler !is NoOpScheduler) {
            eventsSubject.onNext(TempoEvent.SchedulerSetupStart())
            try {
                scheduler.setup()
                eventsSubject.onNext(TempoEvent.SchedulerSetupComplete())
            } catch (e: Exception) {
                eventsSubject.onNext(TempoEvent.SchedulerSetupFailure(e,
                        "Error while setting up scheduler."))
            }
        } else {
            eventsSubject.onNext(TempoEvent.SchedulerSetupSkip())
        }
    }


    private fun syncRetryStratFlow(strat: SyncRetryStrategy): Flowable<Any> {
        return when (strat) {
            is SyncRetryStrategy.None -> Flowable.empty()
            is SyncRetryStrategy.ConstantInterval -> {
                val interval = Flowable.interval(strat.timerMs, strat.intervalMs, TimeUnit.MILLISECONDS)
                val tries = Flowable.range(1, strat.retries)
                Flowable.zip<Long, Int, Any>(interval, tries, BiFunction { _, _ -> 0 })
            }
            is SyncRetryStrategy.ExpBackoff -> {
                val tries = Flowable.range(1, strat.retries)
                val interval = { idx: Int ->
                    val backoff = Math.pow(2.0, idx.toDouble()) * strat.multiplier
                    val timer = (strat.timerMs + backoff).toLong()
                            .coerceAtMost(strat.maxIntervalMs)
                    Flowable.timer(timer, TimeUnit.MILLISECONDS)
                }
                tries.concatMap { idx -> interval(idx) }
            }
        }
    }
}