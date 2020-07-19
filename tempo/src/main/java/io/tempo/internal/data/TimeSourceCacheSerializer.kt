package io.tempo.internal.data

import io.tempo.TimeSourceCache

internal object TimeSourceCacheSerializer {
    fun parse(asString: String): TimeSourceCache? {
        val split = asString.split(SEPARATOR)
        if (split.count() != 6) return null

        return TimeSourceCache(
            timeSourceId = split[0].nullIfEmpty() ?: return null,
            timeSourcePriority = split[1].toIntOrNull() ?: return null,
            estimatedBootTime = split[2].toLongOrNull() ?: return null,
            requestDeviceUptime = split[3].toLongOrNull() ?: return null,
            requestTime = split[4].toLongOrNull() ?: return null,
            bootCount = split[5].toIntOrNull()
        )
    }

    fun asString(value: TimeSourceCache): String = with(value) {
        listOf(
            component1(),
            component2(),
            component3(),
            component4(),
            component5(),
            component6(),
        ).joinToString(separator = SEPARATOR) { it.toString() }
    }

    private const val SEPARATOR = "|;*^*;|"

    private fun String.nullIfEmpty() = if (isBlank()) null else this
}