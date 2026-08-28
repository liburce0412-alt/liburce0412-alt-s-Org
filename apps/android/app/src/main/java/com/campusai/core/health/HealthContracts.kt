package com.campusai.core.health

data class HealthPeriod(val startEpochMillis: Long, val endEpochMillis: Long, val key: String)

enum class HealthFreshness { LIVE, FRESH, STALE, UNKNOWN }

enum class HealthMetricStatus { AVAILABLE, EMPTY, PARTIAL, STALE, ERROR }

enum class HealthMetricUnit {
    COUNT,
    METERS,
    KILOCALORIES,
    MINUTES,
    SCORE,
    BEATS_PER_MINUTE,
    PERCENT,
    STRESS_SCORE,
    MILLILITERS_PER_KILOGRAM_PER_MINUTE,
}

enum class HealthMetricKey {
    STEPS,
    DISTANCE_METERS,
    ACTIVE_CALORIES_KCAL,
    ACTIVITY_DURATION_MINUTES,
    VALID_STAND_COUNT,
    SLEEP_MINUTES,
    SLEEP_DEEP_MINUTES,
    SLEEP_LIGHT_MINUTES,
    SLEEP_REM_MINUTES,
    SLEEP_AWAKE_MINUTES,
    SLEEP_SCORE,
    HEART_RATE_AVERAGE_BPM,
    HEART_RATE_MAXIMUM_BPM,
    HEART_RATE_MINIMUM_BPM,
    RESTING_HEART_RATE_BPM,
    OXYGEN_SATURATION_AVERAGE_PERCENT,
    OXYGEN_SATURATION_MAXIMUM_PERCENT,
    OXYGEN_SATURATION_MINIMUM_PERCENT,
    STRESS_AVERAGE,
    STRESS_MAXIMUM,
    STRESS_MINIMUM,
    VO2_MAX_AVERAGE,
    VO2_MAX_MAXIMUM,
    VO2_MAX_MINIMUM,
    WORKOUT_COUNT,
}

data class HealthMetricProvenance(
    val sourceId: String,
    val endpoint: String,
    val aggregation: String,
    val vendorKey: String? = null,
    /** Number of contributing sources; source identifiers themselves are never retained. */
    val sourceCount: Int? = null,
)

data class HealthMetricValue(
    val value: Double?,
    val unit: HealthMetricUnit,
    val status: HealthMetricStatus,
    val provenance: HealthMetricProvenance,
    /** Stable, non-sensitive diagnostic code. Never contains a server response. */
    val reasonCode: String? = null,
)

data class HealthTimeSeriesPoint(
    val epochMillis: Long,
    val value: Double,
)

data class HealthMetricTimeSeries(
    val unit: HealthMetricUnit,
    val status: HealthMetricStatus,
    val points: List<HealthTimeSeriesPoint>,
    val provenance: HealthMetricProvenance,
    /** Stable, non-sensitive diagnostic code. Never contains a server response. */
    val reasonCode: String? = null,
)

data class HealthMetrics(
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val activityDurationMinutes: Long? = null,
    val validStandCount: Long? = null,
    val heartRateAverageBpm: Long? = null,
    val heartRateMaximumBpm: Long? = null,
    val heartRateMinimumBpm: Long? = null,
    val restingHeartRateBpm: Long? = null,
    val oxygenSaturationAveragePercent: Double? = null,
    val oxygenSaturationMaximumPercent: Double? = null,
    val oxygenSaturationMinimumPercent: Double? = null,
    val sleepMinutes: Long? = null,
    val sleepDeepMinutes: Long? = null,
    val sleepLightMinutes: Long? = null,
    val sleepRemMinutes: Long? = null,
    val sleepAwakeMinutes: Long? = null,
    val sleepScore: Long? = null,
    val sleepStageCount: Int? = null,
    val stressAverage: Long? = null,
    val stressMaximum: Long? = null,
    val stressMinimum: Long? = null,
    val vo2MaxAverage: Double? = null,
    val vo2MaxMaximum: Double? = null,
    val vo2MaxMinimum: Double? = null,
    val workoutCount: Int? = null,
)

data class HealthSnapshot(
    val originPackages: Set<String>,
    val period: HealthPeriod,
    val observedAt: Long,
    val lastSyncAt: Long?,
    val freshness: HealthFreshness,
    val metrics: HealthMetrics,
    val missingFields: Set<String>,
    val confidence: Double,
    val metricValues: Map<HealthMetricKey, HealthMetricValue> = emptyMap(),
    /** Time-series data is independent from authoritative daily aggregate values. */
    val metricTimeSeries: Map<HealthMetricKey, HealthMetricTimeSeries> = emptyMap(),
)

sealed interface HealthAvailability {
    data object Available : HealthAvailability
    data object Unsupported : HealthAvailability
    data object NeedsProvider : HealthAvailability
    data class MissingPermissions(val permissions: Set<String>) : HealthAvailability
}

interface HealthGateway {
    val readPermissions: Set<String>
    fun availability(): HealthAvailability
    suspend fun grantedPermissions(): Set<String>
    suspend fun snapshot(period: HealthPeriod): Result<HealthSnapshot>
}
