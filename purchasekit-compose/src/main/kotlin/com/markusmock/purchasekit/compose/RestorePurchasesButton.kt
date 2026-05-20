// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.compose

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.markusmock.purchasekit.compose.theme.PurchaseKitDefaults

/**
 * "Restore Purchases" text button.
 *
 * Wired by the host to [com.markusmock.purchasekit.PurchaseKitManager.restorePurchases].
 *
 * Accessibility: announces [label] as both the visible text and the content
 * description (English fallback "Restore Purchases").
 *
 * @since 0.1.0
 */
@Composable
public fun RestorePurchasesButton(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Restore Purchases",
) {
    TextButton(
        onClick = onRestore,
        modifier = modifier
            .defaultMinSize(minHeight = PurchaseKitDefaults.MinTouchTarget)
            .semantics { contentDescription = label },
    ) {
        Text(label)
    }
}
