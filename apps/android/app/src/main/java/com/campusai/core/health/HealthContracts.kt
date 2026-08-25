package com.campusai.core.health

data class HealthPeriod(val startEpochMillis: Long, val endEpochMillis: Long, val key: String)

enum class HealthFreshness { LIVE, FRESH, STALE, UNKNOWN }

data class HealthMetrics(
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val heartRateAverageBpm: Long? = null,
    val heartRateMaximumBpm: Long? = null,
    val restingHeartRateBpm: Long? = null,
    val oxygenSaturationAveragePercent: Double? = null,
    val sleepMinutes: Long? = null,
    val sleepStageCount: Int? = null,
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
)

data class BandLiveSnapshot(
    val observedAt: Long,
    val connected: Boolean?,
    val batteryPercent: Int?,
    val charging: Boolean?,
    val wearing: Boolean?,
    val sleeping: Boolean?,
    val heartRateBpm: Int?,
    val stepDelta: Long?,
    val capabilityBits: Long,
    val bridgeState: BandLiveState = BandLiveState.UNAVAILABLE,
    val statusMessage: String? = null,
    val historySyncState: BandHistorySyncState = BandHistorySyncState.UNAVAILABLE,
    val source: String = "CaesarBandBridge",
) {
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean = now - observedAt in 0..15_000L
}

enum class BandLiveState { UNAVAILABLE, IDLE, LISTENING, ERROR }

enum class BandHistorySyncState { UNAVAILABLE, IDLE, CONNECTING, REQUESTED, FINISHED, ERROR }

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

interface BandLiveGateway {
    suspend fun snapshot(): Result<BandLiveSnapshot>
    fun startSession(): Result<Unit>
    fun stopSession(): Result<Unit>
    fun triggerHistorySync(): Result<Unit>
}
