// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.markusmock.purchasekit.compose.theme.PurchaseKitDefaults
import com.markusmock.purchasekit.model.EntitlementState
import com.markusmock.purchasekit.model.PurchaseFlowState

/**
 * Material 3 call-to-action button that renders one of three states based on
 * the option's [EntitlementState] and the manager's [PurchaseFlowState]:
 *
 * - **Active**: shows "Active" and is disabled (entitlement already granted).
 * - **In flight**: shows a spinner (purchase or restore is running).
 * - **Idle**: shows [labelIdle] and is enabled.
 *
 * Hosts that need richer copy or custom layouts read the same state flows
 * directly and build their own button.
 *
 * Accessibility: the button announces "Purchase {labelIdle}" / "Active" /
 * "Loading" via `contentDescription`. The 48dp minimum touch target from
 * [PurchaseKitDefaults.MinTouchTarget] is enforced.
 *
 * @param entitlement       Latest [EntitlementState] for the target option.
 * @param flowState         Latest [PurchaseFlowState] from the manager.
 * @param onPurchase        Called when the user taps the idle button.
 * @param labelIdle         Idle-state label (host-localised).
 * @param labelActive       Active-state label.
 * @param modifier          Compose modifier chain.
 * @param contentPadding    Inner padding.
 *
 * @since 0.1.0
 */
@Composable
public fun PurchaseButton(
    entitlement: EntitlementState,
    flowState: PurchaseFlowState,
    onPurchase: () -> Unit,
    labelIdle: String,
    labelActive: String = "Active",
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PurchaseKitDefaults.ButtonPadding,
) {
    val isActive = entitlement.isActive
    val isInFlight = flowState is PurchaseFlowState.Purchasing || flowState is PurchaseFlowState.Pending
    val description = when {
        isActive -> labelActive
        isInFlight -> "Loading"
        else -> "Purchase $labelIdle"
    }
    Button(
        onClick = onPurchase,
        enabled = !isActive && !isInFlight,
        modifier = modifier
            .defaultMinSize(minHeight = PurchaseKitDefaults.MinTouchTarget)
            .semantics { contentDescription = description },
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Box(modifier = Modifier.padding(end = 8.dp)) {
            if (isInFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(if (isActive) labelActive else labelIdle)
    }
}
