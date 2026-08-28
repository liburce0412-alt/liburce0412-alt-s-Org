package com.campusai

import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.spectraColorScheme
import com.campusai.core.model.SpectraEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpectraEnvironmentCompatibilityTest {
    @Test
    fun legacyEnvironmentNamesAndOrdinalsRemainStable() {
        assertEquals(
            listOf("ORIGINAL", "OCEAN", "ULTRAVIOLET", "EMBER"),
            SpectraEnvironment.entries.take(4).map { it.name },
        )
        assertEquals(4, SpectraEnvironment.AURORA.ordinal)
    }

    @Test
    fun auroraProvidesIndependentReadableLightAndDarkAccents() {
        val light = spectraColorScheme(dark = false, environment = SpectraEnvironment.AURORA)
        val dark = spectraColorScheme(dark = true, environment = SpectraEnvironment.AURORA)

        assertEquals(SpectraColors.Aurora, light.primary)
        assertEquals(SpectraColors.AuroraLight, dark.primary)
        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.primary, light.background)
        assertNotEquals(dark.primary, dark.background)
    }
}
