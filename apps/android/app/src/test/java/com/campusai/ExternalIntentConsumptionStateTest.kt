package com.campusai

import android.os.Bundle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalIntentConsumptionStateTest {
    @Test fun `configuration restore keeps one shot payload consumed`() {
        val original = ExternalIntentConsumptionState(null).apply {
            sharedImageConsumed = true
            automationConversationConsumed = true
        }
        val saved = Bundle()

        original.save(saved)
        val restored = ExternalIntentConsumptionState(saved)

        assertTrue(restored.sharedImageConsumed)
        assertTrue(restored.automationConversationConsumed)
    }

    @Test fun `new intent resets consumption even when payload identity is unchanged`() {
        val state = ExternalIntentConsumptionState(null).apply {
            sharedImageConsumed = true
            automationConversationConsumed = true
        }

        state.resetForNewIntent()

        assertFalse(state.sharedImageConsumed)
        assertFalse(state.automationConversationConsumed)
    }
}
