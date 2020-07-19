package io.tempo.internal.domain.useCases

import io.tempo.TimeSourceCache
import io.tempo.internal.domain.DeviceClocks
import kotlin.math.abs

internal class CheckCacheValidityUC(private val deviceClocks: DeviceClocks) {
    operator fun invoke(cache: TimeSourceCache): Boolean =
        when (val bootCount = deviceClocks.bootCount()) {
            null -> abs(cache.estimatedBootTime - deviceClocks.estimatedBootTime()) <= 5000L
            else -> bootCount == cache.bootCount
        }
}