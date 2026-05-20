// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.model

/**
 * Library-normalised Play Billing connection state.
 *
 * Replaces Link2's `StateFlow<Int>` of the raw `BillingClient.ConnectionState`
 * integer — this enum is the only public way to read connection status.
 *
 * Threading: immutable enum value.
 *
 * @since 0.1.0
 */
public enum class BillingConnectionState {
    /** No connection attempted yet, or [com.markusmock.purchasekit.PurchaseKitManager.shutdown] was called. */
    Disconnected,

    /** `BillingClient.startConnection` in flight. */
    Connecting,

    /** Connected and ready for Play Billing calls. */
    Connected,

    /** Play Billing not available on this device (e.g. no Play Store, AOSP build). */
    Unavailable,
}
