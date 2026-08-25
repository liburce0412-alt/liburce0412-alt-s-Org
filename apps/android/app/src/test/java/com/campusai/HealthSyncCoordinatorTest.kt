package com.campusai

import com.campusai.core.health.BandHistorySyncState
import com.campusai.core.health.BandLiveGateway
import com.campusai.core.health.BandLiveSnapshot
import com.campusai.core.health.BandLiveState
import com.campusai.core.health.HealthAvailability
import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthGateway
import com.campusai.core.health.HealthMetrics
import com.campusai.core.health.HealthPeriod
import com.campusai.core.health.HealthSnapshot
import com.campusai.core.health.HealthSyncCoordinator
import com.campusai.core.health.HealthSyncReason
import com.campusai.core.health.HealthSyncStage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSyncCoordinatorTest {
    @Test
    fun `user sync waits for bridge completion then refreshed health evidence`() = runTest {
        var elapsed = 1_000L
        val health = FakeHealthGateway(listOf(snapshot(lastSync = 10L), snapshot(lastSync = 10L), snapshot(lastSync = 20L)))
        val band = FakeBandGateway(
            listOf(
                band(BandHistorySyncState.CONNECTING),
                band(BandHistorySyncState.REQUESTED),
                band(BandHistorySyncState.FINISHED),
            ),
        )
        val stages = mutableListOf<HealthSyncStage>()
        val coordinator = HealthSyncCoordinator(health, band, { elapsed }) { millis -> elapsed += millis }

        val result = coordinator.synchronize(PERIOD, HealthSyncReason.USER) { stage, _ -> stages += stage }

        assertEquals(1, band.triggerCount)
        assertEquals(HealthSyncStage.COMPLETE, result.stage)
        assertEquals(20L, result.health?.lastSyncAt)
        assertTrue(HealthSyncStage.CONNECTING in stages)
        assertTrue(HealthSyncStage.PULLING_HISTORY in stages)
        assertTrue(HealthSyncStage.IMPORTING_HEALTH_CONNECT in stages)
    }

    @Test
    fun `agent cooldown reads existing health without starting another connection`() = runTest {
        var elapsed = 10_000L
        val health = FakeHealthGateway(List(8) { snapshot(lastSync = 20L) })
        val band = FakeBandGateway(List(8) { band(BandHistorySyncState.FINISHED) })
        val coordinator = HealthSyncCoordinator(health, band, { elapsed }) { millis -> elapsed += millis }

        val first = coordinator.synchronize(PERIOD, HealthSyncReason.AGENT)
        val second = coordinator.synchronize(PERIOD, HealthSyncReason.AGENT)

        assertEquals(HealthSyncStage.COMPLETE, first.stage)
        assertEquals(HealthSyncStage.USING_CACHED_DATA, second.stage)
        assertEquals(1, band.triggerCount)
    }

    @Test
    fun `timeout keeps cached data and never claims completion`() = runTest {
        var elapsed = 1_000L
        val health = FakeHealthGateway(List(100) { snapshot(lastSync = 10L) })
        val band = FakeBandGateway(List(100) { band(BandHistorySyncState.CONNECTING) })
        val coordinator = HealthSyncCoordinator(health, band, { elapsed }) { millis -> elapsed += millis }

        val result = coordinator.synchronize(PERIOD, HealthSyncReason.AGENT)

        assertEquals(HealthSyncStage.USING_CACHED_DATA, result.stage)
        assertEquals(10L, result.health?.lastSyncAt)
        assertTrue(result.message.contains("后台"))
    }

    private class FakeHealthGateway(private val values: List<HealthSnapshot>) : HealthGateway {
        private var index = 0
        override val readPermissions: Set<String> = setOf("health")
        override fun availability(): HealthAvailability = HealthAvailability.Available
        override suspend fun grantedPermissions(): Set<String> = readPermissions
        override suspend fun snapshot(period: HealthPeriod): Result<HealthSnapshot> =
            Result.success(values[index.coerceAtMost(values.lastIndex)].also { index++ })
    }

    private class FakeBandGateway(private val values: List<BandLiveSnapshot>) : BandLiveGateway {
        private var index = 0
        var triggerCount = 0
        override suspend fun snapshot(): Result<BandLiveSnapshot> =
            Result.success(values[index.coerceAtMost(values.lastIndex)].also { index++ })
        override fun startSession(): Result<Unit> = Result.success(Unit)
        override fun stopSession(): Result<Unit> = Result.success(Unit)
        override fun triggerHistorySync(): Result<Unit> {
            triggerCount++
            return Result.success(Unit)
        }
    }

    companion object {
        private val PERIOD = HealthPeriod(0L, 100L, "today")

        private fun snapshot(lastSync: Long) = HealthSnapshot(
            originPackages = setOf("nodomain.freeyourgadget.gadgetbridge.nightly"),
            period = PERIOD,
            observedAt = Long.MAX_VALUE,
            lastSyncAt = lastSync,
            freshness = HealthFreshness.FRESH,
            metrics = HealthMetrics(steps = lastSync),
            missingFields = emptySet(),
            confidence = 1.0,
        )

        private fun band(state: BandHistorySyncState) = BandLiveSnapshot(
            observedAt = Long.MAX_VALUE,
            connected = state == BandHistorySyncState.FINISHED,
            batteryPercent = null,
            charging = null,
            wearing = null,
            sleeping = null,
            heartRateBpm = null,
            stepDelta = null,
            capabilityBits = 0L,
            bridgeState = BandLiveState.LISTENING,
            historySyncState = state,
        )
    }
}
