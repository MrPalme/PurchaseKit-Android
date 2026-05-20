// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import com.android.billingclient.api.Purchase
import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.model.PurchaseError

/**
 * Internal callback bridge from [TransactionService] to [com.markusmock.purchasekit.PurchaseKitManager].
 *
 * Manager implements this; service calls it. Callbacks fire on the dispatcher
 * the service is running on — manager is responsible for hopping to the
 * main thread before publishing public state.
 */
internal interface TransactionServiceDelegate {
    fun onPurchaseSucceeded(option: PurchasableOption, purchase: Purchase)
    fun onPurchasePending(option: PurchasableOption, purchase: Purchase)
    fun onPurchaseFailed(option: PurchasableOption?, error: PurchaseError)
    fun onPurchaseCancelled(option: PurchasableOption?)
    fun onPurchasesQueried(purchases: List<Purchase>)
    fun onRestoreCompleted(purchases: List<Purchase>)
    fun onRestoreFailed(error: PurchaseError)
}
