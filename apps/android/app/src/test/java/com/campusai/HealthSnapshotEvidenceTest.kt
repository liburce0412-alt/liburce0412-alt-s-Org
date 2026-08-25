package com.campusai

import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthRecordEvidence
import com.campusai.core.health.BandLiveSnapshot
import com.campusai.core.health.BandLiveState
import com.campusai.core.health.assembleHealthProvenance
import com.campusai.core.health.evaluateHealthPermissionOutcome
import com.campusai.core.health.healthFreshness
import com.campusai.core.health.healthExportGap
import com.campusai.core.health.HealthMetrics
import com.campusai.core.health.HealthPeriod
import com.campusai.core.health.HealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthSnapshotEvidenceTest {
    @Test
    fun emptyAggregateDoesNotInventQueryEndAsLastSync() {
        val provenance = assembleHealthProvenance(emptySet(), emptyList())

        assertNull(provenance.lastSyncAt)
        assertEquals(emptySet<String>(), provenance.originPackages)
        assertEquals(HealthFreshness.UNKNOWN, healthFreshness(10_000L, provenance.lastSyncAt))
    }

    @Test
    fun lastSyncAndOriginsComeFromRecordMetadata() {
        val provenance = assembleHealthProvenance(
            aggregateOriginPackages = setOf("aggregate.writer"),
            recordEvidence = listOf(
                HealthRecordEvidence("sleep.writer", 4_000L),
                HealthRecordEvidence("heart.writer", 8_000L),
            ),
        )

        assertEquals(8_000L, provenance.lastSyncAt)
        assertEquals(setOf("aggregate.writer", "sleep.writer", "heart.writer"), provenance.originPackages)
    }

    @Test
    fun futureTimestampIsNotReportedFresh() {
        assertEquals(HealthFreshness.UNKNOWN, healthFreshness(1_000L, 70_001L))
    }

    @Test
    fun partialHealthPermissionGrantIsReportedAsCanceledOutcome() {
        val outcome = evaluateHealthPermissionOutcome(
            requested = setOf("steps", "heart"),
            granted = setOf("steps"),
        )

        assertEquals(setOf("steps"), outcome.granted)
        assertEquals(setOf("heart"), outcome.missing)
        assertEquals(false, outcome.allGranted)
    }

    @Test
    fun staleBandSnapshotStillCarriesDeterministicStatus() {
        val snapshot = BandLiveSnapshot(
            observedAt = 1L,
            connected = null,
            batteryPercent = null,
            charging = null,
            wearing = null,
            sleeping = null,
            heartRateBpm = null,
            stepDelta = null,
            capabilityBits = 0L,
            bridgeState = BandLiveState.UNAVAILABLE,
            statusMessage = "protocol unavailable",
        )

        assertEquals(false, snapshot.isFresh(now = 30_000L))
        assertEquals("protocol unavailable", snapshot.statusMessage)
        assertEquals(BandLiveState.UNAVAILABLE, snapshot.bridgeState)
    }

    @Test
    fun partialGadgetbridgeExportKeepsRawMissingValuesWhileAllowingZeroPresentation() {
        val snapshot = HealthSnapshot(
            originPackages = setOf("nodomain.freeyourgadget.gadgetbridge"),
            period = HealthPeriod(0L, 10_000L, "today"),
            observedAt = 10_000L,
            lastSyncAt = 9_000L,
            freshness = HealthFreshness.FRESH,
            metrics = HealthMetrics(heartRateAverageBpm = 68),
            missingFields = setOf("steps", "sleep", "distance"),
            confidence = 1.0,
        )

        val gap = healthExportGap(snapshot)

        assertEquals(setOf("steps", "sleep"), gap?.missingFields)
        assertEquals(true, gap?.message?.contains("步数、睡眠"))
        assertEquals(true, gap?.message?.contains("页面可按无记录显示 0"))
        assertNull(snapshot.metrics.steps)
        assertNull(snapshot.metrics.sleepMinutes)
    }

    @Test
    fun noExportGapIsClaimedWithoutGadgetbridgeEvidence() {
        val snapshot = HealthSnapshot(
            originPackages = setOf("com.mi.health"),
            period = HealthPeriod(0L, 10_000L, "today"),
            observedAt = 10_000L,
            lastSyncAt = 9_000L,
            freshness = HealthFreshness.FRESH,
            metrics = HealthMetrics(),
            missingFields = setOf("steps", "sleep"),
            confidence = 1.0,
        )

        assertNull(healthExportGap(snapshot))
    }
}
