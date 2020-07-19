package io.tempo.internal.domain.useCases

import io.tempo.Storage
import io.tempo.TimeSource
import io.tempo.TimeSourceCache
import io.tempo.internal.domain.TimeSourceWrapper
import kotlinx.coroutines.flow.*

internal class GetBestAvailableTimeSourceUC(
    private val timeSources: List<TimeSource>,
    private val storage: Storage,
    private val checkCacheValidityUC: CheckCacheValidityUC
) {
    operator fun invoke(): Flow<TimeSourceWrapper> {
        val timeSourcesIdx = timeSources.associateBy { it.config.id }

        return storage.observeCaches()
            .catch { it.printStackTrace() }
            .scan(null as? TimeSourceCache?) { previous, cache ->
                val previousPriority = previous?.timeSourcePriority ?: -1000
                if (
                    cache.timeSourcePriority >= previousPriority &&
                    checkCacheValidityUC(cache) &&
                    timeSourcesIdx.contains(cache.timeSourceId)
                ) cache
                else previous
            }
            .filterNotNull()
            .map { cache ->
                TimeSourceWrapper(
                    timeSourcesIdx[cache.timeSourceId] ?: return@map null,
                    cache
                )
            }
            .filterNotNull()
    }
}