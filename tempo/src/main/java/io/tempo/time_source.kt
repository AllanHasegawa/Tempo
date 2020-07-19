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

package io.tempo

/**
 * @param[id] A unique id. It'll be used to manage the time source's cache.
 * @param[priority] The time source's priority. Time sources with highers priority are picked first.
 */
public data class TimeSourceConfig(val id: String, val priority: Int)

public interface TimeSource {
    public val config: TimeSourceConfig

    /**
     * @return A single containing the unix epoch time in milliseconds, or an error.
     */
    public suspend fun requestTime(): Long
}

public data class TimeSourceCache(
    val timeSourceId: String,
    val timeSourcePriority: Int,
    val estimatedBootTime: Long,
    val requestDeviceUptime: Long,
    val requestTime: Long,
    val bootCount: Int?
)