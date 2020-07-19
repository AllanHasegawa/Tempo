package io.tempo.internal.domain.useCases

import io.mockk.every
import io.mockk.mockk
import io.tempo.internal.domain.DeviceClocks
import io.tempo.internal.domain.TimeSourceWrapper
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

internal object GetTimeNowUCTests : Spek({
    describe("a valid cached time source") {
        val cacheRequestTime = 50L
        val cacheUptime = 10L

        val clock = mockk<DeviceClocks>()
        val uc = GetTimeNowUC(clock)

        fun now() = TimeSourceWrapper(
            timeSource = mockk(),
            cache = mockk {
                every { requestTime } returns cacheRequestTime
                every { requestDeviceUptime } returns cacheUptime
            }
        ).let(uc::invoke)

        it("and the clock uptime same as cache, then no time has passed") {
            every { clock.uptime() } returns 10L
            assertEquals(50L, now())
        }

        it("and the clock uptime forward than cache, then time has passed") {
            every { clock.uptime() } returns 20L
            assertEquals(60L, now())
        }
    }
})
