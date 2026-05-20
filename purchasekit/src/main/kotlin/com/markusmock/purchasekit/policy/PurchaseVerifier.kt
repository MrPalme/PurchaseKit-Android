// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.policy

import com.android.billingclient.api.Purchase

/**
 * Optional server-side verification seam.
 *
 * Plugged in via `PurchaseKitConfig.verifier`. The library calls
 * [verify] *before* it acknowledges/consumes a purchase and *before* it
 * publishes the corresponding [com.markusmock.purchasekit.model.EntitlementState].
 *
 * Default: [NoOp] returns `true` — the library trusts Play Billing's signature.
 * Production apps that want defence in depth implement this against their
 * backend (which talks to Google's Real Time Developer Notifications and
 * subscriptions REST API).
 *
 * Threading: [verify] is called from `Dispatchers.IO`. Implementations may
 * suspend (network IO is welcome).
 *
 * Verification mode is controlled by
 * `PurchaseKitConfig.verificationFailureMode`:
 *
 * - `Block`: a `false` return blocks the entitlement grant and surfaces
 *   [com.markusmock.purchasekit.model.PurchaseError.VerificationFailed].
 * - `Warn`: a `false` return logs a warning but grants entitlement anyway
 *   (useful while wiring up a backend).
 *
 * @since 0.1.0
 */
public fun interface PurchaseVerifier {

    /**
     * Verifies a [purchase] against the host's authoritative source of truth.
     *
     * @return `true` if the purchase is valid; `false` to reject.
     */
    public suspend fun verify(purchase: Purchase): Boolean

    public companion object {
        /** Accept-everything default. Library trusts Play's signature. */
        public val NoOp: PurchaseVerifier = PurchaseVerifier { _ -> true }
    }
}

/**
 * How the library reacts when [PurchaseVerifier.verify] returns `false`.
 *
 * @since 0.1.0
 */
public enum class VerificationFailureMode {
    /** Reject the entitlement grant. Surface `PurchaseError.VerificationFailed`. */
    Block,

    /** Log a warning and grant entitlement anyway. Use while wiring up a backend. */
    Warn,
}
