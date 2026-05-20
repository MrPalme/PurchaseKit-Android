// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TierBadgeTest {

    @Test
    fun `default texts are stable english fallbacks`() {
        assertEquals("Best Value", TierBadge.BestValue.defaultText)
        assertEquals("Most Popular", TierBadge.MostPopular.defaultText)
        assertEquals("Save 33%", TierBadge.SavePercent(33).defaultText)
        assertEquals("Founder", TierBadge.Custom("Founder").defaultText)
    }

    @Test
    fun `SavePercent rejects out-of-range values`() {
        assertThrows(IllegalArgumentException::class.java) { TierBadge.SavePercent(0) }
        assertThrows(IllegalArgumentException::class.java) { TierBadge.SavePercent(100) }
    }
}
