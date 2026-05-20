// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.sample.views

import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.ProductDetails
import com.markusmock.purchasekit.PurchaseKitDelegate
import com.markusmock.purchasekit.PurchaseKitManager
import com.markusmock.purchasekit.api.AnyPurchasableOption
import com.markusmock.purchasekit.model.EntitlementState
import com.markusmock.purchasekit.model.PurchaseError
import com.markusmock.purchasekit.model.PurchaseFlowState

/**
 * Demonstrates the *delegate-only* consumption path — no `StateFlow`
 * collection at all, just callbacks. Hosts that don't want to touch
 * coroutines (or Java callers — see [JavaInteropDemo]) use this shape.
 */
class DelegateOnlyController(
    private val manager: PurchaseKitManager,
    private val owner: LifecycleOwner,
) : PurchaseKitDelegate {

    fun attach() {
        manager.addListener(this, owner)
    }

    override fun onProductsLoaded(products: Map<AnyPurchasableOption, ProductDetails>) {
        // Wire to a status bar or analytics
    }

    override fun onProductsLoadFailed(error: PurchaseError) {
        // Surface a snackbar
    }

    override fun onEntitlementUpdated(option: AnyPurchasableOption, state: EntitlementState) {
        // Toggle feature gates
    }

    override fun onPurchaseFlowStateChanged(state: PurchaseFlowState, option: AnyPurchasableOption?) {
        // Update CTA spinners
    }

    override fun onRestoreCompleted(entitlements: Map<AnyPurchasableOption, EntitlementState>) {}
    override fun onRestoreFailed(error: PurchaseError) {}
}
