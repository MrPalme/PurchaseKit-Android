// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.compose.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neutral, brand-free defaults used by the PurchaseKit Compose building blocks.
 *
 * Hosts who want their own look-and-feel either pass through Material 3
 * theming (the components honour `MaterialTheme.colorScheme` / `typography`)
 * or replace the components outright using the state flows directly.
 *
 * @since 0.1.0
 */
public object PurchaseKitDefaults {

    /** WCAG-friendly minimum touch target. */
    public val MinTouchTarget: Dp = 48.dp

    /** Default content padding for [com.markusmock.purchasekit.compose.PurchaseButton]. */
    public val ButtonPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp)

    /** Vertical gap between paywall blocks. */
    public val SectionSpacing: Dp = 16.dp
}
