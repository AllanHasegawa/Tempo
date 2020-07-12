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

package io.tempo.storage

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.tempo.Storage
import io.tempo.TimeSourceCache
import kotlin.reflect.KClass

class SharedPrefStorage(private val context: Context) : Storage {
    companion object {
        private const val FILE = "tempo-storage"

        private fun keyCacheEstBootTime(name: String) = Config("$name-est-boot-time", Long::class)
        private fun keyCacheReqUptime(name: String) = Config("$name-req-uptime", Long::class)
        private fun keyCacheReqTime(name: String) = Config("$name-req-time", Long::class)
        private fun keyBootCount(name: String) = Config("$name-req-boot-count", Int::class)
    }

    private val accessLock = Any()

    override fun putCache(cache: TimeSourceCache): Unit = synchronized(accessLock) {
        getSharedPref().edit().run {
            val timeSourceId = cache.timeSourceId
            put(keyCacheEstBootTime(timeSourceId), cache.estimatedBootTime)
            put(keyCacheReqUptime(timeSourceId), cache.requestDeviceUptime)
            put(keyCacheReqTime(timeSourceId), cache.requestTime)
            cache.bootCount?.let { put(keyBootCount(timeSourceId), it) }
            commit()
        }
    }

    override fun getCache(timeSourceId: String): TimeSourceCache? = synchronized(accessLock) {
        with(getSharedPref()) {
            TimeSourceCache(
                timeSourceId = timeSourceId,
                estimatedBootTime = get(keyCacheEstBootTime(timeSourceId)) ?: return null,
                requestDeviceUptime = get(keyCacheReqUptime(timeSourceId)) ?: return null,
                requestTime = get(keyCacheReqTime(timeSourceId)) ?: return null,
                bootCount = get(keyBootCount(timeSourceId))
            )
        }
    }

    @SuppressLint("ApplySharedPref")
    override fun clearCaches(): Unit = synchronized(accessLock) {
        getSharedPref().edit().clear().commit()
    }

    private fun getSharedPref() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun SharedPreferences.Editor.put(config: Config<Long>, value: Long) =
        putLong(config.key, value)

    private fun SharedPreferences.Editor.put(config: Config<Int>, value: Int) =
        putInt(config.key, value)

    private fun SharedPreferences.get(config: Config<Long>): Long? =
        when (val value = getLong(config.key, -1L)) {
            -1L -> null
            else -> value
        }

    private fun SharedPreferences.get(config: Config<Int>): Int? =
        when (val value = getInt(config.key, -1)) {
            -1 -> null
            else -> value
        }

    private data class Config<T : Any>(val key: String, val type: KClass<T>)
}