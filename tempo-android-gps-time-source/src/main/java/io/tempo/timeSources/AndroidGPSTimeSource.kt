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

package io.tempo.timeSources

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import io.tempo.TimeSource
import io.tempo.TimeSourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

public class AndroidGPSTimeSource(
    private val context: Context,
    id: String = "tempo-default-android-gps",
    priority: Int = 5
) : TimeSource {

    public class PermissionNotSet : RuntimeException("We don't have permission to access the GPS.")

    public override val config: TimeSourceConfig = TimeSourceConfig(id = id, priority = priority)

    private data class GPSInfo(val provider: String, val time: Long)

    @SuppressLint("MissingPermission")
    override suspend fun requestTime(): Long =
        withContext(Dispatchers.Main) {
            callbackFlow<GPSInfo> {
                if (!hasGPSPermission()) throw PermissionNotSet()

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location?) {
                        location?.run { runCatching { offer(GPSInfo(provider, time)) } }
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String?) {}
                    override fun onProviderDisabled(provider: String?) {}
                }

                val mgr = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                mgr.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0.0f,
                    listener,
                    null
                )
                awaitClose { mgr.removeUpdates(listener) }
            }.flowOn(Dispatchers.Main)
                .filter { it.provider == LocationManager.GPS_PROVIDER }
                .first()
                .time
        }

    private fun hasGPSPermission() =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}