package com.campusai.features.ai

import com.campusai.caesar.bandcontract.BandBridgeContract
import com.campusai.core.health.BandLiveSnapshot
import com.campusai.core.health.BandLiveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BandCardCapabilityTest {
    @Test
    fun liveValuesRequireBothCapabilityAndValue() {
        val visible = visibleBandMetrics(
            snapshot(
                capabilityBits = BandBridgeContract.Capability.REALTIME_HEART_RATE or
                    BandBridgeContract.Capability.REALTIME_STEPS or
                    BandBridgeContract.Capability.BATTERY,
                heartRateBpm = 72,
                stepDelta = null,
                batteryPercent = 81,
            ),
        )

        assertEquals(72, visible.heartRateBpm)
        assertNull(visible.stepDelta)
        assertEquals(81, visible.batteryPercent)
    }

    @Test
    fun valuesWithoutCapabilityStayUnavailable() {
        val visible = visibleBandMetrics(
            snapshot(
                capabilityBits = BandBridgeContract.Capability.GADGETBRIDGE_INSTALLED,
                heartRateBpm = 72,
                stepDelta = 120,
                batteryPercent = 81,
            ),
        )

        assertNull(visible.heartRateBpm)
        assertNull(visible.stepDelta)
        assertNull(visible.batteryPercent)
    }

    private fun snapshot(
        capabilityBits: Long,
        heartRateBpm: Int?,
        stepDelta: Long?,
        batteryPercent: Int?,
    ) = BandLiveSnapshot(
        observedAt = 1L,
        connected = null,
        batteryPercent = batteryPercent,
        charging = null,
        wearing = null,
        sleeping = null,
        heartRateBpm = heartRateBpm,
        stepDelta = stepDelta,
        capabilityBits = capabilityBits,
        bridgeState = BandLiveState.IDLE,
    )
}
