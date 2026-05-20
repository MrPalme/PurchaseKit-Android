// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

/**
 * Optional host-defined grouping of [PurchasableOption]s plus the [Feature]s
 * the group unlocks. Used by paywalls that present multiple plans under one
 * heading (e.g. "Pro — monthly / yearly / lifetime").
 *
 * The library does not consume this type at runtime — it ships only as a
 * stable interface so hosts can describe their catalogue without inventing
 * one. Match [id] to [PurchasableOption.offeringId] to link the two.
 *
 * Threading: pure value contract.
 *
 * @since 0.1.0
 */
public interface PurchaseOffering {
    /** Stable identifier. Equals [PurchasableOption.offeringId] of member options. */
    public val id: String

    /** Untranslated heading. */
    public val title: String

    /** Optional sub-heading. */
    public val description: String? get() = null

    /** Features this offering unlocks. May be empty. */
    public val features: List<Feature> get() = emptyList()

    /** Display order across multiple offerings. */
    public val sortOrder: Int get() = 0
}
