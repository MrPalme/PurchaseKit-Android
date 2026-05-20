// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

/**
 * Type-erased snapshot of a [PurchasableOption] suitable for use as a map key.
 *
 * The library exposes its state as `Map<AnyPurchasableOption, …>` so consumers
 * can iterate the full catalogue without having to know the concrete host enum
 * type. Snapshotting the values (instead of holding a reference to the
 * original option) means the wrapper stays stable even if the host's enum
 * `Companion.values()` re-orders between releases.
 *
 * Equality follows the [PurchasableOption] contract (by [productId]) so a
 * `Map.get(AnyPurchasableOption(myEnumValue))` lookup returns the same entry
 * as the original.
 *
 * Threading: immutable value class.
 *
 * @since 0.1.0
 */
public data class AnyPurchasableOption(
    override val id: String,
    override val productId: String,
    override val purchaseType: PurchaseType,
    override val title: String,
    override val subtitle: String? = null,
    override val sortOrder: Int = 0,
    override val offeringId: String? = null,
    override val badge: TierBadge? = null,
    override val tierRank: Int = sortOrder,
    override val subscriptionGroup: String? = offeringId,
) : PurchasableOption {

    public companion object {
        /**
         * Snapshots [option] into a value-stable [AnyPurchasableOption].
         *
         * Idempotent: passing an existing [AnyPurchasableOption] returns the
         * same instance.
         */
        @JvmStatic
        public fun of(option: PurchasableOption): AnyPurchasableOption =
            option as? AnyPurchasableOption
                ?: AnyPurchasableOption(
                    id = option.id,
                    productId = option.productId,
                    purchaseType = option.purchaseType,
                    title = option.title,
                    subtitle = option.subtitle,
                    sortOrder = option.sortOrder,
                    offeringId = option.offeringId,
                    badge = option.badge,
                    tierRank = option.tierRank,
                    subscriptionGroup = option.subscriptionGroup,
                )
    }

    override fun equals(other: Any?): Boolean =
        other is PurchasableOption && other.productId == productId

    override fun hashCode(): Int = productId.hashCode()
}
