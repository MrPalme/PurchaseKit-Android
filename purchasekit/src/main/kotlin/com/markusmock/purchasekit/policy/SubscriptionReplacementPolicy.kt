// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.policy

import com.android.billingclient.api.BillingFlowParams
import com.markusmock.purchasekit.api.PurchasableOption

/**
 * Decides which Play Billing
 * [`SubscriptionReplacementMode`][BillingFlowParams.SubscriptionUpdateParams.ReplacementMode]
 * to use when switching from one active subscription to another within the
 * same [PurchasableOption.subscriptionGroup].
 *
 * The default implementation, [Default], is tier-aware (see [PurchasableOption.tierRank])
 * and matches the rules used by Link2 in production:
 *
 * - Upgrade (lower tier → higher tier): `WITH_TIME_PRORATION` (effective immediately, credited time).
 * - Downgrade (higher tier → lower tier): `DEFERRED` (effective at end of current period).
 * - Lateral (same tier, e.g. monthly → yearly): `CHARGE_PRORATED_PRICE` (charge difference now).
 *
 * Hosts that want different commercial rules implement this interface and
 * pass an instance via `PurchaseKitConfig.replacementPolicy`.
 *
 * Threading: pure value class.
 *
 * @since 0.1.0
 */
public fun interface SubscriptionReplacementPolicy {

    /**
     * Returns the Play Billing replacement mode for a switch from [from] to [to].
     *
     * @param from The currently-active subscription, or `null` when no switch is needed.
     * @param to   The subscription the user wants to purchase.
     * @return A value from
     *   [BillingFlowParams.SubscriptionUpdateParams.ReplacementMode]. Return
     *   [BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.UNKNOWN_REPLACEMENT_MODE]
     *   to indicate "no replacement params should be sent".
     */
    public fun replacementMode(from: PurchasableOption?, to: PurchasableOption): Int

    public companion object {
        /** Tier-aware default; see [SubscriptionReplacementPolicy] docs. */
        public val Default: SubscriptionReplacementPolicy = SubscriptionReplacementPolicy { from, to ->
            if (from == null) {
                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.UNKNOWN_REPLACEMENT_MODE
            } else when {
                from.tierRank < to.tierRank ->
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                from.tierRank > to.tierRank ->
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED
                else ->
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
            }
        }
    }
}
