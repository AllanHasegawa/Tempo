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

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.sameInstance
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subscribers.TestSubscriber
import io.tempo.DeviceClocks
import io.tempo.Scheduler
import io.tempo.Storage
import io.tempo.SyncRetryStrategy
import io.tempo.TempoConfig
import io.tempo.TempoEvent
import io.tempo.TimeSource
import io.tempo.TimeSourceCache
import io.tempo.TimeSourceConfig
import io.tempo.TimeSourceWrapper
import org.junit.Test
import java.util.Random
import java.util.concurrent.TimeUnit


internal class TempoInstanceTests {
    private fun defaultInstance(changeParams: Initializer.() -> Unit = {}): TempoInstance {
        val syncRetryStrategy = SyncRetryStrategy.ConstantInterval(10L, 10L, 3)
        val initializer = Initializer(
                timeSources = listOf(StubTimeSource()),
                config = TempoConfig(syncRetryStrategy = syncRetryStrategy),
                storage = StubStorage(),
                deviceClocks = StubDeviceClocks(),
                scheduler = schedulerMock)
        changeParams.invoke(initializer)
        return TempoInstance(
                timeSources = initializer.timeSources,
                config = initializer.config,
                storage = initializer.storage,
                deviceClocks = initializer.deviceClocks,
                scheduler = initializer.scheduler)
    }


    @Test
    fun testInit_emptyTs() {
        assert.that({ defaultInstance { timeSources = emptyList() } },
                throws<IllegalArgumentException>())
    }

    @Test
    fun testInit_twoTs_sameId() {
        assert.that({
            defaultInstance {
                val stubTs0 = StubTimeSource()
                val stubTs1 = StubTimeSource()
                timeSources = listOf(stubTs0, stubTs1)
            }
        }, throws<IllegalArgumentException>())
    }

    @Test
    fun testInit_normalPath() {
        val ts = TestSubscriber<TempoEvent>()

        val tempo = defaultInstance()
        tempo.observeEvents().takeUntil { it is TempoEvent.Initialized }.subscribe(ts)

        ts.await()
        assert.that(tempo.initialized, equalTo(true))

        val got = ts.events[0]
        val hasIt = { predicate: (Any) -> Boolean ->
            assert.that(got.count(predicate), equalTo(1))
        }
        hasIt { it is TempoEvent.Initializing }
        hasIt { it is TempoEvent.TSSyncRequest }
        hasIt { it is TempoEvent.TSSyncSuccess }
        hasIt { it is TempoEvent.CacheSaved }
        hasIt { it is TempoEvent.SchedulerSetupStart }
        hasIt { it is TempoEvent.SchedulerSetupComplete }
        hasIt { it is TempoEvent.Initialized }
        hasIt { it is TempoEvent.SyncStart }
        hasIt { it is TempoEvent.SyncSuccess }
        assert.that(got.size, equalTo(9))
    }


    @Test
    fun testTSSyncSuccess() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs = StubTimeSource()

        val tempo = defaultInstance {
            timeSources = listOf(stubTs)
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.TSSyncSuccess }.subscribe(ts)

        ts.await()

        val got = ts.events[0]
        assert.that(got[0], isA<TempoEvent.Initializing>())
        assert.that(got.count { it is TempoEvent.TSSyncRequest }, equalTo(1))
        assert.that(got.count { it is TempoEvent.TSSyncSuccess }, equalTo(1))

        val successEvent = got.first { it is TempoEvent.TSSyncSuccess } as TempoEvent.TSSyncSuccess
        val gotWrapper = successEvent.wrapper
        assert.that(gotWrapper.cache.timeSourceId, equalTo(stubTs.id))
        assert.that(gotWrapper.cache.requestDeviceUptime, equalTo(21L))
        assert.that(gotWrapper.cache.requestTime, equalTo(stubTs.time))

