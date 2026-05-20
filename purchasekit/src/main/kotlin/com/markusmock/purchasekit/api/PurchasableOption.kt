// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

/**
 * The host-defined description of a single purchasable item.
 *
 * Hosts typically implement this on an `enum class` so the catalogue is
 * exhaustive and the compiler can verify branches. The library never inspects
 * UI text — it only routes [productId] to Google Play and surfaces the rest
 * back to the host so paywalls can render without re-fetching strings.
 *
 * Equality and hashing are defined on [productId] alone so two
 * `PurchasableOption` instances referring to the same Play SKU compare equal
 * even across host versions of the type. Implementations using `data class`
 * or `enum` get this for free *only if* they keep [productId] stable.
 *
 * Example:
 * ```kotlin
 * enum class AppOption(override val productId: String) : PurchasableOption {
 *     PRO_MONTHLY("app.pro.monthly"),
 *     PRO_YEARLY("app.pro.yearly");
 *     override val purchaseType = PurchaseType.AUTO_RENEWING_SUBSCRIPTION
 *     override val title get() = name
 *     override val sortOrder get() = ordinal
 * }
 * ```
 *
 * Threading: pure value contract, callable from any thread.
 *
 * @since 0.1.0
 */
public interface PurchasableOption {
    /** Stable host identifier (often the enum case name). Need not equal [productId]. */
    public val id: String get() = productId

    /** Google Play product identifier. Must match the SKU configured in Play Console. */
    public val productId: String

    /** Whether this is a subscription or one-time purchase. */
    public val purchaseType: PurchaseType

    /** Untranslated human label. Hosts localise externally. */
    public val title: String

    /** Optional secondary label (e.g. plan length). Defaults to `null`. */
    public val subtitle: String? get() = null

    /** Display order within a paywall. Defaults to 0. */
    public val sortOrder: Int get() = 0

    /** Optional grouping identifier (e.g. `"pro"` to group monthly and yearly). */
    public val offeringId: String? get() = null

    /** Optional marketing badge. See [TierBadge]. */
    public val badge: TierBadge? get() = null

    /**
     * Host-defined ordinal used by the default
     * [com.markusmock.purchasekit.policy.SubscriptionReplacementPolicy] to pick
     * upgrade vs. downgrade vs. lateral replacement. Higher = "more premium".
     *
     * Defaults to [sortOrder] when the host does not override it. Hosts with a
     * tier hierarchy (e.g. `BASIC < PRO < ENTERPRISE`) should override.
     */
    public val tierRank: Int get() = sortOrder

    /**
     * Optional shared subscription-group key. Subscriptions that share the same
     * value are treated as alternatives by the default replacement policy
     * (`monthly` ↔ `yearly` under `"pro"`). Defaults to [offeringId].
     */
    public val subscriptionGroup: String? get() = offeringId
}
