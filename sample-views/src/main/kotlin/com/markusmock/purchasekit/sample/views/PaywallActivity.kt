// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.sample.views

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.markusmock.purchasekit.PurchaseKitManager
import com.markusmock.purchasekit.sample.views.databinding.ActivityPaywallBinding
import kotlinx.coroutines.launch

/**
 * View/XML paywall demonstrating PurchaseKit consumption *without* Compose.
 *
 * Consumes [PurchaseKitManager.flowState] via `repeatOnLifecycle(STARTED)` and
 * surfaces it onto a `TextView`. Buttons map directly to manager calls.
 */
class PaywallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaywallBinding

    private val manager: PurchaseKitManager
        get() = (application as SampleApplication).purchaseKit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buyMonthly.setOnClickListener { manager.purchase(AppOption.Monthly, this) }
        binding.buyYearly.setOnClickListener { manager.purchase(AppOption.Yearly, this) }
        binding.buyLifetime.setOnClickListener { manager.purchase(AppOption.Lifetime, this) }
        binding.restore.setOnClickListener { manager.restorePurchases() }
        binding.manage.setOnClickListener { PurchaseKitManager.openSubscriptionManagement(this) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    manager.flowState.collect { state -> binding.flowStateLabel.text = state.toString() }
                }
                launch {
                    manager.entitlements.collect { entitlements ->
                        binding.buyLifetime.isEnabled =
                            entitlements.entries.none { it.key == com.markusmock.purchasekit.api.AnyPurchasableOption.of(AppOption.Lifetime) && it.value.isActive }
                    }
                }
            }
        }
    }
}
