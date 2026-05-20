// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.support

import com.android.billingclient.api.ProductDetails

/**
 * Picks a subscription offer token from [this] matching the optional
 * [basePlanId] and [offerTag]. Returns the first match (Play returns offers
 * in deterministic order) or `null` when no offer satisfies the filter.
 *
 * Selection rules:
 * - If both filters are `null`, returns the first offer.
 * - Otherwise, requires every provided filter to match
 *   (`basePlanId` against `ProductDetails.SubscriptionOfferDetails.basePlanId`,
 *   `offerTag` against `ProductDetails.SubscriptionOfferDetails.offerTags`).
 */
internal fun ProductDetails.pickOfferToken(
    basePlanId: String? = null,
    offerTag: String? = null,
): String? {
    val offers = subscriptionOfferDetails ?: return null
    val match = offers.firstOrNull { offer ->
        (basePlanId == null || offer.basePlanId == basePlanId) &&
            (offerTag == null || offer.offerTags.contains(offerTag))
    }
    return match?.offerToken
}
