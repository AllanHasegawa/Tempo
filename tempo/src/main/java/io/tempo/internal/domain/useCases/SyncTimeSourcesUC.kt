package io.tempo.internal.domain.useCases

import io.tempo.Storage
import io.tempo.TempoEvent
import io.tempo.TimeSource
import io.tempo.TimeSourceCache
import io.tempo.internal.data.TempoEventLogger
import io.tempo.internal.domain.DeviceClocks
import io.tempo.internal.log
import io.tempo.internal.retryIO
import kotlinx.coroutines.*

internal class SyncTimeSourcesUC(
    private val timeSources: List<TimeSource>,
    private val storage: Storage,
    private val deviceClocks: DeviceClocks,
    private val eventLogger: TempoEventLogger
) {
    companion object {
        private const val TIMEOUT_MS = 30L * 1000L
    }

    /**
     * @return true if any [TimeSource] synced successfully.
     */
    suspend operator fun invoke(): Boolean =
        withContext(Dispatchers.Default) {

            TempoEvent.SyncStart().log()

            val completedSuccessfully = timeSources.map { timeSource ->
                async {
                    try {
                        TempoEvent.TSSyncRequest(timeSource).log()
                        withTimeout(TIMEOUT_MS) {
                            retryIO(times = 20) { syncSingleTimeSource(timeSource) }
                        }
                        TempoEvent.TSSyncSuccess(timeSource.config.id).log()
                        true
                    } catch (t: Throwable) {
                        TempoEvent.TSSyncFailure(timeSource, t).log()
                        false
                    }
                }
            }.awaitAll()

            completedSuccessfully.any().also {
                (if (it) TempoEvent.SyncSuccess() else TempoEvent.SyncFail()).log()
            }
        }

    private suspend fun syncSingleTimeSource(timeSource: TimeSource): String {
        val requestTime = timeSource.requestTime()

        val cache = TimeSourceCache(
            timeSourceId = timeSource.config.id,
            timeSourcePriority = timeSource.config.priority,
            estimatedBootTime = deviceClocks.estimatedBootTime(),
            requestDeviceUptime = deviceClocks.uptime(),
            requestTime = requestTime,
            bootCount = deviceClocks.bootCount()
        )

        storage.putCache(cache)

        return timeSource.config.id
    }

    private fun TempoEvent.log() = log(eventLogger)
}