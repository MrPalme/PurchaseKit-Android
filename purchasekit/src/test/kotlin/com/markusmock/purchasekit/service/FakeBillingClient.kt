// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.QueryProductDetailsParams
import com.markusmock.purchasekit.model.BillingConnectionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-rolled fake [BillingBridge]. No reflection, no Mockito.
 *
 * Programmable per-call: tests set the canned responses, then drive the
 * service. Records every invocation in [invocations] for assertions.
 */
internal class FakeBillingClient : BillingBridge {

    private val state = MutableStateFlow(BillingConnectionState.Disconnected)
    private val purchases = MutableSharedFlow<PurchasesUpdate>(extraBufferCapacity = 32)

    override val connectionState: StateFlow<BillingConnectionState> = state.asStateFlow()
    override val purchasesUpdates: SharedFlow<PurchasesUpdate> = purchases.asSharedFlow()

    var connectResult: BillingResult = ok()
    var connectStateAfter: BillingConnectionState = BillingConnectionState.Connected
    var queryProductDetailsResult: ProductDetailsResult = emptyProductDetailsResult()
    var queryPurchasesResult: PurchasesResult = emptyPurchasesResult()
    var launchBillingFlowResult: BillingResult = ok()
    var acknowledgeResult: BillingResult = ok()
    var consumeResult: BillingResult = ok()

    val invocations = mutableListOf<String>()
    var endConnectionCalls = 0

    override suspend fun connect(): BillingResult {
        invocations += "connect"
        state.value = connectStateAfter
        return connectResult
    }

    override fun endConnection() {
        invocations += "endConnection"
        endConnectionCalls++
        state.value = BillingConnectionState.Disconnected
    }

    override fun isReady(): Boolean = state.value == BillingConnectionState.Connected

    override suspend fun queryProductDetails(params: QueryProductDetailsParams): ProductDetailsResult {
        invocations += "queryProductDetails"
        return queryProductDetailsResult
    }

    override suspend fun queryPurchases(productType: String): PurchasesResult {
        invocations += "queryPurchases:$productType"
        return queryPurchasesResult
    }

    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult {
        invocations += "launchBillingFlow"
        return launchBillingFlowResult
    }

    override suspend fun acknowledge(token: String): BillingResult {
        invocations += "acknowledge:$token"
        return acknowledgeResult
    }

    override suspend fun consume(token: String): BillingResult {
        invocations += "consume:$token"
        return consumeResult
    }

    /** Push a purchase update from the test. */
    suspend fun emit(result: BillingResult, purchases: List<Purchase> = emptyList()) {
        this.purchases.emit(PurchasesUpdate(result, purchases))
    }

    companion object {
        fun ok(): BillingResult =
            BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()

        fun result(code: Int, debug: String = ""): BillingResult =
            BillingResult.newBuilder().setResponseCode(code).setDebugMessage(debug).build()

        private fun emptyProductDetailsResult(): ProductDetailsResult =
            ProductDetailsResult(ok(), emptyList())

        private fun emptyPurchasesResult(): PurchasesResult =
            PurchasesResult(ok(), emptyList())
    }
}
