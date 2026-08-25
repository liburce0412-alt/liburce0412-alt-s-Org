package com.campusai.core.health

internal data class HealthRecordEvidence(
    val originPackage: String,
    val lastModifiedAtMillis: Long,
)

internal data class HealthProvenance(
    val originPackages: Set<String>,
    val lastSyncAt: Long?,
)

internal data class HealthExportGapEvidence(
    val source: String,
    val missingFields: Set<String>,
    val message: String,
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

/**
 * Distinguishes a source that exported only part of a health snapshot from a measured zero.
 * The raw snapshot remains nullable even when a presentation layer chooses to show an empty
 * successfully-read daily window as zero.
 */
internal fun healthExportGap(snapshot: HealthSnapshot?): HealthExportGapEvidence? {
    snapshot ?: return null
    val missing = snapshot.missingFields.intersect(setOf("steps", "sleep"))
    if (missing.isEmpty()) return null
    val gadgetbridge = snapshot.originPackages.firstOrNull { it.contains("gadgetbridge", ignoreCase = true) }
        ?: return null
    if (snapshot.lastSyncAt == null) return null
    val labels = buildList {
        if ("steps" in missing) add("步数")
        if ("sleep" in missing) add("睡眠")
    }
    return HealthExportGapEvidence(
        source = gadgetbridge,
        missingFields = missing,
        message = "Gadgetbridge 已写入其他健康记录，但本次没有导出${labels.joinToString("、")}；页面可按无记录显示 0，原始值仍为空。",
    )
}
