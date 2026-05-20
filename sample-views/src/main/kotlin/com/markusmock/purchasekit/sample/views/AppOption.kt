// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.sample.views

import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType

/** Sample catalogue, identical in shape to the Compose sample for parity. */
enum class AppOption(
    override val productId: String,
    override val purchaseType: PurchaseType,
    override val title: String,
    override val sortOrder: Int,
) : PurchasableOption {
    Monthly("sample.pro.monthly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Pro Monthly", 0),
    Yearly("sample.pro.yearly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Pro Yearly", 1),
    Lifetime("sample.lifetime", PurchaseType.NON_CONSUMABLE, "Lifetime", 2);

    override val subscriptionGroup: String? get() = if (purchaseType.isSubscription) "pro" else null
}
