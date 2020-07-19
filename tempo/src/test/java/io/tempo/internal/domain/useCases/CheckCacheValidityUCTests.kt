package io.tempo.internal.domain.useCases

import io.mockk.every
import io.mockk.mockk
import io.tempo.TimeSourceCache
import io.tempo.internal.domain.DeviceClocks
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal object CheckCacheValidityUCTests : Spek({
    val baseCache = TimeSourceCache(
        timeSourceId = "id",
        timeSourcePriority = 10,
        estimatedBootTime = 9_000L,
        requestDeviceUptime = 60L,
        requestTime = 100L,
        bootCount = 10
    )

    fun TimeSourceCache.isValid(clockHasBootCount: Boolean = true): Boolean {
        val clock = mockk<DeviceClocks> {
            every { bootCount() } answers { if (clockHasBootCount) 10 else null }
            every { estimatedBootTime() } returns 10_000L
        }

        return CheckCacheValidityUC(clock)(this)
    }

    describe("A valid cache with boot count") {
        val validCache = baseCache.copy()

        it("should return true") {
            assertTrue(validCache.isValid())
        }
    }

    describe("An invalid cache with boot count") {
        val invalidCache = baseCache.copy(bootCount = 9)

        it("should return false") {
            assertFalse(invalidCache.isValid())
        }
    }

    describe("A valid cache without boot count") {
        val validCache = baseCache.copy(bootCount = null)

        it("should return true") {
            assertTrue(validCache.isValid(clockHasBootCount = false))
        }
    }

    describe("An invalid cache without boot count") {
        val invalidCache = baseCache.copy(bootCount = null, estimatedBootTime = 4_000L)

        it("should return false") {
            assertFalse(invalidCache.isValid(clockHasBootCount = false))
        }
    }
})