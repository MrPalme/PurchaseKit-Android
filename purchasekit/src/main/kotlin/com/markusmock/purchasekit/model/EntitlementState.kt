// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.model

/**
 * The library's normalised view of a user's right to consume a single
 * [com.markusmock.purchasekit.api.PurchasableOption].
 *
 * Sourced from Google Play (`BillingClient.queryPurchasesAsync`) on every
 * connect / foreground / restore cycle. The persistence cache exists only to
 * paint a plausible UI before Play responds — it is overwritten by Play's
 * truth on the next query and never resurrects an [Inactive] option into
 * [SubscriptionActive].
 *
 * Threading: immutable, safe to share.
 *
 * @since 0.1.0
 */
public sealed class EntitlementState {
    /** The user has no record of ever purchasing this option, or it was refunded. */
    public data object Inactive : EntitlementState()

    /**
     * A non-consumable / non-renewing purchase that has been acknowledged.
     *
     * @property transactionId Google Play `Purchase.orderId` (best-effort; empty when Play omits it).
     */
    public data class NonConsumable(val transactionId: String) : EntitlementState()

    /**
     * Active auto-renewing subscription, currently valid.
     *
     * @property expirationEpochMillis Best-known expiration (epoch millis). May be 0 when Play does
     *                                 not return an explicit expiry — in which case [isActive] is
     *                                 driven by the underlying `Purchase.purchaseState`.
     * @property transactionId         Google Play `Purchase.orderId` (best-effort).
     */
    public data class SubscriptionActive(
        val expirationEpochMillis: Long,
        val transactionId: String,
    ) : EntitlementState()

    /**
     * A previously-active subscription that has expired (auto-renew off and
     * grace period elapsed).
     *
     * @property expirationEpochMillis Best-known expiration (epoch millis).
     */
    public data class SubscriptionExpired(val expirationEpochMillis: Long) : EntitlementState()

    /**
     * The user's purchase was revoked (refund, chargeback).
     *
     * @property revocationEpochMillis Best-known revocation time (epoch millis).
     */
    public data class Revoked(val revocationEpochMillis: Long) : EntitlementState()

    /** True only for [NonConsumable] and [SubscriptionActive]. */
    public val isActive: Boolean
        get() = this is NonConsumable || this is SubscriptionActive
}
