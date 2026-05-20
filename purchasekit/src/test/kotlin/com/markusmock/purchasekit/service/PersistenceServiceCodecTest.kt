// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import com.markusmock.purchasekit.model.EntitlementState
import com.markusmock.purchasekit.service.PersistenceService.Companion.decode
import com.markusmock.purchasekit.service.PersistenceService.Companion.encode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.json.JSONObject

class PersistenceServiceCodecTest {

    @Test
    fun `Inactive round trips`() {
        val state: EntitlementState = EntitlementState.Inactive
        assertEquals(state, decode(encode(state)))
    }

    @Test
    fun `NonConsumable round trips`() {
        val state = EntitlementState.NonConsumable("order-1234")
        assertEquals(state, decode(encode(state)))
    }

    @Test
    fun `SubscriptionActive round trips`() {
        val state = EntitlementState.SubscriptionActive(
            expirationEpochMillis = 1_700_000_000_000L,
            transactionId = "tx-1",
        )
        assertEquals(state, decode(encode(state)))
    }

    @Test
    fun `SubscriptionExpired round trips`() {
        val state = EntitlementState.SubscriptionExpired(1_700_000_000_000L)
        assertEquals(state, decode(encode(state)))
    }

    @Test
    fun `Revoked round trips`() {
        val state = EntitlementState.Revoked(1_700_000_000_000L)
        assertEquals(state, decode(encode(state)))
    }

    @Test
    fun `unknown kind decodes to Inactive`() {
        val obj = JSONObject().put("v", 1).put("kind", "mystery")
        assertEquals(EntitlementState.Inactive, decode(obj))
    }

    @Test
    fun `wrong version falls back to Inactive`() {
        val obj = encode(EntitlementState.NonConsumable("tx")).put("v", 99)
        assertEquals(EntitlementState.Inactive, decode(obj))
    }

    @Test
    fun `Inactive never resurrects into Active across decode`() {
        // Defence-in-depth: a stale "subscription_active" payload with version
        // 0 must be discarded rather than resurrecting an entitlement.
        val staleActive = JSONObject()
            .put("v", 0)
            .put("kind", "subscription_active")
            .put("expiresAt", Long.MAX_VALUE)
            .put("tx", "ghost")
        assertEquals(EntitlementState.Inactive, decode(staleActive))
    }
}
