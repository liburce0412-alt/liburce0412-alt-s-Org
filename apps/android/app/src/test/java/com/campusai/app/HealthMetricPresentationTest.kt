package com.campusai.app

import com.campusai.core.health.BandHistorySyncState
import com.campusai.core.health.BandLiveSnapshot
import com.campusai.core.health.HealthAvailability
import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthMetrics
import com.campusai.core.health.HealthPeriod
import com.campusai.core.health.HealthSnapshot
import com.campusai.features.ai.CaesarHealthUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthMetricPresentationTest {
    @Test
    fun successfulCurrentDayReadDisplaysMissingStepsAndSleepAsZero() {
        val state = state(snapshot = snapshot(origins = setOf("nodomain.freeyourgadget.gadgetbridge")))

        assertEquals(0L, displayedDailySteps(state))
        assertEquals(0L, displayedDailySleepMinutes(state))
    }

    @Test
    fun actualRecordsAlwaysWinOverZeroPresentation() {
        val state = state(
            snapshot = snapshot(
                origins = setOf("nodomain.freeyourgadget.gadgetbridge"),
                metrics = HealthMetrics(steps = 1_234L, sleepMinutes = 420L),
                missing = emptySet(),
            ),
        )

        assertEquals(1_234L, displayedDailySteps(state))
        assertEquals(420L, displayedDailySleepMinutes(state))
    }

    @Test
    fun missingValuesStayUnknownWithoutSuccessfulCurrentDayEvidence() {
        val unavailable = state(snapshot = snapshot(origins = emptySet()), availability = HealthAvailability.Unsupported)
        val failed = state(snapshot = snapshot(origins = setOf("writer")), healthError = "读取失败")
        val otherWindow = state(snapshot = snapshot(origins = setOf("writer"), periodKey = "week"))
        val noSource = state(snapshot = snapshot(origins = emptySet()))

        listOf(unavailable, failed, otherWindow, noSource).forEach { candidate ->
            assertNull(displayedDailySteps(candidate))
            assertNull(displayedDailySleepMinutes(candidate))
        }
    }

    @Test
    fun finishedBandSyncIsSufficientSourceEvidenceForAnEmptyDay() {
        val state = state(
            snapshot = snapshot(origins = emptySet()),
            band = BandLiveSnapshot(
                observedAt = 1L,
                connected = false,
                batteryPercent = null,
                charging = null,
                wearing = null,
                sleeping = null,
                heartRateBpm = null,
                stepDelta = null,
                capabilityBits = 0L,
                historySyncState = BandHistorySyncState.FINISHED,
            ),
        )

        assertEquals(0L, displayedDailySteps(state))
        assertEquals(0L, displayedDailySleepMinutes(state))
    }

    private fun state(
        snapshot: HealthSnapshot?,
        availability: HealthAvailability = HealthAvailability.Available,
        healthError: String? = null,
        band: BandLiveSnapshot? = null,
    ) = CaesarHealthUiState(
        availability = availability,
        snapshot = snapshot,
        healthError = healthError,
        band = band,
    )

    private fun snapshot(
        origins: Set<String>,
        metrics: HealthMetrics = HealthMetrics(),
        missing: Set<String> = setOf("steps", "sleep"),
        periodKey: String = "today",
    ) = HealthSnapshot(
        originPackages = origins,
        period = HealthPeriod(0L, 10_000L, periodKey),
        observedAt = 10_000L,
        lastSyncAt = origins.takeIf { it.isNotEmpty() }?.let { 9_000L },
        freshness = HealthFreshness.FRESH,
        metrics = metrics,
        missingFields = missing,
        confidence = if (origins.isEmpty()) 0.0 else 1.0,
    )
}
