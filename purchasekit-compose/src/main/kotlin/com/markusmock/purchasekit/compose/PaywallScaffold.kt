// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.markusmock.purchasekit.compose.theme.PurchaseKitDefaults

/**
 * Opinionated scaffold for a paywall built from the PurchaseKit primitives.
 *
 * Renders a vertical column: title, optional subtitle, an arbitrary
 * `content` slot for option rows, and a footer slot for restore /
 * manage-subscriptions / legal links.
 *
 * The scaffold deliberately ships no business logic — it only arranges
 * components. Hosts that need a different layout are expected to read the
 * same state flows and assemble their own UI.
 *
 * @since 0.1.0
 */
@Composable
public fun PaywallScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PurchaseKitDefaults.SectionSpacing),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        content()
        if (footer != null) {
            footer()
        }
    }
}
