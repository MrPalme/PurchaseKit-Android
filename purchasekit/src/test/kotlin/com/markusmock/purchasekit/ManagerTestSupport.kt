// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit

import com.markusmock.purchasekit.api.AnyPurchasableOption
import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType
import com.markusmock.purchasekit.api.TierBadge

/**
 * Synthetic catalogue used across manager tests.
 */
internal enum class TestOption(
    override val productId: String,
    override val purchaseType: PurchaseType,
    override val sortOrder: Int,
    override val tierRank: Int,
    override val subscriptionGroup: String? = "pro",
    override val badge: TierBadge? = null,
) : PurchasableOption {
    Monthly("app.pro.monthly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, 0, 0),
    Yearly("app.pro.yearly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, 1, 0, badge = TierBadge.BestValue),
    Lifetime("app.lifetime", PurchaseType.NON_CONSUMABLE, 2, 0, subscriptionGroup = null);

    override val title: String get() = name
}

internal fun allTestOptions(): List<AnyPurchasableOption> =
    TestOption.values().map(AnyPurchasableOption.Companion::of)
