// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.markusmock.purchasekit.model.BillingConnectionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Internal seam between PurchaseKit services and the Play Billing client.
 *
 * Production code uses [RealBillingBridge] which wraps `BillingClient` 1:1.
 * Unit tests use a hand-rolled `FakeBillingClient` implementing the same
 * interface — no Mockito, no reflection.
 *
 * Threading: callers are responsible for the dispatcher; the implementation
 * publishes state via threadsafe `StateFlow` / `SharedFlow` collectors.
 */
internal interface BillingBridge {

    val connectionState: StateFlow<BillingConnectionState>
    val purchasesUpdates: SharedFlow<PurchasesUpdate>

    suspend fun connect(): BillingResult
    fun endConnection()
    fun isReady(): Boolean

    suspend fun queryProductDetails(params: QueryProductDetailsParams): ProductDetailsResult
    suspend fun queryPurchases(productType: String): PurchasesResult
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult
    suspend fun acknowledge(token: String): BillingResult
    suspend fun consume(token: String): BillingResult
}

internal data class PurchasesUpdate(
    val result: BillingResult,
    val purchases: List<Purchase>,
)

/**
 * Production [BillingBridge] implementation backed by a real [BillingClient].
 *
 * Uses `enableAutoServiceReconnection()` — there is no manual reconnect loop
 * (Link2 smell #9: replaced).
 */
internal class RealBillingBridge(
    context: Context,
) : BillingBridge, PurchasesUpdatedListener {

    private val state = MutableStateFlow(BillingConnectionState.Disconnected)
    private val purchases = MutableSharedFlow<PurchasesUpdate>(extraBufferCapacity = 8)

    override val connectionState: StateFlow<BillingConnectionState> = state.asStateFlow()
    override val purchasesUpdates: SharedFlow<PurchasesUpdate> = purchases.asSharedFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    override suspend fun connect(): BillingResult {
        if (client.isReady) {
            state.value = BillingConnectionState.Connected
            return ok()
        }
        state.value = BillingConnectionState.Connecting
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    state.value = when (result.responseCode) {
                        BillingClient.BillingResponseCode.OK ->
                            BillingConnectionState.Connected
                        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                            BillingConnectionState.Unavailable
                        else -> BillingConnectionState.Disconnected
                    }
                    if (cont.isActive) cont.resume(result)
                }

                override fun onBillingServiceDisconnected() {
                    state.value = BillingConnectionState.Disconnected
                }
            })
        }
    }

    override fun endConnection() {
        try {
            client.endConnection()
        } catch (_: Exception) {
            // already closed
        }
        state.value = BillingConnectionState.Disconnected
    }

    override fun isReady(): Boolean = client.isReady

    override suspend fun queryProductDetails(params: QueryProductDetailsParams): ProductDetailsResult =
        client.queryProductDetails(params)

    override suspend fun queryPurchases(productType: String): PurchasesResult =
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(productType).build(),
        )

    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult =
        client.launchBillingFlow(activity, params)

    override suspend fun acknowledge(token: String): BillingResult = client.acknowledgePurchase(
        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
    )

    override suspend fun consume(token: String): BillingResult = client.consumePurchase(
        ConsumeParams.newBuilder().setPurchaseToken(token).build(),
    ).billingResult

    override fun onPurchasesUpdated(result: BillingResult, list: MutableList<Purchase>?) {
        purchases.tryEmit(PurchasesUpdate(result, list.orEmpty()))
    }

    private fun ok(): BillingResult =
        BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
}
