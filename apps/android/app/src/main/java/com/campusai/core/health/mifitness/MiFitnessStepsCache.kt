package com.campusai.core.health.mifitness

import android.content.Context
import com.campusai.core.health.HealthPeriod
import org.json.JSONObject
import java.time.LocalDate

data class MiFitnessStepsSummary(
    val period: HealthPeriod,
    val localDate: LocalDate,
    val accountScope: String,
    val steps: Long,
    val recordCount: Int,
    val observedAt: Long,
    val lastSyncAt: Long,
    /** The vendor has not documented the record schema or aggregation semantics. */
    val schemaProvisional: Boolean = true,
    val aggregationProvisional: Boolean = true,
)

class MiFitnessStepsCache internal constructor(
    private val storage: MiFitnessSecretStorage,
) {
    constructor(context: Context) : this(SecurePreferencesMiFitnessStorage(context))

    fun save(summary: MiFitnessStepsSummary): Result<Unit> {
        validationError(summary)?.let { return Result.failure(IllegalArgumentException(it)) }
        val payload = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("periodKey", summary.period.key)
            .put("startEpochMillis", summary.period.startEpochMillis)
            .put("endEpochMillis", summary.period.endEpochMillis)
            .put("localDate", summary.localDate.toString())
            .put("accountScope", summary.accountScope)
            .put("steps", summary.steps)
            .put("recordCount", summary.recordCount)
            .put("observedAt", summary.observedAt)
            .put("lastSyncAt", summary.lastSyncAt)
            .put("schemaProvisional", summary.schemaProvisional)
            .put("aggregationProvisional", summary.aggregationProvisional)
            .toString()
        return if (storage.write(STORAGE_KEY, payload)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("系统安全存储不可用，步数摘要未缓存。"))
        }
    }

    fun read(period: HealthPeriod, localDate: LocalDate, accountScope: String): MiFitnessStepsSummary? {
        val payload = storage.read(STORAGE_KEY)
        if (payload.isBlank()) return null
        return runCatching {
            val json = JSONObject(payload)
            if (json.optInt("version") != FORMAT_VERSION) return@runCatching null
            val cachedPeriod = HealthPeriod(
                startEpochMillis = json.getLong("startEpochMillis"),
                endEpochMillis = json.getLong("endEpochMillis"),
                key = json.getString("periodKey"),
            )
            if (
                cachedPeriod.key.isBlank() ||
                cachedPeriod.startEpochMillis < 0 ||
                cachedPeriod.endEpochMillis < cachedPeriod.startEpochMillis
            ) {
                return@runCatching null
            }
            if (cachedPeriod.startEpochMillis != period.startEpochMillis || cachedPeriod.key != period.key) {
                return@runCatching null
            }
            if (json.getString("localDate") != localDate.toString()) return@runCatching null
            if (json.getString("accountScope") != accountScope) return@runCatching null
            val summary = MiFitnessStepsSummary(
                period = period,
                localDate = LocalDate.parse(json.getString("localDate")),
                accountScope = json.getString("accountScope"),
                steps = json.getLong("steps"),
                recordCount = json.getInt("recordCount"),
                observedAt = json.getLong("observedAt"),
                lastSyncAt = json.getLong("lastSyncAt"),
                schemaProvisional = json.getBoolean("schemaProvisional"),
                aggregationProvisional = json.getBoolean("aggregationProvisional"),
            )
            summary.takeIf { validationError(it) == null }
        }.getOrNull()
    }

    fun delete(): Boolean = storage.write(STORAGE_KEY, "")

    companion object {
        private const val FORMAT_VERSION = 1
        private const val STORAGE_KEY = "mi_fitness_cn_steps_summary_v1"

        private fun validationError(summary: MiFitnessStepsSummary): String? = when {
            summary.period.key.isBlank() -> "缓存时间窗口无效。"
            summary.period.startEpochMillis < 0 -> "缓存起始时间无效。"
            summary.period.endEpochMillis < summary.period.startEpochMillis -> "缓存结束时间无效。"
            !ACCOUNT_SCOPE_PATTERN.matches(summary.accountScope) -> "缓存账号范围无效。"
            summary.steps !in 0..MAX_TOTAL_STEPS -> "缓存步数无效。"
            summary.recordCount !in 0..MAX_RECORDS -> "缓存记录数无效。"
            summary.observedAt < 0 || summary.lastSyncAt < 0 -> "缓存同步时间无效。"
            !summary.schemaProvisional || !summary.aggregationProvisional -> "缓存版本标记无效。"
            else -> null
        }

        private val ACCOUNT_SCOPE_PATTERN = Regex("[0-9a-f]{32}")
        private const val MAX_TOTAL_STEPS = 10_000_000L
        private const val MAX_RECORDS = 10_000
    }
}
