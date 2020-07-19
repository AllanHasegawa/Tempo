package io.tempo.internal.domain.useCases

import io.tempo.TimeSourceCache
import io.tempo.internal.domain.DeviceClocks
import io.tempo.internal.domain.TimeSourceWrapper

internal class GetTimeNowUC(private val deviceClocks: DeviceClocks) {
    operator fun invoke(wrapper: TimeSourceWrapper) =
        nowFromCache(wrapper.cache, deviceClocks.uptime())

    private fun nowFromCache(cache: TimeSourceCache, deviceUptime: Long): Long =
        cache.requestTime + (deviceUptime - cache.requestDeviceUptime)
}