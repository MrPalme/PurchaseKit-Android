// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

/**
 * Optional host-defined description of a capability that an entitlement unlocks.
 *
 * The library never reads feature flags itself — feature gating is an
 * application-level concern. This contract exists so hosts can publish the
 * "what you get" lists shown on a paywall without inventing their own type.
 *
 * Threading: pure value contract.
 *
 * @since 0.1.0
 */
public interface Feature {
    /** Stable identifier used for equality. */
    public val id: String

    /** Untranslated display name. Hosts localise externally. */
    public val localizedName: String

    /** Untranslated longer description. Hosts localise externally. */
    public val localizedDescription: String
}
