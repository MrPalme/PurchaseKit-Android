// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit

import androidx.annotation.MainThread
import com.android.billingclient.api.ProductDetails
import com.markusmock.purchasekit.api.AnyPurchasableOption
import com.markusmock.purchasekit.model.EntitlementState
import com.markusmock.purchasekit.model.PurchaseError
import com.markusmock.purchasekit.model.PurchaseFlowState

/**
 * Callback-style consumer of [PurchaseKitManager] events.
 *
 * Hosts that prefer not to collect `StateFlow` (callback-only fragments, Java
 * consumers, pure View/XML apps) implement this and register via
 * [PurchaseKitManager.addListener]. The contract mirrors the iOS
 * `PurchaseKitDelegate` protocol so behaviour stays portable across platforms.
 *
 * Threading: every callback fires on the **main thread**. Implementations
 * must not block.
 *
 * @since 0.1.0
 */
public interface PurchaseKitDelegate {

    /** Called after [PurchaseKitManager.loadProducts] resolves successfully. */
    @MainThread
    public fun onProductsLoaded(products: Map<AnyPurchasableOption, ProductDetails>) {}

    /** Called when product loading fails. The [error] carries a stable code. */
    @MainThread
    public fun onProductsLoadFailed(error: PurchaseError) {}

    /**
     * Called whenever an option's [EntitlementState] transitions to a new
     * value. Replaces Link2's `onPurchaseStateUpdated`.
     */
    @MainThread
    public fun onEntitlementUpdated(option: AnyPurchasableOption, state: EntitlementState) {}

    /**
     * Called when the global [PurchaseFlowState] transitions. Use [option] to
     * disambiguate per-option failures when multiple purchases are in flight.
     */
    @MainThread
    public fun onPurchaseFlowStateChanged(state: PurchaseFlowState, option: AnyPurchasableOption?) {}

    /** Called after [PurchaseKitManager.restorePurchases] resolves. */
    @MainThread
    public fun onRestoreCompleted(entitlements: Map<AnyPurchasableOption, EntitlementState>) {}

    /** Called when [PurchaseKitManager.restorePurchases] fails. */
    @MainThread
    public fun onRestoreFailed(error: PurchaseError) {}
}
