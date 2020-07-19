package io.tempo.internal

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay

internal suspend fun <T> retryIO(
    times: Int = Int.MAX_VALUE,
    initialDelay: Long = 100L,
    maxDelay: Long = 2_000L,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    require(times > 0)
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (t: Throwable) {
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // last attempt
}

internal fun <V : Any> SendChannel<V>.safeOffer(a: V) {
    runCatching { offer(a) }
}