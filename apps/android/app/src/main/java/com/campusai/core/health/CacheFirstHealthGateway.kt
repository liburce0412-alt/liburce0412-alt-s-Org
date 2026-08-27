package com.campusai.core.health

/** Selects one complete snapshot. It never merges metrics from overlapping sources. */
class CacheFirstHealthGateway(
    private val cache: HealthGateway,
    private val fallback: HealthGateway,
    private val fallbackEnabled: () -> Boolean = { true },
) : HealthGateway {
    override val readPermissions: Set<String>
        get() = if (fallbackEnabled()) fallback.readPermissions else emptySet()

    override fun availability(): HealthAvailability = when (cache.availability()) {
        HealthAvailability.Available -> HealthAvailability.Available
        else -> if (fallbackEnabled()) fallback.availability() else HealthAvailability.Unsupported
    }

    override suspend fun grantedPermissions(): Set<String> =
        if (fallbackEnabled()) fallback.grantedPermissions() else emptySet()

    override suspend fun snapshot(period: HealthPeriod): Result<HealthSnapshot> {
        val cached = cache.snapshot(period)
        return if (cached.isSuccess || !fallbackEnabled()) cached else fallback.snapshot(period)
    }
}
