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

package io.tempo.time_sources

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.tempo.TimeSource
import io.tempo.TimeSourceConfig
import io.tempo.internal.AndroidSntpClient
import io.tempo.internal.SntpClient
import io.tempo.internal.SntpClient.Result
import java.net.InetAddress

/**
 * A [TimeSource] implementation using a more forgiving SNTP algorithm. It queries the [ntpPool]
 * five times concurrently, then, removes all failures and queries where the round trip took more
 * than [maxRoundTripMs]. If one or more queries succeeds, we take the one with the median round
 * trip time and return it.
 *
 * @param[id] The unique time source id.
 * @param[priority] The time source priority.
 * @param[ntpPool] The address of the NTP pool.
 * @param[maxRoundTripMs] The maximum allowed round trip time in milliseconds.
 * @param[timeoutMs] The maximum time allowed per each query, in milliseconds.
 */
class SlackSntpTimeSource(val id: String = "default-slack-sntp",
    private val priority: Int = 10,
    private val ntpPool: String = "time.google.com",
    private val maxRoundTripMs: Int = 1_000,
    private val timeoutMs: Int = 10_000
) : TimeSource {

    class AllRequestsFailure(errorMsg: String, cause: Throwable?)
        : RuntimeException(errorMsg, cause)

    override fun config() = TimeSourceConfig(id = id, priority = priority)

    override fun requestTime(): Single<Long> =
        Single
            .create<InetAddress> { emitter ->
                try {
                    val result = AndroidSntpClient.queryHostAddress(ntpPool)
                    if (!emitter.isDisposed) {
                        emitter.onSuccess(result)
                    }
                } catch (t: Throwable) {
                    emitter.tryOnError(t)
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .flatMap { address ->
                // Make 5 requests
                val requests = (1..5).map { requestTimeToAddress(address) }

                // Wait for completion, then join them
                Single.zip(requests) {
                    @Suppress("UNCHECKED_CAST")
                    it.toList() as List<SntpClient.Result>
                }
            }
            .map { rawResults ->
                val results = turnSlowRequestsIntoFailure(rawResults)

                val successes = results.mapNotNull { it as? Result.Success }
                if (successes.isNotEmpty()) {
                    // If at least one succeeds, sort by 'round trip time' and get median.
                    successes
                        .sortedBy { it.roundTripTimeMs }
                        .map { it.ntpTimeMs }
                        .elementAt(successes.size / 2)
                } else {
                    // If all fail, throw 'AllRequestsFailure' exception.
                    val failures = results.mapNotNull { it as? Result.Failure }
                    val msgs = failures.joinToString("; ", prefix = "[", postfix = "]") { it.errorMsg }
                    val errorMsg = "All NTP requests failed: $msgs"
                    val cause = failures.firstOrNull()?.error
                    throw AllRequestsFailure(errorMsg, cause)
                }
            }

    private fun turnSlowRequestsIntoFailure(rawResults: List<Result>): List<Result> =
        rawResults.map {
            when (it) {
                is Result.Success -> when (it.roundTripTimeMs > maxRoundTripMs) {
                    true -> Result.Failure(
                        null,
                        "RoundTrip time exceeded allowed threshold:" +
                            " took ${it.roundTripTimeMs}, but max is $maxRoundTripMs"
                    )
                    else -> it
                }
                else -> it
            }
        }

    private fun requestTimeToAddress(address: InetAddress) =
        Single
            .create<SntpClient.Result> { emitter ->
                try {
                    val result = AndroidSntpClient.requestTime(
                        address,
                        AndroidSntpClient.NTP_PORT,
                        timeoutMs
                    )

                    if (!emitter.isDisposed) {
                        emitter.onSuccess(result)
                    }
                } catch (t: Throwable) {
                    emitter.tryOnError(t)
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .onErrorReturn { error ->
                val msg = error.message ?: "Error requesting time source time."
                Result.Failure(error, msg)
            }
}