        assert.that(gotWrapper.timeSource, sameInstance<TimeSource>(stubTs))
    }

    @Test
    fun testTSSyncFailure() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs = StubTimeSource()
        stubTs.error = RuntimeException("^^")

        val tempo = defaultInstance {
            timeSources = listOf(stubTs)
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.TSSyncFailure }.subscribe(ts)

        ts.await()

        val got = ts.events[0]
        assert.that(got.count { it is TempoEvent.TSSyncFailure }, equalTo(1))

        val gotFailure = (got.first { it is TempoEvent.TSSyncFailure } as TempoEvent.TSSyncFailure)
        assert.that(gotFailure.error, equalTo(stubTs.error))
        assert.that(gotFailure.timeSource, sameInstance<TimeSource>(stubTs))
    }

    @Test
    fun testTSSyncFailure_notInitialized() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs = StubTimeSource()
        stubTs.error = RuntimeException()

        val tempo = defaultInstance {
            timeSources = listOf(stubTs)
        }
        tempo.observeEvents().subscribe(ts)

        ts.await(50L, TimeUnit.MILLISECONDS)
        assert.that(tempo.initialized, equalTo(false))

        val got = ts.events[0]
        val hasIt = { predicate: (Any) -> Boolean ->
            assert.that(got.count(predicate), equalTo(3))
        }
        hasIt { it is TempoEvent.TSSyncRequest }
        hasIt { it is TempoEvent.TSSyncFailure }
    }

    @Test
    fun testSyncSuccess() {
        val ts = TestSubscriber<TempoEvent>()

        val tempo = defaultInstance()
        tempo.observeEvents().takeWhile { it !is TempoEvent.Initialized }.subscribe(ts)
        ts.await(10_000L, TimeUnit.MILLISECONDS)

        val got = ts.events[0]
        val hasIt = { predicate: (Any) -> Boolean ->
            assert.that(got.count(predicate), equalTo(1))
        }
        hasIt { it is TempoEvent.SyncSuccess }
    }

    @Test
    fun testSyncSuccess_withTsError() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource()
        val stubTs1 = StubTimeSource("stub1")

        listOf(stubTs0, stubTs1)[Random().nextInt(2)]
                .error = RuntimeException()

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0, stubTs1)
        }
        tempo.observeEvents().takeWhile { it !is TempoEvent.Initialized }.subscribe(ts)
        ts.await(10_000L, TimeUnit.MILLISECONDS)

        val got = ts.events[0]
        val hasIt = { predicate: (Any) -> Boolean ->
            assert.that(got.count(predicate), equalTo(1))
        }
        hasIt { it is TempoEvent.SyncSuccess }
    }

    @Test
    fun testSyncFail() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource()
        stubTs0.error = RuntimeException()
        val stubTs1 = StubTimeSource("stub1")
        stubTs1.error = RuntimeException()

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0, stubTs1)
        }
        tempo.observeEvents().takeWhile { it !is TempoEvent.Initialized }.subscribe(ts)

        ts.await(100L, TimeUnit.MILLISECONDS)

        val got = ts.events[0]
        val hasIt = { predicate: (Any) -> Boolean ->
            assert.that(got.count(predicate), equalTo(3))
        }
        hasIt { it is TempoEvent.SyncFail }
    }

    @Test
    fun testActiveTimeWrapper_notInitialized() {
        val tempo = defaultInstance { }
        assert.that(tempo.activeTimeWrapper(), equalTo<TimeSourceWrapper>(null))
    }

    @Test
    fun testNow_notInitialized() {
        val tempo = defaultInstance { }
        assert.that(tempo.now(), equalTo<Long>(null))
    }

    @Test
    fun testActiveTimeWrapper_byPriority() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource(id = "stub0", priority = 3)
        val stubTs1 = StubTimeSource(id = "stub1", priority = 5)
        val stubTs2 = StubTimeSource(id = "stub2", priority = 4)

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0, stubTs1, stubTs2)
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.Initialized }.subscribe(ts)

        ts.await()

        assert.that(tempo.activeTimeWrapper()!!.timeSource, sameInstance<TimeSource>(stubTs1))
    }

    @Test
    fun testActiveTimeWrapper_withFailures() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource(id = "stub0", priority = 3)
        val stubTs1 = StubTimeSource(id = "stub1", priority = 5).apply {
            error = IllegalAccessException("^^")
        }
        val stubTs2 = StubTimeSource(id = "stub2", priority = 4)

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0, stubTs1, stubTs2)
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.Initialized }.subscribe(ts)

        ts.await()

        assert.that(tempo.activeTimeWrapper()!!.timeSource, sameInstance<TimeSource>(stubTs2))
    }

    @Test
    fun testGetTime() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource()
        val stubDC = StubDeviceClocks()

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0)
            deviceClocks = stubDC
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.Initialized }.subscribe(ts)

        ts.await()

        // StubDC will inc one millisecond for every call
        val expectedTime = stubTs0.time + ((stubDC.uptime + 1) - stubDC.uptime)
        assert.that(tempo.now(), equalTo(expectedTime))
    }

    @Test
    fun testCacheRestoring_cleanCache() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource()
        val mockStorage = mock<Storage>()
        whenever(mockStorage.getCache(any())).thenReturn(null)


        val tempo = defaultInstance {
            timeSources = listOf(stubTs0)
            storage = mockStorage
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.Initialized }.subscribe(ts)

        ts.await()
        verify(mockStorage, times(1)).getCache(stubTs0.id)
        verify(mockStorage, times(1)).putCache(any())
        verifyNoMoreInteractions(mockStorage)
    }

    @Test
    fun testCacheRestoring_validCache_tsFailure() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource()
        stubTs0.error = RuntimeException()
        val stubDC = StubDeviceClocks()
        val mockStorage = mock<Storage>()

        val validCache = TimeSourceCache(stubTs0.id, stubDC.estimatedBootTime,
                requestTime = 1000L, requestDeviceUptime = 12L)
        whenever(mockStorage.getCache(stubTs0.id)).thenReturn(validCache)

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0)
            deviceClocks = stubDC
            storage = mockStorage
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.Initialized }.subscribe(ts)

        ts.await()
        verify(mockStorage, times(1)).getCache(stubTs0.id)
        verifyNoMoreInteractions(mockStorage)

        val expectedTime = 1000L + (stubDC.uptime - 12L)
        assert.that(tempo.now(), equalTo(expectedTime))
    }

    @Test
    fun testCacheRestoring_validCache_tsSuccess() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource()
        val stubDC = StubDeviceClocks()
        val mockStorage = mock<Storage>()

        val validCache = TimeSourceCache(stubTs0.id, stubDC.estimatedBootTime,
                requestTime = 1000L, requestDeviceUptime = 12L)
        whenever(mockStorage.getCache(stubTs0.id)).thenReturn(validCache)

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0)
            deviceClocks = stubDC
            storage = mockStorage
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.Initialized }.subscribe(ts)

        ts.await()
        verify(mockStorage, times(1)).getCache(stubTs0.id)
        verify(mockStorage, times(1)).putCache(any())
        verifyNoMoreInteractions(mockStorage)

        val expectedTime = stubTs0.time + ((stubDC.uptime + 1) - stubDC.uptime)
        assert.that(tempo.now(), equalTo(expectedTime))
    }

    @Test
    fun testCacheRestoring_invalidCache_tsFailure() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource()
        stubTs0.error = RuntimeException()
        val stubDC = StubDeviceClocks()
        val mockStorage = mock<Storage>()

        val validCache = TimeSourceCache(stubTs0.id, stubDC.estimatedBootTime + 60L * 60L * 1000L,
                requestTime = 1000L, requestDeviceUptime = 12L)
        whenever(mockStorage.getCache(stubTs0.id)).thenReturn(validCache)

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0)
            deviceClocks = stubDC
            storage = mockStorage
        }
        tempo.observeEvents().subscribe(ts)

        ts.await(500, TimeUnit.MILLISECONDS)
        verify(mockStorage, times(1)).getCache(stubTs0.id)
        verifyNoMoreInteractions(mockStorage)

        assert.that(tempo.initialized, equalTo(false))
    }

    @Test
    fun testCacheSaving_tsSuccess() {
        val ts = TestSubscriber<TempoEvent>()
        val stubTs0 = StubTimeSource()
        val stubDC = StubDeviceClocks()
        val mockStorage = mock<Storage>()

        whenever(mockStorage.getCache(any())).thenReturn(null)

        val tempo = defaultInstance {
            timeSources = listOf(stubTs0)
            deviceClocks = stubDC
            storage = mockStorage
        }
        tempo.observeEvents().takeUntil { it is TempoEvent.Initialized }.subscribe(ts)

        ts.await()
        verify(mockStorage, times(1)).getCache(stubTs0.id)

        val expectedCache = TimeSourceCache(stubTs0.id, stubDC.estimatedBootTime,
                requestTime = stubTs0.time, requestDeviceUptime = stubDC.uptime)
        verify(mockStorage, times(1)).putCache(expectedCache)
        verifyNoMoreInteractions(mockStorage)
    }

    @Test
    fun testSyncFailure_retry() {
        val ts = TestSubscriber<TempoEvent>()

        val stubTs = StubTimeSource()
        stubTs.error = RuntimeException()

        val tempo = defaultInstance {
            timeSources = listOf(stubTs)
        }
        tempo.observeEvents().subscribe(ts)

        ts.await(100L, TimeUnit.MILLISECONDS)

        val got = ts.events[0]
        val failureEvents = got.filter { it is TempoEvent.TSSyncFailure }
        val retries = 3
        assert.that(failureEvents.size, equalTo(retries))
    }

    @Test
    fun testSyncSuccess_noRetry() {
        val ts = TestSubscriber<TempoEvent>()

        val tempo = defaultInstance { }
        tempo.observeEvents().subscribe(ts)

        ts.await(100L, TimeUnit.MILLISECONDS)

        val got = ts.events[0]
        val successEvents = got.filter { it is TempoEvent.TSSyncSuccess }
        assert.that(successEvents.size, equalTo(1))
    }
}

