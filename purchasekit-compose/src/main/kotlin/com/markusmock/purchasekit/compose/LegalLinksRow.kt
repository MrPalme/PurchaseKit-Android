// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.markusmock.purchasekit.compose.theme.PurchaseKitDefaults

/**
 * A horizontal row of terms / privacy / info links, rendered as M3 text buttons.
 *
 * Each link is independently optional; passing `null` for a URL hides that link.
 *
 * Accessibility: each link's `contentDescription` carries the visible label
 * (host-localised); touch targets are at least 48dp tall.
 *
 * @since 0.1.0
 */
@Composable
public fun LegalLinksRow(
    termsUrl: String? = null,
    privacyUrl: String? = null,
    infoUrl: String? = null,
    termsLabel: String = "Terms",
    privacyLabel: String = "Privacy",
    infoLabel: String = "Info",
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (termsUrl != null) {
            TextButton(
                onClick = { uriHandler.openUri(termsUrl) },
                modifier = Modifier
                    .defaultMinSize(minHeight = PurchaseKitDefaults.MinTouchTarget)
                    .semantics { contentDescription = termsLabel },
            ) { Text(termsLabel) }
        }
        if (privacyUrl != null) {
            TextButton(
                onClick = { uriHandler.openUri(privacyUrl) },
                modifier = Modifier
                    .defaultMinSize(minHeight = PurchaseKitDefaults.MinTouchTarget)
                    .semantics { contentDescription = privacyLabel },
            ) { Text(privacyLabel) }
        }
        if (infoUrl != null) {
            TextButton(
                onClick = { uriHandler.openUri(infoUrl) },
                modifier = Modifier
                    .defaultMinSize(minHeight = PurchaseKitDefaults.MinTouchTarget)
                    .semantics { contentDescription = infoLabel },
            ) { Text(infoLabel) }
        }
    }
}
