// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType
import com.markusmock.purchasekit.model.PurchaseError
import com.markusmock.purchasekit.support.PurchaseKitLogger
import com.markusmock.purchasekit.support.w

/**
 * Looks up [ProductDetails] for a catalogue of [PurchasableOption]s.
 *
 * Splits a heterogeneous catalogue into two `BillingClient` calls (one for
 * `inapp`, one for `subs`) and merges the results back into a single map
 * keyed by `productId`.
 */
internal class ProductService(
    private val bridge: BillingBridge,
    private val logger: PurchaseKitLogger,
) {

    sealed class Result {
        data class Success(val details: Map<String, ProductDetails>) : Result()
        data class Failure(val error: PurchaseError) : Result()
    }

    suspend fun query(options: Collection<PurchasableOption>): Result {
        if (options.isEmpty()) return Result.Success(emptyMap())
        val byType = options.groupBy { playProductType(it.purchaseType) }
        val merged = HashMap<String, ProductDetails>(options.size)
        var fatal: PurchaseError? = null

        for ((productType, group) in byType) {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    group.map { option ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(option.productId)
                            .setProductType(productType)
                            .build()
                    },
                )
                .build()
            val result = try {
                bridge.queryProductDetails(params)
            } catch (t: Throwable) {
                logger.w(TAG, "queryProductDetails($productType) threw", t)
                fatal = PurchaseError.fromThrowable(t)
                continue
            }
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                logger.w(TAG, "queryProductDetails($productType) -> ${result.billingResult.responseCode}")
                fatal = fatal ?: PurchaseError.fromBillingResult(result.billingResult)
                continue
            }
            result.productDetailsList?.forEach { detail ->
                merged[detail.productId] = detail
            }
        }
        return if (merged.isEmpty() && fatal != null) Result.Failure(fatal) else Result.Success(merged)
    }

    private fun playProductType(type: PurchaseType): String = when (type) {
        PurchaseType.NON_CONSUMABLE, PurchaseType.CONSUMABLE -> BillingClient.ProductType.INAPP
        PurchaseType.AUTO_RENEWING_SUBSCRIPTION, PurchaseType.NON_RENEWING_SUBSCRIPTION ->
            BillingClient.ProductType.SUBS
    }

    private companion object {
        private const val TAG = "PurchaseKit.Product"
    }
}