data class Initializer(
        var timeSources: List<TimeSource>,
        var config: TempoConfig,
        var storage: Storage,
        var deviceClocks: DeviceClocks,
        val scheduler: Scheduler)


class StubStorage : Storage {
    var store = mutableMapOf<String, TimeSourceCache>()

    override fun putCache(cache: TimeSourceCache) {
        store[cache.timeSourceId] = cache
    }

    override fun getCache(timeSourceId: String): TimeSourceCache? {
        return store[timeSourceId]
    }

    override fun clearCaches() {
        store.clear()
    }
}

class StubDeviceClocks : DeviceClocks {
    var uptime = 21L
    var calls = 0
    var estimatedBootTime = 500L

    override fun uptime(): Long = (uptime + calls++)
    override fun estimatedBootTime(): Long = estimatedBootTime
}

class StubTimeSource(val id: String = "stub", val priority: Int = 10) : TimeSource {
    var time = 100L
    var timeDelayMs = 1L
    var error: Throwable? = null

    override fun config() = TimeSourceConfig(id, priority)

    override fun requestTime(): Single<Long> {
        return Single
                .fromCallable {
                    Thread.sleep(timeDelayMs)
                    error?.let { throw it }
                    time
                }
                .subscribeOn(Schedulers.io())
    }
}

val schedulerMock = mock<Scheduler>()
