package io.tempo.internal

import io.tempo.TempoEvent
import io.tempo.internal.data.TempoEventLogger

internal fun TempoEvent.log(logger: TempoEventLogger) = logger.log(this)