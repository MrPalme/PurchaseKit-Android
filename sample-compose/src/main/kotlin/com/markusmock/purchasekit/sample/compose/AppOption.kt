// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.sample.compose

import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType
import com.markusmock.purchasekit.api.TierBadge

/** Sample catalogue. Replace product IDs with the real ones from Play Console. */
enum class AppOption(
    override val productId: String,
    override val purchaseType: PurchaseType,
    override val title: String,
    override val sortOrder: Int,
    override val badge: TierBadge? = null,
) : PurchasableOption {
    Monthly("sample.pro.monthly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Pro Monthly", 0),
    Yearly("sample.pro.yearly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Pro Yearly", 1, badge = TierBadge.BestValue),
    Lifetime("sample.lifetime", PurchaseType.NON_CONSUMABLE, "Lifetime", 2);

    override val subscriptionGroup: String? get() = if (purchaseType.isSubscription) "pro" else null
}
