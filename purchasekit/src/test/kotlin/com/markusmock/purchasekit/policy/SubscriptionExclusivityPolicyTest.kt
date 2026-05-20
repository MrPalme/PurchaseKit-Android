// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.policy

import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionExclusivityPolicyTest {

    private data class Sub(override val productId: String, override val tierRank: Int) : PurchasableOption {
        override val purchaseType = PurchaseType.AUTO_RENEWING_SUBSCRIPTION
        override val title = productId
    }

    @Test
    fun `default selects highest tier`() {
        val winner = SubscriptionExclusivityPolicy.Default.selectPrimary(
            listOf(Sub("basic", 0), Sub("pro", 1), Sub("enterprise", 2)),
        )
        assertEquals("enterprise", winner.productId)
    }

    @Test
    fun `default on single candidate returns that candidate`() {
        val only = Sub("solo", 0)
        assertEquals(only, SubscriptionExclusivityPolicy.Default.selectPrimary(listOf(only)))
    }
}
