// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.sample.compose

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markusmock.purchasekit.PurchaseKitManager
import com.markusmock.purchasekit.api.AnyPurchasableOption
import com.markusmock.purchasekit.compose.LegalLinksRow
import com.markusmock.purchasekit.compose.ManageSubscriptionsButton
import com.markusmock.purchasekit.compose.PaywallScaffold
import com.markusmock.purchasekit.compose.PurchaseButton
import com.markusmock.purchasekit.compose.RestorePurchasesButton

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = (application as SampleApplication).purchaseKit
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    PaywallRoot(manager = manager, activity = this)
                }
            }
        }
    }
}

@Composable
private fun PaywallRoot(manager: PurchaseKitManager, activity: Activity) {
    val entitlements by manager.entitlements.collectAsStateWithLifecycle()
    val flowState by manager.flowState.collectAsStateWithLifecycle()

    PaywallScaffold(
        title = "Unlock Pro",
        subtitle = "Sample app for PurchaseKit-Android",
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RestorePurchasesButton(onRestore = manager::restorePurchases)
                ManageSubscriptionsButton()
                LegalLinksRow(
                    termsUrl = "https://example.com/terms",
                    privacyUrl = "https://example.com/privacy",
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AppOption.entries.forEach { option ->
                val key = AnyPurchasableOption.of(option)
                val state = entitlements[key] ?: com.markusmock.purchasekit.model.EntitlementState.Inactive
                PurchaseButton(
                    entitlement = state,
                    flowState = flowState,
                    onPurchase = { manager.purchase(option, activity) },
                    labelIdle = option.title,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(text = "Flow: $flowState", style = MaterialTheme.typography.bodySmall)
        }
    }
}
