package com.campusai

import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthRecordEvidence
import com.campusai.core.health.assembleHealthProvenance
import com.campusai.core.health.evaluateHealthPermissionOutcome
import com.campusai.core.health.healthFreshness
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
    fun missingHealthMetricsRemainUnknownInsteadOfBecomingZero() {
        val snapshot = HealthSnapshot(
            originPackages = setOf("com.mi.health"),
            period = HealthPeriod(0L, 10_000L, "today"),
            observedAt = 10_000L,
            lastSyncAt = 9_000L,
            freshness = HealthFreshness.FRESH,
            metrics = HealthMetrics(heartRateAverageBpm = 68),
            missingFields = setOf("steps", "sleep", "distance"),
            confidence = 1.0,
        )

        assertNull(snapshot.metrics.steps)
        assertNull(snapshot.metrics.sleepMinutes)
        assertEquals(setOf("steps", "sleep", "distance"), snapshot.missingFields)
    }
}
