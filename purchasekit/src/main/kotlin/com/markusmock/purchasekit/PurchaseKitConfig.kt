// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit

import com.markusmock.purchasekit.policy.PurchaseVerifier
import com.markusmock.purchasekit.policy.SubscriptionExclusivityPolicy
import com.markusmock.purchasekit.policy.SubscriptionReplacementPolicy
import com.markusmock.purchasekit.policy.VerificationFailureMode
import com.markusmock.purchasekit.service.NetworkService
import com.markusmock.purchasekit.support.PurchaseKitLogger

/**
 * Bundled configuration for [PurchaseKitManager].
 *
 * Every field has a sensible default so a host can call
 * `PurchaseKitManager.create(context, options)` without ever constructing
 * this type. Hosts that need to swap one seam (e.g. plug in a backend
 * [PurchaseVerifier]) should use Kotlin's `copy(...)` syntax:
 *
 * ```kotlin
 * val manager = PurchaseKitManager.create(
 *     context,
 *     AppOption.entries,
 *     PurchaseKitConfig(verifier = MyServerVerifier(api)),
 * )
 * ```
 *
 * Threading: immutable.
 *
 * @property logger                  Diagnostic sink. Replace to bridge into your logging pipeline.
 * @property networkService          Optional connectivity awareness. `null` = library always reports `true`.
 * @property verifier                Optional server-side verification.
 * @property verificationFailureMode How to react when [verifier] returns `false`.
 * @property exclusivityPolicy       Picks the primary subscription when multiple are concurrently active.
 * @property replacementPolicy       Picks Play's replacement mode on subscription switches.
 * @property persistencePrefsName    `SharedPreferences` filename for the entitlement cache.
 *
 * @since 0.1.0
 */
public data class PurchaseKitConfig(
    val logger: PurchaseKitLogger = PurchaseKitLogger.Default,
    val networkService: NetworkService? = null,
    val verifier: PurchaseVerifier = PurchaseVerifier.NoOp,
    val verificationFailureMode: VerificationFailureMode = VerificationFailureMode.Warn,
    val exclusivityPolicy: SubscriptionExclusivityPolicy = SubscriptionExclusivityPolicy.Default,
    val replacementPolicy: SubscriptionReplacementPolicy = SubscriptionReplacementPolicy.Default,
    val persistencePrefsName: String = "purchasekit_prefs",
)
