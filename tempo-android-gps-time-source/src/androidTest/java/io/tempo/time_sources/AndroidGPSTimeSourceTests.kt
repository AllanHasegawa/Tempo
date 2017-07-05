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

import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import io.reactivex.schedulers.Schedulers
import io.reactivex.subscribers.TestSubscriber
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidGPSTimeSourceTests {
    val application = InstrumentationRegistry.getTargetContext().applicationContext as Application

    @Test
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun testNormalPath() {
        // On a device, go to "Developer Options" and enable location mocks for this library.
        // Also, manually open the app in the "Settings" screen and enable the "Location" permission.
        val gpsUptime = 50L
        val gpsTime = 100L
        val gpsElapsedRealTime = 200L

        // Setup location mock
        val mgr = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = LocationManager.GPS_PROVIDER
        mgr.addTestProvider(provider, false, true, false, false, true, true, true, 0, 0)
        mgr.setTestProviderEnabled(provider, true)
        mgr.setTestProviderStatus(provider, LocationProvider.AVAILABLE, null, gpsUptime)
        val location = Location(provider)
                .apply {
                    latitude = 1.0
                    longitude = 2.0
                    accuracy = 3.0f
                    bearing = 4.0f
                    speed = 5.0f
                    elapsedRealtimeNanos = gpsElapsedRealTime
                    extras = null
                    time = gpsTime
                }

        // Request time
        val ts = TestSubscriber<Long>()
        AndroidGPSTimeSource(application).requestTime()
                .subscribeOn(Schedulers.io())
                .toFlowable()
                .subscribe(ts)
        Thread.sleep(100)
        mgr.setTestProviderLocation(provider, location)
        ts.await(10, TimeUnit.SECONDS)
        ts.assertNoErrors()

        val got = ts.events[0]
        assert.that(ts.isTerminated, equalTo(true))
        assert.that(got.size, equalTo(1))
        assert.that(got[0] as Long, equalTo(gpsTime))
    }
}