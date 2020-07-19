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

package io.tempo.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.lessThan
import io.tempo.internal.data.AndroidSntpClient
import io.tempo.internal.domain.SntpClient.Result
import org.junit.Test
import org.junit.runner.RunWith
import java.net.SocketTimeoutException
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
internal class AndroidSntpClientTest {
    private val googleNtp = "time.google.com"

    @Test
    fun testSimpleRequest() {
        val address = AndroidSntpClient.queryHostAddress(googleNtp)
        val result = AndroidSntpClient.requestTime(address, AndroidSntpClient.NTP_PORT, 5000)
        if (result is Result.Failure) {
            result.error?.let { throw it }
            throw RuntimeException(result.errorMsg)
        }

        assert.that(result, isA<Result.Success>())

        if (result is Result.Success) {
            val ntpNow = result.ntpTimeMs
            val systemNow = System.currentTimeMillis()

            assert.that(abs(ntpNow - systemNow), lessThan(1000L))
        }
    }

    @Test
    fun testTimeout() {
        val address = AndroidSntpClient.queryHostAddress(googleNtp)
        val result = AndroidSntpClient.requestTime(address, AndroidSntpClient.NTP_PORT, 10)
        assert.that(result, isA<Result.Failure>())
        if (result is Result.Failure) {
            assert.that(result.error!!, isA<SocketTimeoutException>())
        }
    }
}
