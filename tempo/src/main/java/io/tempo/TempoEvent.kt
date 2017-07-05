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

sealed class TempoEvent(val systemTime: Long = System.currentTimeMillis()) {
    class SyncStart : TempoEvent()
    data class SyncSuccess(val activeTimeSource: TimeSourceWrapper) : TempoEvent()
    class SyncFail : TempoEvent()

    data class TSSyncRequest(val timeSource: TimeSource) : TempoEvent()
    data class TSSyncSuccess(val wrapper: TimeSourceWrapper) : TempoEvent()
    data class TSSyncFailure(val timeSource: TimeSource, val error: Throwable?,
                             val errorMsg: String) : TempoEvent()

    class Initializing : TempoEvent()
    class Initialized : TempoEvent()

    data class CacheSaved(val cache: TimeSourceCache) : TempoEvent()
    data class CacheRestored(val cache: TimeSourceCache) : TempoEvent()

    class SchedulerSetupSkip : TempoEvent()
    class SchedulerSetupStart : TempoEvent()
    class SchedulerSetupComplete : TempoEvent()
    data class SchedulerSetupFailure(val error: Throwable?, val errorMsg: String) : TempoEvent()
}