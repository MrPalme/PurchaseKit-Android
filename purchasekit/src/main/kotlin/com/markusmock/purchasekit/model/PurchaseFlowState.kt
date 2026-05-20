// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.model

/**
 * Transient, UI-only purchase flow state.
 *
 * This is *not* persisted and is never the source of truth for entitlement —
 * use [EntitlementState] for that. The library transitions through these
 * values during a purchase attempt:
 *
 * `Idle → Purchasing → (Idle | Pending | Failed)`.
 *
 * Threading: immutable.
 *
 * @since 0.1.0
 */
public sealed class PurchaseFlowState {
    /** No purchase in progress. */
    public data object Idle : PurchaseFlowState()

    /** Play purchase sheet shown / billing flow active. */
    public data object Purchasing : PurchaseFlowState()

    /** Purchase awaiting external completion (cash, family approval). */
    public data object Pending : PurchaseFlowState()

    /** Last attempt failed; reset to [Idle] on next attempt. */
    public data class Failed(val error: PurchaseError) : PurchaseFlowState()
}
