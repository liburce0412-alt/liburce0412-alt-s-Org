package com.campusai.core.health.mifitness

import com.campusai.core.health.HealthMetricKey

data class MiFitnessStepRecord(
    val epochSeconds: Long,
    val steps: Long,
    val distanceMeters: Long,
    val caloriesKcal: Long,
    val sourceCount: Int,
    internal val canonicalFingerprint: String,
)

data class MiFitnessStepsPage(
    val records: List<MiFitnessStepRecord>,
    val hasMore: Boolean,
    val nextKey: String?,
)

data class MiFitnessStepsAggregate(
    val steps: Long,
    val distanceMeters: Long,
    val caloriesKcal: Long,
    val recordCount: Int,
    val recordEpochSeconds: Long,
    val sourceCount: Int,
)

/** Strict parser for Mi Fitness's vendor-produced daily_report/steps aggregate. */
object MiFitnessStepsParser {
    fun parse(rawJson: String): Result<MiFitnessStepsPage> = MiFitnessAggregateParser
        .parse(rawJson, "steps")
        .mapCatching { page ->
            MiFitnessStepsPage(
                records = page.records.map(::toStepRecord),
                hasMore = page.hasMore,
                nextKey = page.nextKey,
            )
        }

    private fun toStepRecord(record: MiFitnessAggregateRecord): MiFitnessStepRecord {
        val metrics = MiFitnessAggregateParser.metricsFor(record).getOrThrow()
        return MiFitnessStepRecord(
            epochSeconds = record.epochSeconds,
            steps = checkNotNull(metrics[HealthMetricKey.STEPS]?.value).toLong(),
            distanceMeters = checkNotNull(metrics[HealthMetricKey.DISTANCE_METERS]?.value).toLong(),
            caloriesKcal = checkNotNull(metrics[HealthMetricKey.ACTIVE_CALORIES_KCAL]?.value).toLong(),
            sourceCount = record.sourceCount,
            canonicalFingerprint = record.canonicalFingerprint,
        )
    }
}

/**
 * Selects one authoritative vendor daily aggregate. It deliberately has no summing operation:
 * get_fitness_data_by_time buckets are time-series points, not a daily-total fallback.
 */
object MiFitnessStepsAggregator {
    fun selectVendorDaily(
        records: List<MiFitnessStepRecord>,
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
    ): Result<MiFitnessStepsAggregate?> = runCatching {
        require(startEpochSeconds < endEpochSecondsExclusive)
        require(records.all { it.epochSeconds in startEpochSeconds until endEpochSecondsExclusive })
        val distinct = records.distinctBy(MiFitnessStepRecord::canonicalFingerprint)
        require(distinct.size <= 1) { "Conflicting vendor daily aggregates" }
        distinct.singleOrNull()?.let { record ->
            MiFitnessStepsAggregate(
                steps = record.steps,
                distanceMeters = record.distanceMeters,
                caloriesKcal = record.caloriesKcal,
                recordCount = 1,
                recordEpochSeconds = record.epochSeconds,
                sourceCount = record.sourceCount,
            )
        }
    }
}
