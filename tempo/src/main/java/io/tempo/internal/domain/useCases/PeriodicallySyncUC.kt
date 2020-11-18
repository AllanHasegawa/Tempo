package io.tempo.internal.domain.useCases

import io.tempo.AutoSyncConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

internal class PeriodicallySyncUC(
    private val autoSyncConfig: AutoSyncConfig,
    private val syncTimeSourcesUC: SyncTimeSourcesUC
) {
    suspend operator fun invoke(manualSyncTrigger: Flow<Unit>) {
        manualSyncTrigger
            .onStart { emit(Unit) }
            .mapLatest {
                var anyTimeSourceCompletedOk = syncTimeSourcesUC()

                var errorRetries = 0
                while (autoSyncConfig is AutoSyncConfig.ConstantInterval) {
                    val nextSyncDelay =
                        if (anyTimeSourceCompletedOk) {
                            errorRetries = 0
                            autoSyncConfig.intervalDurationMs
                        } else {
                            autoSyncConfig.errorRetryDurationMsFactory(errorRetries)
                                .also { errorRetries++ }
                        }

                    delay(nextSyncDelay.coerceAtLeast(0L))
                    anyTimeSourceCompletedOk = syncTimeSourcesUC()
                }
            }
            .collect()
    }
}