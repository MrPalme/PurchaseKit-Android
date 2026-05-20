// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntitlementStateTest {

    @Test
    fun `isActive returns true only for NonConsumable and SubscriptionActive`() {
        assertTrue(EntitlementState.NonConsumable("tx").isActive)
        assertTrue(EntitlementState.SubscriptionActive(1L, "tx").isActive)
        assertFalse(EntitlementState.Inactive.isActive)
        assertFalse(EntitlementState.SubscriptionExpired(1L).isActive)
        assertFalse(EntitlementState.Revoked(1L).isActive)
    }
}
