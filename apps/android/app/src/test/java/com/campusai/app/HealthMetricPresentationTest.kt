package com.campusai.app

import com.campusai.core.health.HealthAvailability
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
import com.campusai.features.ai.CaesarHealthUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthMetricPresentationTest {
    @Test
    fun successfulCurrentDayReadKeepsMissingStepsAndSleepUnknown() {
        val state = state(snapshot = snapshot(origins = setOf("com.mi.health")))

        assertNull(displayedDailySteps(state))
        assertNull(displayedDailySleepMinutes(state))
    }

    @Test
    fun actualRecordsAlwaysWinOverZeroPresentation() {
        val state = state(
            snapshot = snapshot(
                origins = setOf("com.mi.health"),
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
    fun detailShowsOnlyMetricsWithValuesAndCollapsesErrorsIntoOneNotice() {
        val state = state(
            snapshot = snapshot(
                origins = setOf("mi_fitness_cloud_cn"),
                metricValues = mapOf(
                    HealthMetricKey.STEPS to metric(974.0, HealthMetricStatus.AVAILABLE),
                    HealthMetricKey.SLEEP_MINUTES to metric(null, HealthMetricStatus.EMPTY),
                    HealthMetricKey.HEART_RATE_AVERAGE_BPM to metric(72.0, HealthMetricStatus.PARTIAL),
                    HealthMetricKey.RESTING_HEART_RATE_BPM to metric(null, HealthMetricStatus.PARTIAL),
                    HealthMetricKey.STRESS_AVERAGE to metric(null, HealthMetricStatus.ERROR),
                ),
            ),
        )

        assertEquals(listOf("今日步数", "平均心率"), displayedHealthMetricLabels(state))
        assertEquals("部分健康数据暂未同步，请稍后重试。", healthMetricIssueNotice(state))
    }

    @Test
    fun availableOrPartialStepTrendWithPointsIsDisplayableWithoutChangingDailySteps() {
        val series = HealthMetricTimeSeries(
            unit = HealthMetricUnit.COUNT,
            status = HealthMetricStatus.PARTIAL,
            points = listOf(HealthTimeSeriesPoint(1_000L, 16.0)),
            provenance = provenance(),
            reasonCode = "cursor_repeated",
        )
        val state = state(
            snapshot = snapshot(
                origins = setOf("mi_fitness_cloud_cn"),
                metrics = HealthMetrics(steps = 974L),
                metricTimeSeries = mapOf(HealthMetricKey.STEPS to series),
            ),
        )

        assertEquals(974L, displayedDailySteps(state))
        assertEquals(series, displayedStepSeries(state))
    }

    private fun state(
        snapshot: HealthSnapshot?,
        availability: HealthAvailability = HealthAvailability.Available,
        healthError: String? = null,
    ) = CaesarHealthUiState(
        availability = availability,
        snapshot = snapshot,
        healthError = healthError,
    )

    private fun snapshot(
        origins: Set<String>,
        metrics: HealthMetrics = HealthMetrics(),
        missing: Set<String> = setOf("steps", "sleep"),
        periodKey: String = "today",
        metricValues: Map<HealthMetricKey, HealthMetricValue> = emptyMap(),
        metricTimeSeries: Map<HealthMetricKey, HealthMetricTimeSeries> = emptyMap(),
    ) = HealthSnapshot(
        originPackages = origins,
        period = HealthPeriod(0L, 10_000L, periodKey),
        observedAt = 10_000L,
        lastSyncAt = origins.takeIf { it.isNotEmpty() }?.let { 9_000L },
        freshness = HealthFreshness.FRESH,
        metrics = metrics,
        missingFields = missing,
        confidence = if (origins.isEmpty()) 0.0 else 1.0,
        metricValues = metricValues,
        metricTimeSeries = metricTimeSeries,
    )

    private fun metric(value: Double?, status: HealthMetricStatus) = HealthMetricValue(
        value = value,
        unit = HealthMetricUnit.COUNT,
        status = status,
        provenance = provenance(),
        reasonCode = if (status == HealthMetricStatus.AVAILABLE) null else "synthetic_status",
    )

    private fun provenance() = HealthMetricProvenance(
        sourceId = "mi_fitness_cloud_cn",
        endpoint = "/synthetic/read-only",
        aggregation = "synthetic",
        vendorKey = "steps",
    )
}
