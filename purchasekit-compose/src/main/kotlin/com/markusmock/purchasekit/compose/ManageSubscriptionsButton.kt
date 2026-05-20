// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.compose

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.markusmock.purchasekit.PurchaseKitManager
import com.markusmock.purchasekit.compose.theme.PurchaseKitDefaults

/**
 * "Manage Subscriptions" text button. Tapping launches the Play Store
 * subscription management UI via [PurchaseKitManager.openSubscriptionManagement].
 *
 * @param productId Optional Play SKU to deep-link to.
 * @param label     Host-localised label. Default "Manage Subscriptions".
 *
 * @since 0.1.0
 */
@Composable
public fun ManageSubscriptionsButton(
    productId: String? = null,
    modifier: Modifier = Modifier,
    label: String = "Manage Subscriptions",
) {
    val context = LocalContext.current
    TextButton(
        onClick = { PurchaseKitManager.openSubscriptionManagement(context, productId) },
        modifier = modifier
            .defaultMinSize(minHeight = PurchaseKitDefaults.MinTouchTarget)
            .semantics { contentDescription = label },
    ) {
        Text(label)
    }
}
