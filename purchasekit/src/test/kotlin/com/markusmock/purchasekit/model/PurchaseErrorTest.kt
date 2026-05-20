// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.model

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class PurchaseErrorTest {

    private fun result(code: Int): BillingResult =
        BillingResult.newBuilder().setResponseCode(code).setDebugMessage("test").build()

    @Test
    fun `BillingResult mapping is exhaustive over enumerated codes`() {
        assertEquals(
            PurchaseError.UserCancelled,
            PurchaseError.fromBillingResult(result(BillingClient.BillingResponseCode.USER_CANCELED)),
        )
        assertEquals(
            PurchaseError.NetworkError,
            PurchaseError.fromBillingResult(result(BillingClient.BillingResponseCode.NETWORK_ERROR)),
        )
        assertEquals(
            PurchaseError.StoreProblem,
            PurchaseError.fromBillingResult(result(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)),
        )
        assertEquals(
            PurchaseError.PurchaseNotAllowed,
            PurchaseError.fromBillingResult(result(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)),
        )
        assertEquals(
            PurchaseError.ProductUnavailable,
            PurchaseError.fromBillingResult(result(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)),
        )
        assertEquals(
            PurchaseError.AlreadyOwned,
            PurchaseError.fromBillingResult(result(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)),
        )
        assertEquals(
            PurchaseError.NotOwned,
            PurchaseError.fromBillingResult(result(BillingClient.BillingResponseCode.ITEM_NOT_OWNED)),
        )
        assertEquals(
            PurchaseError.SystemError,
            PurchaseError.fromBillingResult(result(BillingClient.BillingResponseCode.DEVELOPER_ERROR)),
        )
    }

    @Test
    fun `unknown response codes route to Unknown with diagnostic`() {
        val unknown = PurchaseError.fromBillingResult(result(-9999)) as PurchaseError.Unknown
        assertEquals("unknown", unknown.code)
    }

    @Test
    fun `fromThrowable maps IOException to NetworkError`() {
        assertEquals(PurchaseError.NetworkError, PurchaseError.fromThrowable(IOException("offline")))
    }

    @Test
    fun `fromThrowable maps unknown exceptions to Unknown carrying the message`() {
        val error = PurchaseError.fromThrowable(RuntimeException("boom")) as PurchaseError.Unknown
        assertEquals("unknown", error.code)
        assertEquals("boom", error.debugMessage)
    }

    @Test
    fun `code is stable across releases`() {
        // Snapshot test — guard against accidental renames.
        assertEquals("user_cancelled", PurchaseError.UserCancelled.code)
        assertEquals("network_error", PurchaseError.NetworkError.code)
        assertEquals("store_problem", PurchaseError.StoreProblem.code)
        assertEquals("purchase_not_allowed", PurchaseError.PurchaseNotAllowed.code)
        assertEquals("product_unavailable", PurchaseError.ProductUnavailable.code)
        assertEquals("already_owned", PurchaseError.AlreadyOwned.code)
        assertEquals("not_owned", PurchaseError.NotOwned.code)
        assertEquals("system_error", PurchaseError.SystemError.code)
        assertEquals("verification_failed", PurchaseError.VerificationFailed.code)
        assertEquals("pending", PurchaseError.Pending.code)
    }
}
