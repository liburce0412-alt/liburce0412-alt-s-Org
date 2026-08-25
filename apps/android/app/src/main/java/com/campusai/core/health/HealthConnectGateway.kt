package com.campusai.core.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.Record
import java.time.Instant

class HealthConnectGateway(context: Context) : HealthGateway {
    private val appContext = context.applicationContext
    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(appContext)
        } else null
    }

    override val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    )

    override fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(appContext)) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val missing = readPermissions.filterTo(mutableSetOf()) {
                ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) HealthAvailability.Available
            else HealthAvailability.MissingPermissions(missing)
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NeedsProvider
        else -> HealthAvailability.Unsupported
    }

    override suspend fun grantedPermissions(): Set<String> = client?.permissionController?.getGrantedPermissions().orEmpty()

    override suspend fun snapshot(period: HealthPeriod): Result<HealthSnapshot> = runCatching {
        val healthClient = client ?: error("Health Connect 当前不可用")
        val granted = healthClient.permissionController.getGrantedPermissions()
        val timeRange = TimeRangeFilter.between(
            Instant.ofEpochMilli(period.startEpochMillis),
            Instant.ofEpochMilli(period.endEpochMillis),
        )
        val aggregateMetrics = buildSet {
            if (canRead<StepsRecord>(granted)) add(StepsRecord.COUNT_TOTAL)
            if (canRead<DistanceRecord>(granted)) add(DistanceRecord.DISTANCE_TOTAL)
            if (canRead<ActiveCaloriesBurnedRecord>(granted)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (canRead<HeartRateRecord>(granted)) {
                add(HeartRateRecord.BPM_AVG)
                add(HeartRateRecord.BPM_MAX)
            }
            if (canRead<RestingHeartRateRecord>(granted)) add(RestingHeartRateRecord.BPM_AVG)
        }
        check(aggregateMetrics.isNotEmpty() || canRead<SleepSessionRecord>(granted) || canRead<ExerciseSessionRecord>(granted) || canRead<OxygenSaturationRecord>(granted)) {
            "Health Connect 读取权限尚未授予"
        }
        val aggregate = aggregateMetrics.takeIf { it.isNotEmpty() }?.let {
            healthClient.aggregate(AggregateRequest(it, timeRange))
        }
        val sleeps = if (canRead<SleepSessionRecord>(granted)) {
            healthClient.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange)).records
        } else emptyList()
        val workouts = if (canRead<ExerciseSessionRecord>(granted)) {
            healthClient.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRange)).records
        } else emptyList()
        val oxygen = if (canRead<OxygenSaturationRecord>(granted)) {
            healthClient.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRange)).records
        } else emptyList()

        val latestAggregateRecords = buildList<Record> {
            if (canRead<StepsRecord>(granted)) latestRecord<StepsRecord>(healthClient, timeRange)?.let(::add)
            if (canRead<DistanceRecord>(granted)) latestRecord<DistanceRecord>(healthClient, timeRange)?.let(::add)
            if (canRead<ActiveCaloriesBurnedRecord>(granted)) latestRecord<ActiveCaloriesBurnedRecord>(healthClient, timeRange)?.let(::add)
            if (canRead<HeartRateRecord>(granted)) latestRecord<HeartRateRecord>(healthClient, timeRange)?.let(::add)
            if (canRead<RestingHeartRateRecord>(granted)) latestRecord<RestingHeartRateRecord>(healthClient, timeRange)?.let(::add)
        }
        val provenanceRecords = latestAggregateRecords + sleeps + workouts + oxygen
        val provenance = assembleHealthProvenance(
            aggregateOriginPackages = aggregate?.dataOrigins?.mapTo(mutableSetOf()) { it.packageName }.orEmpty(),
            recordEvidence = provenanceRecords.map {
                HealthRecordEvidence(
                    originPackage = it.metadata.dataOrigin.packageName,
                    lastModifiedAtMillis = it.metadata.lastModifiedTime.toEpochMilli(),
                )
            },
        )
        val now = System.currentTimeMillis()
        val metrics = HealthMetrics(
            steps = aggregate?.get(StepsRecord.COUNT_TOTAL),
            distanceMeters = aggregate?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            activeCaloriesKcal = aggregate?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            heartRateAverageBpm = aggregate?.get(HeartRateRecord.BPM_AVG),
            heartRateMaximumBpm = aggregate?.get(HeartRateRecord.BPM_MAX),
            restingHeartRateBpm = aggregate?.get(RestingHeartRateRecord.BPM_AVG),
            oxygenSaturationAveragePercent = oxygen.takeIf { it.isNotEmpty() }?.map { it.percentage.value }?.average(),
            sleepMinutes = sleeps.takeIf { it.isNotEmpty() }?.sumOf { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()).coerceAtLeast(0L) / 60_000L },
            sleepStageCount = sleeps.takeIf { it.isNotEmpty() }?.sumOf { it.stages.size },
            workoutCount = workouts.size.takeIf { workouts.isNotEmpty() },
        )
        val missing = buildSet {
            if (metrics.steps == null) add("steps")
            if (metrics.distanceMeters == null) add("distance")
            if (metrics.activeCaloriesKcal == null) add("activeCalories")
            if (metrics.heartRateAverageBpm == null) add("heartRate")
            if (metrics.restingHeartRateBpm == null) add("restingHeartRate")
            if (metrics.oxygenSaturationAveragePercent == null) add("oxygenSaturation")
            if (metrics.sleepMinutes == null) add("sleep")
            if (metrics.workoutCount == null) add("workouts")
        }
        HealthSnapshot(
            originPackages = provenance.originPackages,
            period = period,
            observedAt = now,
            lastSyncAt = provenance.lastSyncAt,
            freshness = healthFreshness(now, provenance.lastSyncAt),
            metrics = metrics,
            missingFields = missing,
            confidence = if (provenance.originPackages.isEmpty()) 0.0 else 1.0,
        )
    }

    private suspend inline fun <reified T : Record> latestRecord(
        healthClient: HealthConnectClient,
        timeRange: TimeRangeFilter,
    ): T? = healthClient.readRecords(
        ReadRecordsRequest(
            recordType = T::class,
            timeRangeFilter = timeRange,
            ascendingOrder = false,
            pageSize = 1,
        ),
    ).records.firstOrNull()

    private inline fun <reified T : androidx.health.connect.client.records.Record> canRead(granted: Set<String>): Boolean =
        HealthPermission.getReadPermission(T::class) in granted
}
