// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PurchaseTypeTest {

    @Test
    fun `isSubscription and isOneTime are mutually exclusive`() {
        for (type in PurchaseType.values()) {
            assertTrue(
                type.isSubscription xor type.isOneTime,
                "$type must be either a subscription or one-time, not both / neither",
            )
        }
    }

    @Test
    fun `auto-renewing and non-renewing both count as subscriptions`() {
        assertTrue(PurchaseType.AUTO_RENEWING_SUBSCRIPTION.isSubscription)
        assertTrue(PurchaseType.NON_RENEWING_SUBSCRIPTION.isSubscription)
        assertFalse(PurchaseType.NON_CONSUMABLE.isSubscription)
        assertFalse(PurchaseType.CONSUMABLE.isSubscription)
    }
}
