package com.campusai

import com.campusai.features.ai.AiGenerationEpoch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGenerationEpochTest {
    @Test fun `cancelled generation cannot clear or overwrite its replacement`() {
        val epoch = AiGenerationEpoch()
        val oldToken = epoch.begin()
        epoch.invalidate()
        val newToken = epoch.begin()
        var activeGeneration = "new"

        if (epoch.isCurrent(oldToken)) activeGeneration = "old completion"

        assertFalse(epoch.isCurrent(oldToken))
        assertTrue(epoch.isCurrent(newToken))
        assertEquals("new", activeGeneration)
    }
}
