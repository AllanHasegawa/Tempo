package io.tempo.internal.domain.useCases

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

internal class PeriodicallySyncUC(private val syncTimeSourcesUC: SyncTimeSourcesUC) {
    companion object {
        private const val OK_INTERVAL_MS = 5L /*MIN*/ * 60L /*SECS*/ * 1_000L /*MS*/
        private const val ERROR_INTERVAL_MS = 10L * 1_000L
    }

    suspend operator fun invoke(manualSyncTrigger: Flow<Unit>) {
        manualSyncTrigger
            .onStart { emit(Unit) }
            .mapLatest {
                while (true) {
                    val anyTimeSourceCompletedOk = syncTimeSourcesUC()

                    val nextSyncDelay =
                        if (anyTimeSourceCompletedOk) OK_INTERVAL_MS
                        else ERROR_INTERVAL_MS

                    delay(nextSyncDelay)
                }
            }
            .collect()
    }
}