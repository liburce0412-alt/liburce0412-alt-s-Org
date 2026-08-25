package com.campusai

import com.campusai.core.localai.LocalGenerationState
import com.campusai.core.localai.LocalCancellationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalGenerationStateTest {
    @Test fun `closing an old flow cannot cancel the pointer attached to a new flow`() {
        val cancelledPointers = mutableListOf<Long>()
        val old = LocalGenerationState(cancelledPointers::add)
        val current = LocalGenerationState(cancelledPointers::add)

        assertTrue(old.attach(11))
        old.detach()
        assertTrue(current.attach(22))
        old.cancel()

        assertTrue(cancelledPointers.isEmpty())
        current.cancel()
        assertEquals(listOf(22L), cancelledPointers)
    }

    @Test fun `cancellation racing ahead of pointer attachment cancels on attach`() {
        val cancelledPointers = mutableListOf<Long>()
        val generation = LocalGenerationState(cancelledPointers::add)

        generation.cancel()

        assertFalse(generation.attach(33))
        assertEquals(listOf(33L), cancelledPointers)
    }

    @Test fun `memory pressure cancellation reports one recoverable error without changing model`() {
        val generation = LocalGenerationState { }
        val error = LocalCancellationError(
            code = "local_memory_pressure",
            message = "release and retry",
        )

        generation.cancel(error)

        assertTrue(generation.cancelled.get())
        assertEquals(error, generation.takeCancellationError())
        assertNull(generation.takeCancellationError())
    }

    @Test fun `manual cancellation stays silent`() {
        val generation = LocalGenerationState { }

        generation.cancel()

        assertNull(generation.takeCancellationError())
    }
}
