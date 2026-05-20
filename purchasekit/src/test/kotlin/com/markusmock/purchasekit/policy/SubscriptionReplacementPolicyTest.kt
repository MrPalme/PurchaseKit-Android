// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.policy

import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode
import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionReplacementPolicyTest {

    private data class Sub(
        override val productId: String,
        override val tierRank: Int,
    ) : PurchasableOption {
        override val purchaseType = PurchaseType.AUTO_RENEWING_SUBSCRIPTION
        override val title = productId
    }

    @Test
    fun `null from yields UNKNOWN replacement`() {
        val mode = SubscriptionReplacementPolicy.Default.replacementMode(
            from = null,
            to = Sub("a", 1),
        )
        assertEquals(ReplacementMode.UNKNOWN_REPLACEMENT_MODE, mode)
    }

    @Test
    fun `upgrade applies time proration`() {
        val mode = SubscriptionReplacementPolicy.Default.replacementMode(
            from = Sub("basic", 0),
            to = Sub("pro", 1),
        )
        assertEquals(ReplacementMode.WITH_TIME_PRORATION, mode)
    }

    @Test
    fun `downgrade defers until end of period`() {
        val mode = SubscriptionReplacementPolicy.Default.replacementMode(
            from = Sub("pro", 1),
            to = Sub("basic", 0),
        )
        assertEquals(ReplacementMode.DEFERRED, mode)
    }

    @Test
    fun `lateral charges prorated price`() {
        val mode = SubscriptionReplacementPolicy.Default.replacementMode(
            from = Sub("pro_monthly", 1),
            to = Sub("pro_yearly", 1),
        )
        assertEquals(ReplacementMode.CHARGE_PRORATED_PRICE, mode)
    }

    @Test
    fun `custom policy can override default rules entirely`() {
        val alwaysImmediate = SubscriptionReplacementPolicy { _, _ -> ReplacementMode.WITH_TIME_PRORATION }
        assertEquals(
            ReplacementMode.WITH_TIME_PRORATION,
            alwaysImmediate.replacementMode(Sub("a", 5), Sub("b", 0)),
        )
    }
}
