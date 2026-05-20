// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.model

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import java.io.IOException

/**
 * Library-normalised purchase error category.
 *
 * Carries a stable [code] string (suitable for analytics and host-side
 * message lookup) and an optional [debugMessage] for diagnostics. The library
 * **never** returns a localised string — hosts map [code] to their own
 * resources. This is the explicit successor of Link2's
 * `StateFlow<String?> errorMessage`, which leaked localisation into the
 * library.
 *
 * Threading: immutable.
 *
 * @since 0.1.0
 */
public sealed class PurchaseError(
    public val code: String,
    public val debugMessage: String? = null,
) {
    /** User cancelled the Play purchase sheet. */
    public data object UserCancelled : PurchaseError("user_cancelled")

    /** Purchase is pending external completion (e.g. cash payment). */
    public data object Pending : PurchaseError("pending")

    /** Connectivity issue; Play could not be reached. */
    public data object NetworkError : PurchaseError("network_error")

    /** Play service / Google Play app problem. */
    public data object StoreProblem : PurchaseError("store_problem")

    /** Purchases disabled on this account/device (parental, country, etc.). */
    public data object PurchaseNotAllowed : PurchaseError("purchase_not_allowed")

    /** Requested SKU is unknown or not available in the user's region. */
    public data object ProductUnavailable : PurchaseError("product_unavailable")

    /** Already-owned one-time product (`inapp`). Often needs a restore. */
    public data object AlreadyOwned : PurchaseError("already_owned")

    /** Operation requires ownership the user does not have. */
    public data object NotOwned : PurchaseError("not_owned")

    /** Developer / system error (typically misconfiguration). */
    public data object SystemError : PurchaseError("system_error")

    /** Server-side verification rejected the purchase. */
    public data object VerificationFailed : PurchaseError("verification_failed")

    /** Catch-all for response codes the library does not enumerate. */
    public class Unknown(debugMessage: String? = null) :
        PurchaseError("unknown", debugMessage) {
        override fun equals(other: Any?): Boolean =
            other is Unknown && other.debugMessage == debugMessage
        override fun hashCode(): Int = debugMessage.hashCode()
    }

    final override fun toString(): String =
        if (debugMessage.isNullOrBlank()) code else "$code ($debugMessage)"

    public companion object {
        /**
         * Converts a [BillingResult] from a Play Billing call into a [PurchaseError].
         *
         * Caller invariant: only call when `responseCode != OK`. Passing OK
         * returns [Unknown] (Play returned success but caller treated it as an
         * error — a programming bug worth surfacing).
         */
        @JvmStatic
        public fun fromBillingResult(result: BillingResult): PurchaseError = when (result.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> UserCancelled
            BillingClient.BillingResponseCode.NETWORK_ERROR -> NetworkError
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.ERROR,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> StoreProblem
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> PurchaseNotAllowed
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> ProductUnavailable
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> SystemError
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> AlreadyOwned
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> NotOwned
            BillingClient.BillingResponseCode.OK -> Unknown("unexpected_ok_treated_as_error")
            else -> Unknown("responseCode=${result.responseCode}")
        }

        /**
         * Maps a [Throwable] caught in the library's coroutine perimeter to a
         * [PurchaseError]. Re-throws [kotlinx.coroutines.CancellationException]
         * is the caller's responsibility.
         */
        @JvmStatic
        public fun fromThrowable(t: Throwable): PurchaseError = when (t) {
            is IOException -> NetworkError
            else -> Unknown(t.message)
        }
    }
}
