package com.campusai.core.automation

import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthMetricKey
import com.campusai.core.health.HealthMetricProvenance
import com.campusai.core.health.HealthMetricStatus
import com.campusai.core.health.HealthMetricTimeSeries
import com.campusai.core.health.HealthMetricUnit
import com.campusai.core.health.HealthMetricValue
import com.campusai.core.health.HealthMetrics
import com.campusai.core.health.HealthPeriod
import com.campusai.core.health.HealthSnapshot
import com.campusai.core.health.HealthTimeSeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthTaskDataTest {
    @Test
    fun revision_ignores_refresh_timestamps_and_time_series() {
        val first = snapshot(observedAt = 100L, lastSyncAt = 90L, seriesValue = 3.0)
        val second = snapshot(observedAt = 900L, lastSyncAt = 800L, seriesValue = 99.0)

        assertEquals(
            HealthRevision.from("2026-08-28", first),
            HealthRevision.from("2026-08-28", second),
        )
    }

    @Test
    fun revision_changes_for_typed_aggregate_or_workout_revision_fields() {
        val baseline = HealthRevision.from("2026-08-28", snapshot())
        val changedValue = HealthRevision.from("2026-08-28", snapshot(steps = 975.0))
        val changedWorkoutSourceCount = HealthRevision.from(
            "2026-08-28",
            snapshot(workoutCount = 2.0, workoutSourceCount = 2),
        )
        val changedDate = HealthRevision.from("2026-08-29", snapshot())
        val changedWorkoutRecord = HealthRevision.from("2026-08-28", snapshot(), "a".repeat(64))

        assertNotEquals(baseline, changedValue)
        assertNotEquals(baseline, changedWorkoutSourceCount)
        assertNotEquals(baseline, changedDate)
        assertNotEquals(baseline, changedWorkoutRecord)
    }

    @Test
    fun cloud_summary_allows_only_available_daily_values() {
        val metrics = snapshot().metricValues.toMutableMap().apply {
            this[HealthMetricKey.HEART_RATE_AVERAGE_BPM] = metric(
                key = "heart_rate",
                value = 66.0,
                unit = HealthMetricUnit.BEATS_PER_MINUTE,
                status = HealthMetricStatus.ERROR,
            )
        }
        val summary = HealthCloudObservation(
            "2026-08-28",
            snapshot().copy(metricValues = metrics),
        ).allowedCloudSummary()

        assertEquals(974L, summary.steps)
        assertEquals(1L, summary.workoutCount)
        assertNull(summary.averageHeartRateBpm)
    }

    @Test
    fun message_parser_accepts_short_json_only() {
        val result = AutoMessageBatch.parse("""{"messages":["今天走得不少呀","睡眠数据也到了"]}""")

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(2, result.getOrThrow().messages.size)
    }

    @Test
    fun message_parser_rejects_prefix_markdown_and_wrong_count() {
        assertFalse(AutoMessageBatch.parse("""{"messages":["健康提醒：多走走","今天继续加油"]}""").isSuccess)
        assertFalse(AutoMessageBatch.parse("""{"messages":["## 今日状态","今天继续加油"]}""").isSuccess)
        assertFalse(AutoMessageBatch.parse("""{"messages":["只有一条消息"]}""").isSuccess)
        assertFalse(AutoMessageBatch.parse("""```json {"messages":["今天走得不少","继续保持呀"]} ```""").isSuccess)
        assertFalse(AutoMessageBatch.parse("""{"messages":["gemini says hello","今天继续加油"]}""").isSuccess)
        assertFalse(AutoMessageBatch.parse("""{"messages":["cloud data ready","今天继续加油"]}""").isSuccess)
    }

    private fun snapshot(
        steps: Double = 974.0,
        workoutCount: Double = 1.0,
        workoutSourceCount: Int = 1,
        observedAt: Long = 1_000L,
        lastSyncAt: Long = 900L,
        seriesValue: Double = 16.0,
    ): HealthSnapshot {
        val metrics = linkedMapOf(
            HealthMetricKey.STEPS to metric("steps", steps, HealthMetricUnit.COUNT),
            HealthMetricKey.WORKOUT_COUNT to metric(
                "sport_records",
                workoutCount,
                HealthMetricUnit.COUNT,
                sourceCount = workoutSourceCount,
            ),
        )
        return HealthSnapshot(
            originPackages = setOf("mi_fitness_cloud_cn"),
            period = HealthPeriod(1L, 86_400_000L, "today"),
            observedAt = observedAt,
            lastSyncAt = lastSyncAt,
            freshness = HealthFreshness.LIVE,
            metrics = HealthMetrics(steps = steps.toLong(), workoutCount = workoutCount.toInt()),
            missingFields = emptySet(),
            confidence = .95,
            metricValues = metrics,
            metricTimeSeries = mapOf(
                HealthMetricKey.STEPS to HealthMetricTimeSeries(
                    unit = HealthMetricUnit.COUNT,
                    status = HealthMetricStatus.AVAILABLE,
                    points = listOf(HealthTimeSeriesPoint(10L, seriesValue)),
                    provenance = HealthMetricProvenance(
                        sourceId = "mi_fitness_cloud_cn",
                        endpoint = "/trend",
                        aggregation = "vendor_time_series",
                        vendorKey = "steps",
                        sourceCount = 1,
                    ),
                ),
            ),
        )
    }

    private fun metric(
        key: String,
        value: Double,
        unit: HealthMetricUnit,
        status: HealthMetricStatus = HealthMetricStatus.AVAILABLE,
        sourceCount: Int = 1,
    ) = HealthMetricValue(
        value = value,
        unit = unit,
        status = status,
        provenance = HealthMetricProvenance(
            sourceId = "mi_fitness_cloud_cn",
            endpoint = "/aggregate",
            aggregation = "vendor_daily_aggregate",
            vendorKey = key,
            sourceCount = sourceCount,
        ),
    )
}
