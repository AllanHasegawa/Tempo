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

import io.tempo.SyncRetryStrategy.ExpBackoff

sealed class SyncRetryStrategy {
    class None : SyncRetryStrategy()

    /**
     * Will retry with the following intervals: [timerMs], [intervalMs], [intervalMs], [intervalMs], ...
     *
     * @param[timerMs] The initial timer.
     * @param[intervalMs] Subsequent intervals time.
     * @param[retries] Number of retries.
     */
    data class ConstantInterval(val timerMs: Long, val intervalMs: Long, val retries: Int) : SyncRetryStrategy()

    /**
     * Interval computation: [timerMs] + (2^i) * [multiplier], where `i` is the index of the retry.
     *
     * Example when [timerMs] is 1000 and [multiplier] is 500:
     *
     *     (1000 + 2^1 * 500), (1000 + 2^2 * 500), (1000 + 2^3 * 500), etc...
     *
     * @param[timerMs] Constant timer between each retry.
     * @param[multiplier] Backoff time multiplier.
     * @param[maxIntervalMs] The maximum interval.
     * @param[retries] Number of retries.
     */
    data class ExpBackoff(val timerMs: Long, val multiplier: Double, val maxIntervalMs: Long,
                          val retries: Int) : SyncRetryStrategy()
}

data class TempoConfig(
        val syncTimeoutMs: Long = 30_000L,
        val syncRetryStrategy: SyncRetryStrategy = defaultSyncRetryStrategy)


private val defaultSyncRetryStrategy = ExpBackoff(
        timerMs = 10_000L,
        multiplier = 500.0,
        maxIntervalMs = 10L * 60L * 1000L,
        retries = 20)

