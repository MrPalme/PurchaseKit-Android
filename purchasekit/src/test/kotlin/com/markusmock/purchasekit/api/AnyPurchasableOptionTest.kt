// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnyPurchasableOptionTest {

    private enum class HostOption(
        override val productId: String,
        override val purchaseType: PurchaseType,
        override val title: String,
        override val sortOrder: Int,
    ) : PurchasableOption {
        Monthly("app.pro.monthly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Monthly", 0),
        Yearly("app.pro.yearly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Yearly", 1),
    }

    @Test
    fun `snapshots all properties of a host option`() {
        val wrapped = AnyPurchasableOption.of(HostOption.Monthly)
        assertEquals("app.pro.monthly", wrapped.productId)
        assertEquals(PurchaseType.AUTO_RENEWING_SUBSCRIPTION, wrapped.purchaseType)
        assertEquals("Monthly", wrapped.title)
        assertEquals(0, wrapped.sortOrder)
    }

    @Test
    fun `equality is defined on productId across host implementations`() {
        val fromEnum = AnyPurchasableOption.of(HostOption.Yearly)
        val fromCustom = AnyPurchasableOption(
            id = "ignored",
            productId = "app.pro.yearly",
            purchaseType = PurchaseType.AUTO_RENEWING_SUBSCRIPTION,
            title = "Different title",
        )
        assertEquals(fromEnum, fromCustom)
        assertEquals(fromEnum.hashCode(), fromCustom.hashCode())
    }

    @Test
    fun `different productIds are not equal`() {
        assertNotEquals(
            AnyPurchasableOption.of(HostOption.Monthly),
            AnyPurchasableOption.of(HostOption.Yearly),
        )
    }

    @Test
    fun `of is idempotent on an already-wrapped value`() {
        val once = AnyPurchasableOption.of(HostOption.Monthly)
        val twice = AnyPurchasableOption.of(once)
        assertSame(once, twice)
    }

    @Test
    fun `equality with a raw PurchasableOption matches productId`() {
        val wrapped = AnyPurchasableOption.of(HostOption.Monthly)
        // Equality is one-way (data class hashCode override on wrapped),
        // but lookup in a Map<AnyPurchasableOption, …> via wrapped key is what matters.
        assertTrue(wrapped == AnyPurchasableOption.of(HostOption.Monthly))
    }
}
