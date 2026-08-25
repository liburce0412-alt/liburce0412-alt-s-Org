package com.campusai.caesar.bandbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GadgetbridgePackageSelectionTest {
    @Test
    fun nightlyCompatibilityBuildWinsWhenOfficialIsAlsoInstalled() {
        val installed = setOf(
            "nodomain.freeyourgadget.gadgetbridge",
            "nodomain.freeyourgadget.gadgetbridge.nightly",
        )

        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.nightly",
            preferredGadgetbridgePackage(installed::contains),
        )
    }

    @Test
    fun officialPackageRemainsTheFallback() {
        val installed = setOf("nodomain.freeyourgadget.gadgetbridge")

        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge",
            preferredGadgetbridgePackage(installed::contains),
        )
    }

    @Test
    fun missingSupportedPackageReturnsNull() {
        assertNull(preferredGadgetbridgePackage(emptySet<String>()::contains))
    }
}
