// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType
import com.markusmock.purchasekit.model.PurchaseError
import com.markusmock.purchasekit.policy.SubscriptionReplacementPolicy
import com.markusmock.purchasekit.support.PurchaseKitLogger
import com.markusmock.purchasekit.support.d
import com.markusmock.purchasekit.support.pickOfferToken
import com.markusmock.purchasekit.support.w
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Coordinates `launchBillingFlow`, restore, acknowledge / consume.
 *
 * Subscribes to [BillingBridge.purchasesUpdates] for the lifetime of [scope]
 * and forwards normalised events to the [TransactionServiceDelegate]. Owns
 * no UI state of its own.
 */
internal class TransactionService(
    private val bridge: BillingBridge,
    private val replacementPolicy: SubscriptionReplacementPolicy,
    private val logger: PurchaseKitLogger,
    private val scope: CoroutineScope,
) {

    private var delegate: TransactionServiceDelegate? = null
    private var subscription: Job? = null
    private val pendingByProductId: MutableMap<String, PurchasableOption> = HashMap()
    private var optionResolver: ((String) -> PurchasableOption?)? = null

    fun setDelegate(delegate: TransactionServiceDelegate?) {
        this.delegate = delegate
    }

    /** Registered once by the manager so the service can resolve `productId → PurchasableOption`. */
    fun setOptionResolver(resolver: (String) -> PurchasableOption?) {
        this.optionResolver = resolver
    }

    fun start() {
        if (subscription != null) return
        subscription = scope.launch {
            bridge.purchasesUpdates.collect { update ->
                handleUpdate(update)
            }
        }
    }

    fun stop() {
        subscription?.cancel()
        subscription = null
        delegate = null
        pendingByProductId.clear()
    }

    /**
     * Launches the Play purchase sheet. Returns the [PurchaseError] when a
     * preflight check fails synchronously, or `null` when Play has accepted
     * the call (success/failure now arrives via [PurchasesUpdate]).
     */
    fun launchBillingFlow(
        activity: Activity,
        option: PurchasableOption,
        productDetails: ProductDetails,
        basePlanId: String?,
        offerTag: String?,
        obfuscatedAccountId: String?,
        isPricePersonalized: Boolean,
        activePurchases: List<Purchase>,
    ): PurchaseError? {
        if (activity.isFinishing || activity.isDestroyed) {
            return PurchaseError.SystemError
        }

        val productParamsList = when (option.purchaseType) {
            PurchaseType.AUTO_RENEWING_SUBSCRIPTION, PurchaseType.NON_RENEWING_SUBSCRIPTION -> {
                val token = productDetails.pickOfferToken(basePlanId, offerTag)
                    ?: return PurchaseError.ProductUnavailable
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(token)
                        .build(),
                )
            }
            else -> listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build(),
            )
        }

        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productParamsList)
        obfuscatedAccountId?.let(builder::setObfuscatedAccountId)
        if (isPricePersonalized) builder.setIsOfferPersonalized(true)

        if (option.purchaseType == PurchaseType.AUTO_RENEWING_SUBSCRIPTION) {
            val existing = activePurchases.firstOrNull { purchase ->
                purchase.products.any { productId ->
                    optionResolver?.invoke(productId)?.subscriptionGroup == option.subscriptionGroup &&
                        productId != option.productId
                }
            }
            if (existing != null) {
                val fromOption = existing.products.firstNotNullOfOrNull { optionResolver?.invoke(it) }
                val mode = replacementPolicy.replacementMode(fromOption, option)
                if (mode != BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.UNKNOWN_REPLACEMENT_MODE) {
                    builder.setSubscriptionUpdateParams(
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(existing.purchaseToken)
                            .setSubscriptionReplacementMode(mode)
                            .build(),
                    )
                    logger.d(TAG, "Replacement ${fromOption?.productId}->${option.productId} mode=$mode")
                }
            }
        }

        pendingByProductId[option.productId] = option
        val result = bridge.launchBillingFlow(activity, builder.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingByProductId.remove(option.productId)
            return PurchaseError.fromBillingResult(result)
        }
        return null
    }

    /** Restores purchases. Posts [TransactionServiceDelegate.onRestoreCompleted] / `onRestoreFailed`. */
    suspend fun restore() {
        val all = mutableListOf<Purchase>()
        for (productType in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            val result = try {
                bridge.queryPurchases(productType)
            } catch (t: Throwable) {
                logger.w(TAG, "queryPurchases($productType) threw", t)
                delegate?.onRestoreFailed(PurchaseError.fromThrowable(t))
                return
            }
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                delegate?.onRestoreFailed(PurchaseError.fromBillingResult(result.billingResult))
                return
            }
            all += result.purchasesList
        }
        delegate?.onRestoreCompleted(all)
    }

    /** Queries the current state. Posts [TransactionServiceDelegate.onPurchasesQueried]. */
    suspend fun refresh() {
        val all = mutableListOf<Purchase>()
        for (productType in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            val result = try {
                bridge.queryPurchases(productType)
            } catch (t: Throwable) {
                logger.w(TAG, "refresh queryPurchases($productType) threw", t)
                return
            }
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                all += result.purchasesList
            }
        }
        delegate?.onPurchasesQueried(all)
    }

    suspend fun acknowledge(purchase: Purchase): Boolean {
        val result = try {
            bridge.acknowledge(purchase.purchaseToken)
        } catch (t: Throwable) {
            logger.w(TAG, "acknowledge threw", t)
            return false
        }
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    suspend fun consume(purchase: Purchase): Boolean {
        val result = try {
            bridge.consume(purchase.purchaseToken)
        } catch (t: Throwable) {
            logger.w(TAG, "consume threw", t)
            return false
        }
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun handleUpdate(update: PurchasesUpdate) {
        val code = update.result.responseCode
        if (code != BillingClient.BillingResponseCode.OK) {
            val option = pendingByProductId.values.firstOrNull()
            pendingByProductId.clear()
            if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
                delegate?.onPurchaseCancelled(option)
            } else {
                delegate?.onPurchaseFailed(option, PurchaseError.fromBillingResult(update.result))
            }
            return
        }
        for (purchase in update.purchases) {
            val option = purchase.products.firstNotNullOfOrNull { productId ->
                pendingByProductId.remove(productId) ?: optionResolver?.invoke(productId)
            } ?: continue
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED ->
                    delegate?.onPurchaseSucceeded(option, purchase)
                Purchase.PurchaseState.PENDING ->
                    delegate?.onPurchasePending(option, purchase)
                else -> delegate?.onPurchaseFailed(option, PurchaseError.Unknown("state=${purchase.purchaseState}"))
            }
        }
    }

    private companion object {
        private const val TAG = "PurchaseKit.Transact"
    }
}
