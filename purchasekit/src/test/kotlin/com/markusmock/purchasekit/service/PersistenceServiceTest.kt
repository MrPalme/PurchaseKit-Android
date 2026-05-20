// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import androidx.test.core.app.ApplicationProvider
import com.markusmock.purchasekit.model.EntitlementState
import com.markusmock.purchasekit.support.PurchaseKitLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PersistenceServiceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val service = PersistenceService(
        context = context,
        prefsName = "purchasekit_test_prefs",
        logger = PurchaseKitLogger.NoOp,
        io = Dispatchers.Unconfined,
    )

    @Test
    fun empty_cache_reads_as_empty_map() = runTest {
        service.clear()
        assertEquals(emptyMap<String, EntitlementState>(), service.snapshot())
    }

    @Test
    fun put_then_snapshot_returns_the_entry() = runTest {
        service.clear()
        service.put("app.pro.monthly", EntitlementState.SubscriptionActive(123L, "tx-1"))
        val snap = service.snapshot()
        assertEquals(EntitlementState.SubscriptionActive(123L, "tx-1"), snap["app.pro.monthly"])
    }

    @Test
    fun replaceAll_overwrites_prior_entries() = runTest {
        service.clear()
        service.put("a", EntitlementState.NonConsumable("tx-a"))
        service.replaceAll(mapOf("b" to EntitlementState.NonConsumable("tx-b")))
        val snap = service.snapshot()
        assertEquals(1, snap.size)
        assertEquals(EntitlementState.NonConsumable("tx-b"), snap["b"])
    }

    @Test
    fun survives_a_fresh_service_instance_pointing_at_the_same_prefs() = runTest {
        service.clear()
        service.put("persisted", EntitlementState.NonConsumable("tx-keep"))

        val fresh = PersistenceService(
            context = context,
            prefsName = "purchasekit_test_prefs",
            logger = PurchaseKitLogger.NoOp,
            io = Dispatchers.Unconfined,
        )
        val snap = fresh.snapshot()
        assertEquals(EntitlementState.NonConsumable("tx-keep"), snap["persisted"])
    }
}
