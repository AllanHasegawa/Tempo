package io.tempo.internal.data

import android.os.Handler
import android.os.Looper
import io.tempo.TempoEvent
import io.tempo.TempoEventsListener

internal class TempoEventLogger {
    private val eventsListener = mutableSetOf<TempoEventsListener>()

    fun log(event: TempoEvent) {
        event.sendToAllListeners()
    }

    fun addEventsListener(listener: TempoEventsListener) {
        synchronized(eventsListener) {
            eventsListener.add(listener)
        }
    }

    fun removeEventsListener(listener: TempoEventsListener) {
        synchronized(eventsListener) {
            eventsListener.remove(listener)
        }
    }

    private fun TempoEvent.sendToAllListeners() {
        Handler(Looper.getMainLooper()).post {
            synchronized(eventsListener) {
                eventsListener.forEach { it.onEvent(this) }
            }
        }
    }
}