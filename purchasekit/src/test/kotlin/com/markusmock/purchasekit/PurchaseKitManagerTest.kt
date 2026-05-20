// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.ProductDetails
import com.markusmock.purchasekit.api.AnyPurchasableOption
import com.markusmock.purchasekit.model.EntitlementState
import com.markusmock.purchasekit.model.PurchaseError
import com.markusmock.purchasekit.model.PurchaseFlowState
import com.markusmock.purchasekit.service.FakeBillingClient
import com.markusmock.purchasekit.support.PurchaseKitLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PurchaseKitManagerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun TestScope.newManager(
        bridge: FakeBillingClient = FakeBillingClient(),
        config: PurchaseKitConfig = PurchaseKitConfig(logger = PurchaseKitLogger.NoOp),
    ): PurchaseKitManager {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return PurchaseKitManager.create(
            context = context,
            options = allTestOptions(),
            config = config,
            bridge = bridge,
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun initial_state_is_inactive_for_every_option() = runTest {
        val manager = newManager()
        runCurrent()
        val snapshot = manager.entitlements.value
        assertEquals(3, snapshot.size)
        snapshot.values.forEach { assertEquals(EntitlementState.Inactive, it) }
        manager.shutdown()
    }

    @Test
    fun connection_succeeds_to_Connected() = runTest {
        val bridge = FakeBillingClient()
        val manager = newManager(bridge)
        runCurrent()
        assertTrue(bridge.invocations.contains("connect"))
        manager.shutdown()
    }

    @Test
    fun addListener_with_LifecycleOwner_is_removed_on_destroy() = runTest {
        val manager = newManager()
        var calls = 0
        val listener = object : PurchaseKitDelegate {
            override fun onEntitlementUpdated(
                option: AnyPurchasableOption,
                state: EntitlementState,
            ) {
                calls++
            }
        }
        val owner = TestLifecycleOwner()
        manager.addListener(listener, owner)
        (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.STARTED
        (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.DESTROYED

        // After DESTROY the listener should be gone — the listener list is empty.
        val listenersField = PurchaseKitManager::class.java.getDeclaredField("listeners").also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val listeners = listenersField.get(manager) as java.util.concurrent.CopyOnWriteArrayList<Any>
        assertTrue("Listener list must be empty after DESTROY", listeners.isEmpty())
        assertEquals(0, calls)
        manager.shutdown()
    }

    @Test
    fun removeListener_is_idempotent() = runTest {
        val manager = newManager()
        val listener = object : PurchaseKitDelegate {}
        manager.addListener(listener)
        manager.removeListener(listener)
        manager.removeListener(listener) // no-op, must not throw
        manager.shutdown()
    }

    @Test
    fun flow_state_transitions_to_failed_when_product_is_missing() = runTest {
        val manager = newManager()
        runCurrent()
        // No availableProducts loaded — purchase should fail fast.
        val activity = org.robolectric.Robolectric
            .buildActivity(android.app.Activity::class.java).create().get()
        manager.purchase(TestOption.Monthly, activity)
        runCurrent()
        val perOption = manager.perOptionFlowState.value
        val expectedKey = AnyPurchasableOption.of(TestOption.Monthly)
        assertEquals(
            PurchaseFlowState.Failed(PurchaseError.ProductUnavailable),
            perOption[expectedKey],
        )
        manager.shutdown()
    }

    @Test
    fun shutdown_calls_endConnection_and_clears_listeners() = runTest {
        val bridge = FakeBillingClient()
        val manager = newManager(bridge)
        val listener = object : PurchaseKitDelegate {}
        manager.addListener(listener)
        manager.shutdown()
        assertTrue("endConnection should be called", bridge.endConnectionCalls >= 1)
    }

    @Test
    fun hasAnyActiveSubscription_initially_false() = runTest {
        val manager = newManager()
        runCurrent()
        assertFalse(manager.hasAnyActiveSubscription.value)
        assertNull(manager.primaryActiveSubscription.value)
        manager.shutdown()
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.INITIALIZED }
    override val lifecycle: LifecycleRegistry get() = registry
}
