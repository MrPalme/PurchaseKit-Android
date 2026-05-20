// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.policy

import com.markusmock.purchasekit.api.PurchasableOption

/**
 * Enforces the invariant that *at most one* auto-renewing subscription is
 * active in a given [PurchasableOption.subscriptionGroup] at a time.
 *
 * This policy does not buy or refund anything — it only resolves *which* of
 * two simultaneously-active subscriptions (an outcome that can occur for a
 * short window during a replacement) should be reported as the "primary"
 * active subscription via `PurchaseKitManager.primaryActiveSubscription`.
 *
 * Default rule: highest [PurchasableOption.tierRank] wins; on ties, the
 * option with the longer remaining expiration wins.
 *
 * Threading: pure value class.
 *
 * @since 0.1.0
 */
public fun interface SubscriptionExclusivityPolicy {

    /**
     * Returns the option that should be treated as "primary" given multiple
     * concurrently-active subscriptions in the same group.
     *
     * @param candidates Active subscriptions in the same [PurchasableOption.subscriptionGroup].
     *                   Always non-empty; size > 1 means a replacement is in flight.
     * @return One of [candidates]. Never `null`.
     */
    public fun selectPrimary(candidates: List<PurchasableOption>): PurchasableOption

    public companion object {
        /** Highest-tier wins; ties keep the candidate that appears first. */
        public val Default: SubscriptionExclusivityPolicy = SubscriptionExclusivityPolicy { candidates ->
            candidates.maxBy { it.tierRank }
        }
    }
}
