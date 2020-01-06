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

package io.tempo.time_sources

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.tempo.TimeSource
import io.tempo.TimeSourceConfig

class AndroidGPSTimeSource(
    val context: Context,
    val id: String = "tempo-default-android-gps",
    private val priority: Int = 5
) : TimeSource {

    class PermissionNotSet : RuntimeException("We don't have permission to access the GPS.")

    override fun config() = TimeSourceConfig(id = id, priority = priority)

    override fun requestTime(): Single<Long> {
        data class GPSInfo(val provider: String, val time: Long)

        return Flowable
            .create<GPSInfo>({ emitter ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location?) {
                        location?.let {
                            if (!emitter.isCancelled) {
                                emitter.onNext(GPSInfo(location.provider, location.time))
                            }
                        }
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String?) {}
                    override fun onProviderDisabled(provider: String?) {}
                }

                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    val mgr = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    emitter.setCancellable {
                        mgr.removeUpdates(listener)
                    }
                    mgr.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        0L,
                        0.0f,
                        listener,
                        null
                    )
                } else {
                    throw PermissionNotSet()
                }
            }, BackpressureStrategy.BUFFER)
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(Schedulers.io())
            .filter { it.provider == LocationManager.GPS_PROVIDER }
            .map { it.time }
            .take(1)
            .singleOrError()
    }
}