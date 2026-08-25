package com.campusai.caesar.bandcontract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BandBridgeContractTest {
    @Test
    fun defaultSnapshotNeverInventsLiveMeasurements() {
        val snapshot = BandBridgeSnapshot(observedAt = 123L)

        assertEquals(BridgeState.UNAVAILABLE, snapshot.bridgeState)
        assertEquals(0L, snapshot.capabilityBits)
        assertEquals(null, snapshot.connected)
        assertEquals(null, snapshot.heartRateBpm)
        assertFalse(snapshot.capabilityBits and BandBridgeContract.Capability.REALTIME_HEART_RATE != 0L)
    }
}
