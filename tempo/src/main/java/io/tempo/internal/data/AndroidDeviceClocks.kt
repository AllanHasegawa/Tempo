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
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import io.tempo.internal.domain.DeviceClocks

internal class AndroidDeviceClocks(private val context: Context) : DeviceClocks {
    override fun bootCount(): Int? =
        if (Build.VERSION.SDK_INT >= 24)
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        else null

    override fun uptime(): Long = SystemClock.elapsedRealtime()
    override fun estimatedBootTime(): Long = System.currentTimeMillis() - uptime()
}
