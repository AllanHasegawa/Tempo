package io.tempo

/**
 * By default Tempo will automatically keep syncing in the background whenever the
 * main app process is running and Tempo is initialized and started.
 *
 * This config class determines the frequency of those syncs, or turn it off completely.
 *
 * By default Tempo uses [AutoSyncConfig.ConstantInterval].
 */
public sealed class AutoSyncConfig {
    public object Off : AutoSyncConfig()

    public data class ConstantInterval(
        val intervalDurationMs: Long = 5L /*MIN*/ * 60L /*SECS*/ * 1_000L, /*MS*/
        val errorRetryDurationMsFactory: (retries: Int) -> Long = { 10L /*SECS*/ * 1_000L /*MS*/ },
    ) : AutoSyncConfig()
}