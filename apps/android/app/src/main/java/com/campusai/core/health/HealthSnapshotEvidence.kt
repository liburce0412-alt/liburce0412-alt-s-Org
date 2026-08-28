package com.campusai.core.health

internal data class HealthRecordEvidence(
    val originPackage: String,
    val lastModifiedAtMillis: Long,
)

internal data class HealthProvenance(
    val originPackages: Set<String>,
    val lastSyncAt: Long?,
)

internal fun assembleHealthProvenance(
    aggregateOriginPackages: Set<String>,
    recordEvidence: Iterable<HealthRecordEvidence>,
): HealthProvenance {
    val evidence = recordEvidence.toList()
    return HealthProvenance(
        originPackages = (aggregateOriginPackages + evidence.map { it.originPackage })
            .filter(String::isNotBlank)
            .toSet(),
        // Health Connect metadata.lastModifiedTime is the closest deterministic evidence of
        // when a source inserted or changed a record. Never substitute the query end time.
        lastSyncAt = evidence.maxOfOrNull(HealthRecordEvidence::lastModifiedAtMillis),
    )
}

internal fun healthFreshness(nowMillis: Long, lastSyncAt: Long?): HealthFreshness = when {
    lastSyncAt == null -> HealthFreshness.UNKNOWN
    lastSyncAt > nowMillis + 60_000L -> HealthFreshness.UNKNOWN
    nowMillis - lastSyncAt <= 30 * 60_000L -> HealthFreshness.FRESH
    else -> HealthFreshness.STALE
}
