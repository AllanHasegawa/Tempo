package io.tempo.internal.domain

import io.tempo.TimeSource
import io.tempo.TimeSourceCache

internal data class TimeSourceWrapper(
    val timeSource: TimeSource,
    val cache: TimeSourceCache
)
