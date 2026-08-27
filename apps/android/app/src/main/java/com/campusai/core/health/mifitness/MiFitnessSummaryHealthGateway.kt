package com.campusai.core.health.mifitness

import com.campusai.core.health.HealthAvailability
import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthGateway
import com.campusai.core.health.HealthMetrics
import com.campusai.core.health.HealthPeriod
import com.campusai.core.health.HealthSnapshot
import com.campusai.core.health.healthFreshness
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MiFitnessSummaryHealthGateway(
    private val credentialStore: MiFitnessCredentialStore,
    private val cache: MiFitnessStepsCache,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneOffset.ofHours(8),
    private val requestZoneId: () -> ZoneId = ZoneId::systemDefault,
) : HealthGateway {
    override val readPermissions: Set<String> = emptySet()

    override fun availability(): HealthAvailability {
        val now = nowMillis()
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val credentials = runCatching { credentialStore.read() }.getOrNull()
            ?: return HealthAvailability.Unsupported
        val cached = runCatching {
            cache.read(canonicalTodayPeriod(today), today, credentials.accountScope)
        }.getOrNull()
        return if (cached != null) HealthAvailability.Available else HealthAvailability.Unsupported
    }

    override suspend fun grantedPermissions(): Set<String> = emptySet()

    override suspend fun snapshot(period: HealthPeriod): Result<HealthSnapshot> = runCatching {
        val now = nowMillis()
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val currentRequestZone = requestZoneId()
        val requestTodayStart = Instant.ofEpochMilli(now)
            .atZone(currentRequestZone)
            .toLocalDate()
            .atStartOfDay(currentRequestZone)
            .toInstant()
            .toEpochMilli()
        require(
            period.key.lowercase() in TODAY_KEYS &&
                period.startEpochMillis == requestTodayStart &&
                period.endEpochMillis >= period.startEpochMillis &&
                period.endEpochMillis <= now + CLOCK_SKEW_TOLERANCE_MILLIS
        ) {
            "Mi Fitness 缓存仅支持当天步数。"
        }
        val credentials = credentialStore.read() ?: error("尚未配置 Mi Fitness 凭据。")
        val summary = cache.read(canonicalTodayPeriod(today), today, credentials.accountScope)
            ?: error("Mi Fitness 当天步数缓存不可用。")
        HealthSnapshot(
            originPackages = setOf(SOURCE_ID),
            period = summary.period,
            observedAt = summary.observedAt,
            lastSyncAt = summary.lastSyncAt,
            freshness = healthFreshness(now, summary.lastSyncAt),
            metrics = HealthMetrics(steps = summary.steps),
            missingFields = MISSING_NON_STEP_FIELDS,
            confidence = PROVISIONAL_CONFIDENCE,
        )
    }

    private fun canonicalTodayPeriod(today: java.time.LocalDate): HealthPeriod {
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        return HealthPeriod(startEpochMillis = start, endEpochMillis = end, key = "today")
    }

    companion object {
        const val SOURCE_ID = "mi_fitness_cloud_cn"
        const val PROVISIONAL_CONFIDENCE = 0.65

        private const val CLOCK_SKEW_TOLERANCE_MILLIS = 60_000L
        private val TODAY_KEYS = setOf("today", "day", "今天", "今日")
        private val MISSING_NON_STEP_FIELDS = setOf(
            "distance",
            "activeCalories",
            "heartRate",
            "restingHeartRate",
            "oxygenSaturation",
            "sleep",
            "workouts",
        )
    }
}
