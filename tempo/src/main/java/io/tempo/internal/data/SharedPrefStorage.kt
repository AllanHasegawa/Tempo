/*
 * Copyright 2017 Allan Yoshio Hasegawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.tempo.internal.data

import android.content.Context
import android.content.SharedPreferences
import io.tempo.Storage
import io.tempo.TempoEvent
import io.tempo.TimeSourceCache
import io.tempo.internal.log
import io.tempo.internal.safeOffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

internal class SharedPrefStorage(
    private val context: Context,
    private val bucket: String,
    private val eventLogger: TempoEventLogger
) : Storage {
    companion object {
        private const val KEY_SOURCE_CACHE_PREFIX = "time-source-cache-"
        private fun keyTimeSourceCache(timeSourceId: String) = "$KEY_SOURCE_CACHE_PREFIX$timeSourceId"
    }

    private val sharedPref by lazy { context.getSharedPreferences(bucket, Context.MODE_PRIVATE) }

    override suspend fun putCache(cache: TimeSourceCache) {
        withContext(Dispatchers.IO) {
            with(sharedPref.edit()) {
                val asString = TimeSourceCacheSerializer.asString(cache)
                putString(keyTimeSourceCache(cache.timeSourceId), asString)
                commit()
            }
            TempoEvent.CacheSaved(cache).log(eventLogger)
        }
    }

    override fun observeCaches(): Flow<TimeSourceCache> =
        callbackFlow {
            sharedPref.all
                .asSequence()
                .filter { (key, value) ->
                    value is String &&
                        key.startsWith(KEY_SOURCE_CACHE_PREFIX)
                }
                .mapNotNull { (_, value) -> (value as String).let(TimeSourceCacheSerializer::parse) }
                .forEach { safeOffer(it) }

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key.startsWith(KEY_SOURCE_CACHE_PREFIX)) {
                    sharedPreferences.getString(key, null)
                        ?.let(TimeSourceCacheSerializer::parse)
                        ?.let { safeOffer(it) }
                }
            }

            sharedPref.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { sharedPref.unregisterOnSharedPreferenceChangeListener(listener) }
        }.flowOn(Dispatchers.Default)
            .onEach { TempoEvent.CacheRestored(it).log(eventLogger) }
}