/*
 * Copyright (C) 2008 The Android Open Source Project.
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

import android.os.SystemClock
import io.tempo.internal.SntpClient.Result
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Arrays
import kotlin.experimental.and

internal object AndroidSntpClient : SntpClient {
    class InetException(errorMsg: String) : RuntimeException(errorMsg)

    val NTP_PORT = 123

    private val ORIGINATE_TIME_OFFSET = 24
    private val RECEIVE_TIME_OFFSET = 32
    private val TRANSMIT_TIME_OFFSET = 40
    private val NTP_PACKET_SIZE = 48
    private val NTP_MODE_CLIENT = 3
    private val NTP_MODE_SERVER = 4
    private val NTP_MODE_BROADCAST = 5
    private val NTP_VERSION = 3
    private val NTP_LEAP_NOSYNC = 3
    private val NTP_STRATUM_DEATH = 0
    private val NTP_STRATUM_MAX = 15
    // Number of seconds between Jan 1, 1900 and Jan 1, 1970
    // 70 years plus 17 leap days
    private val OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L

    fun queryHostAddress(host: String): InetAddress {
        return try {
            Inet4Address.getByName(host)
        } catch (e: Exception) {
            val errorMsg = "Error getting the host ($host) inet address."
            throw InetException(errorMsg)
        }
    }

    override fun requestTime(address: InetAddress, port: Int, timeoutMs: Int): Result {
        fun useSocket(use: DatagramSocket.() -> Unit): Result? {
            // We can't use the nice 'Obj.use {}' pattern from Kotlin because
            // DatagramSocket() is not of type Closeable in Android API 19 or lower.
            val socket = DatagramSocket()
            val result = try {
                socket.soTimeout = timeoutMs
                use.invoke(socket)
                null
            } catch (e: Exception) {
                val errorMsg = "Error transmitting request/response"
                Result.Failure(e, errorMsg)
            }
            socket.close()
            return result
        }

        val buffer = ByteArray(NTP_PACKET_SIZE)
        // Set mode = 3 (client) and version = 3
        // Mode is in low 3 bits of first byte
        // Version is in bits 3-5 of first byte
        buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

        // Get current time and write it to the request packet
        val requestTime = System.currentTimeMillis()
        val requestTicks = SystemClock.elapsedRealtime()
        writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime)

        useSocket {
            // Request and Response
            val request = DatagramPacket(buffer, buffer.size, address, port)
            send(request)
            val response = DatagramPacket(buffer, buffer.size)
            receive(response)
        }?.let { failure -> return failure }

        // Read the response
        val responseTicks = SystemClock.elapsedRealtime()
        val responseTime = requestTime + (responseTicks - requestTicks)

        // Extract the results
        val leap = ((buffer[0].toInt() shr 6) and 0x3).toByte()
        val mode = (buffer[0].toInt() and 0x7).toByte()
        val stratum = buffer[1].toInt() and 0xFF

        val originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET)
        val receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET)
        val transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET)

        // Do sanity check according to RFC
        checkValidServerReply(leap, mode, stratum, transmitTime)?.let { failure ->
            return failure
        }

        val roundTripTime = responseTicks - requestTicks - (transmitTime - receiveTime)
        val clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2L

        // Save our results - use the times on this side of the network latency
        // (response rather than request time)
        return Result.Success(
                ntpTimeMs = responseTime + clockOffset,
                uptimeReferenceMs = responseTicks,
                roundTripTimeMs = roundTripTime)
    }


    /**
     * @return Not null if success, otherwise a Result.Failure
     */
    private fun checkValidServerReply(leap: Byte, mode: Byte, stratum: Int, transmitTime: Long)
            : Result.Failure? {
        val errorMsg = when {
            leap == NTP_LEAP_NOSYNC.toByte() -> "Unsynchronized server"
            mode != NTP_MODE_SERVER.toByte() && mode != NTP_MODE_BROADCAST.toByte() -> "Untrusted mode: $mode"
            stratum == NTP_STRATUM_DEATH || stratum > NTP_STRATUM_MAX -> "Untrusted stratum: $stratum"
            transmitTime == 0L -> "Zero transmit time"
            else -> null
        }
        return errorMsg?.let { Result.Failure(error = null, errorMsg = it) }
    }

    /**
     * Reads an unsigned 32 bit big endian number from the given offset in the buffer.
     */
    private fun read32(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset]
        val b1 = buffer[offset + 1]
        val b2 = buffer[offset + 2]
        val b3 = buffer[offset + 3]

        val signedToUnsigned = { b: Byte ->
            val x80 = (0x80).toByte()
            val x7f = (0x7F).toByte()
            if ((b and x80) == x80) (b and x7f).toLong() + (1L shl 7) else b.toLong()
        }
        // convert signed bytes to unsigned values
        val i0 = signedToUnsigned(b0)
        val i1 = signedToUnsigned(b1)
        val i2 = signedToUnsigned(b2)
        val i3 = signedToUnsigned(b3)

        return (i0 shl 24) + (i1 shl 16) + (i2 shl 8) + (i3)
    }

    /**
     * Reads the NTP time stamp at the given offset in the buffer and returns
     * it as a system time (milliseconds since January 1, 1970).
     */
    private fun readTimeStamp(buffer: ByteArray, offset: Int): Long {
        val seconds = read32(buffer, offset)
        val fraction = read32(buffer, offset + 4)
        // Special case: zero means zero.
        if (seconds == 0L && fraction == 0L) {
            return 0
        }
        return ((seconds - OFFSET_1900_TO_1970) * 1000L) + ((fraction * 1000L) / 0x100000000L)
    }

    /**
     * Writes system time (milliseconds since January 1, 1970) as an NTP time stamp
     * at the given offset in the buffer.
     */
    private fun writeTimeStamp(buffer: ByteArray, offset: Int, time: Long) {
        // Special case: zero means zero.
        if (time == 0L) {
            Arrays.fill(buffer, offset, offset + 8, 0.toByte())
            return
        }
        val milliseconds = time - (time / 1000L) * 1000L
        val seconds = (time / 1000L) + OFFSET_1900_TO_1970

        // write seconds in big endian format
        var o = offset
        buffer[o++] = (seconds shr 24).toByte()
        buffer[o++] = (seconds shr 16).toByte()
        buffer[o++] = (seconds shr 8).toByte()
        buffer[o++] = (seconds shr 0).toByte()
        val fraction = milliseconds * 0x100000000L / 1000L
        // write fraction in big endian format
        buffer[o++] = (fraction shr 24).toByte()
        buffer[o++] = (fraction shr 16).toByte()
        buffer[o++] = (fraction shr 8).toByte()
        // low order bits should be random data
        buffer[o] = ((Math.random() * 255.0).toLong()).toByte()
    }
}
