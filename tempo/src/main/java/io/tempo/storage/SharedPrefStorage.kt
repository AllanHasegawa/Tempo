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
import io.tempo.Storage
import io.tempo.TimeSourceCache

class SharedPrefStorage(private val context: Context) : Storage {
    companion object {
        private const val FILE = "tempo-storage"
        private fun keyCacheEstBootTime(name: String) = "$name-est-boot-time"
        private fun keyCacheReqUptime(name: String) = "$name-req-uptime"
        private fun keyCacheReqTime(name: String) = "$name-req-time"
    }

    private val accessLock = Any()

    @SuppressLint("CommitPrefEdits", "ApplySharedPref")
    override fun putCache(cache: TimeSourceCache) {
        synchronized(accessLock) {
            getSharedPref().edit().apply {
                val timeSourceId = cache.timeSourceId
                putLong(keyCacheEstBootTime(timeSourceId), cache.estimatedBootTime)
                putLong(keyCacheReqUptime(timeSourceId), cache.requestDeviceUptime)
                putLong(keyCacheReqTime(timeSourceId), cache.requestTime)
                commit()
            }
        }
    }

    override fun getCache(timeSourceId: String): TimeSourceCache? {
        synchronized(accessLock) {
            val estBootTime = getSharedPref().getLong(keyCacheEstBootTime(timeSourceId), -1L)
            val reqUptime = getSharedPref().getLong(keyCacheReqUptime(timeSourceId), -1L)
            val reqTime = getSharedPref().getLong(keyCacheReqTime(timeSourceId), -1L)
            return when (reqUptime > 0L && reqTime > 0L && estBootTime > 0L) {
                true -> TimeSourceCache(timeSourceId,
                    estimatedBootTime = estBootTime,
                    requestDeviceUptime = reqUptime,
                    requestTime = reqTime)
                else -> null
            }
        }
    }

    @SuppressLint("CommitPrefEdits", "ApplySharedPref")
    override fun clearCaches() {
        synchronized(accessLock) {
            getSharedPref().edit().clear().commit()
        }
    }

    private fun getSharedPref() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}