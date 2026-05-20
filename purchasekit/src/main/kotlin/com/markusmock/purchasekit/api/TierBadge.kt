// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

/**
 * Optional badge metadata a host can attach to a [PurchasableOption] to drive
 * paywall accents (e.g. "Best Value", "Save 33%").
 *
 * The library never localises [defaultText]; it returns an English fallback so
 * host UI can render *something* before localisation is wired up. Production
 * apps are expected to map a badge to their own localisation keys.
 *
 * Threading: immutable value type, safe to share across threads.
 *
 * @since 0.1.0
 */
public sealed class TierBadge {
    /** Marketing badge for the best-value option (typically the yearly plan). */
    public data object BestValue : TierBadge()

    /** Marketing badge for the most-popular option (typically the monthly plan). */
    public data object MostPopular : TierBadge()

    /**
     * Computed savings vs. a baseline price, in whole percent.
     *
     * @property percent Whole-number percentage savings. Must be in `1..99`.
     */
    public data class SavePercent(public val percent: Int) : TierBadge() {
        init {
            require(percent in 1..99) { "SavePercent expects 1..99, got $percent" }
        }
    }

    /** Host-defined badge with raw text. Used for non-standard accents. */
    public data class Custom(public val text: String) : TierBadge()

    /**
     * Best-effort English fallback. Hosts that need localisation should map the
     * concrete subtype to their own resource catalogue.
     */
    public val defaultText: String
        get() = when (this) {
            BestValue -> "Best Value"
            MostPopular -> "Most Popular"
            is SavePercent -> "Save $percent%"
            is Custom -> text
        }
}
