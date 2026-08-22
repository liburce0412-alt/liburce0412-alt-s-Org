package com.campusai

import com.campusai.features.community.allowedOrderTransitions
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderWorkflowTest {
    @Test
    fun `buyer can only advance buyer-owned order steps`() {
        assertEquals(listOf("paid", "cancelled"), allowedOrderTransitions("pending_payment", isBuyer = true))
        assertEquals(listOf("disputed"), allowedOrderTransitions("paid", isBuyer = true))
        assertEquals(listOf("completed", "disputed"), allowedOrderTransitions("meeting", isBuyer = true))
    }

    @Test
    fun `seller can prepare handoff but cannot complete the order`() {
        assertEquals(listOf("cancelled"), allowedOrderTransitions("pending_payment", isBuyer = false))
        assertEquals(listOf("meeting", "disputed"), allowedOrderTransitions("paid", isBuyer = false))
        assertEquals(listOf("disputed"), allowedOrderTransitions("meeting", isBuyer = false))
        assertEquals(emptyList<String>(), allowedOrderTransitions("completed", isBuyer = false))
    }
}
