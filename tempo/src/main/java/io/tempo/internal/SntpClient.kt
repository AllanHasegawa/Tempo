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

import java.net.InetAddress

internal interface SntpClient {
    sealed class Result {
        /**
         * @property[ntpTimeMs] The resulting time in milliseconds at the time the request completed.
         * @property[uptimeReferenceMs]
         * @property[roundTripTimeMs]
         */
        data class Success(val ntpTimeMs: Long, val uptimeReferenceMs: Long, val roundTripTimeMs: Long) : Result()
        data class Failure(val error: Throwable?, val errorMsg: String) : Result()
    }

    /**
     * @param[address]    Address of the server.
     * @param[timeoutMs]  Network timeoutMs in milliseconds.
     * @return            Either a Result.Success or a Result.Failure.
     */
    fun requestTime(address: InetAddress, port: Int, timeoutMs: Int): Result
}
