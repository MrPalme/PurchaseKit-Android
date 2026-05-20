// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

/**
 * The Google Play product category a [PurchasableOption] represents.
 *
 * Maps directly to the two Play product types (`inapp` / `subs`), refined into
 * four logical categories so consumers can describe their catalogue without
 * touching Play Billing constants. The library uses this to:
 *
 * - Pick the correct `BillingClient.queryProductDetails` product type.
 * - Decide whether `acknowledgePurchase` (subscriptions, non-consumables) or
 *   `consumePurchase` (consumables) applies after a successful purchase.
 * - Derive whether subscription replacement / exclusivity rules apply.
 *
 * Threading: pure value type, safe to access from any thread.
 *
 * @since 0.1.0
 */
public enum class PurchaseType {
    /** One-time, non-restorable, non-consumable Play `inapp` product (e.g. lifetime unlock). */
    NON_CONSUMABLE,

    /** One-time consumable Play `inapp` product (e.g. coin bundle). Must be consumed after grant. */
    CONSUMABLE,

    /** Auto-renewing Play `subs` product. Subject to replacement / exclusivity rules. */
    AUTO_RENEWING_SUBSCRIPTION,

    /** Prepaid (non-renewing) Play `subs` product. Treated as a fixed-window entitlement. */
    NON_RENEWING_SUBSCRIPTION;

    /** True for the two `subs` variants. */
    public val isSubscription: Boolean
        get() = this == AUTO_RENEWING_SUBSCRIPTION || this == NON_RENEWING_SUBSCRIPTION

    /** True for the two `inapp` variants. */
    public val isOneTime: Boolean
        get() = this == NON_CONSUMABLE || this == CONSUMABLE
}
