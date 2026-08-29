package com.campusai.core.health.mifitness

import android.content.Context
import com.campusai.core.health.HealthMetricKey
import com.campusai.core.health.HealthMetricProvenance
import com.campusai.core.health.HealthMetricStatus
import com.campusai.core.health.HealthMetricTimeSeries
import com.campusai.core.health.HealthMetricUnit
import com.campusai.core.health.HealthMetricValue
import com.campusai.core.health.HealthPeriod
import com.campusai.core.health.HealthTimeSeriesPoint
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class MiFitnessStepsSummary(
    val period: HealthPeriod,
    val localDate: LocalDate,
    val accountScope: String,
    val steps: Long?,
    val recordCount: Int,
    val observedAt: Long,
    val lastSyncAt: Long,
    val metricValues: Map<HealthMetricKey, HealthMetricValue> = defaultStepMetricValues(steps),
    val metricTimeSeries: Map<HealthMetricKey, HealthMetricTimeSeries> = emptyMap(),
    /** Stable digest only; raw workout identifiers and records are never persisted. */
    val workoutRevision: String? = null,
    /** Retained only for source compatibility with the v1 model; verified v2 summaries require false. */
    val schemaProvisional: Boolean = false,
    /** Retained only for source compatibility with the v1 model; verified v2 summaries require false. */
    val aggregationProvisional: Boolean = false,
)

class MiFitnessStepsCache internal constructor(
    private val storage: MiFitnessSecretStorage,
) {
    constructor(context: Context) : this(SecurePreferencesMiFitnessStorage(context))

    fun save(summary: MiFitnessStepsSummary): Result<Unit> {
        validationError(summary)?.let { return Result.failure(IllegalArgumentException(it)) }
        val metrics = JSONObject()
        summary.metricValues.toSortedMap(compareBy(HealthMetricKey::name)).forEach { (key, metric) ->
            metrics.put(key.name, encodeMetric(metric))
        }
        val timeSeries = JSONObject()
        summary.metricTimeSeries.toSortedMap(compareBy(HealthMetricKey::name)).forEach { (key, series) ->
            timeSeries.put(key.name, encodeTimeSeries(series))
        }
        val payload = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("periodKey", summary.period.key)
            .put("startEpochMillis", summary.period.startEpochMillis)
            .put("endEpochMillis", summary.period.endEpochMillis)
            .put("localDate", summary.localDate.toString())
            .put("accountScope", summary.accountScope)
            .put("steps", summary.steps ?: JSONObject.NULL)
            .put("recordCount", summary.recordCount)
            .put("observedAt", summary.observedAt)
            .put("lastSyncAt", summary.lastSyncAt)
            .put("schemaProvisional", false)
            .put("aggregationProvisional", false)
            .put("metricValues", metrics)
            .put("metricTimeSeries", timeSeries)
            .put("workoutRevision", summary.workoutRevision ?: JSONObject.NULL)
            .toString()
        return if (storage.write(STORAGE_KEY, payload)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("系统安全存储不可用，健康摘要未缓存。"))
        }
    }

    fun read(period: HealthPeriod, localDate: LocalDate, accountScope: String): MiFitnessStepsSummary? {
        val payload = storage.read(STORAGE_KEY)
        if (payload.isBlank()) return null
        return runCatching {
            val json = JSONObject(payload)
            val version = json.optInt("version")
            if (version !in READABLE_FORMAT_VERSIONS) return@runCatching null
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
            if (
                cachedPeriod.startEpochMillis != period.startEpochMillis ||
                cachedPeriod.key != period.key
            ) {
                return@runCatching null
            }
            if (json.getString("localDate") != localDate.toString()) return@runCatching null
            if (json.getString("accountScope") != accountScope) return@runCatching null
            val summary = MiFitnessStepsSummary(
                period = period,
                localDate = LocalDate.parse(json.getString("localDate")),
                accountScope = json.getString("accountScope"),
                steps = if (json.has("steps") && json.isNull("steps")) null else json.getLong("steps"),
                recordCount = json.getInt("recordCount"),
                observedAt = json.getLong("observedAt"),
                lastSyncAt = json.getLong("lastSyncAt"),
                metricValues = decodeMetrics(json.getJSONObject("metricValues")),
                metricTimeSeries = decodeTimeSeriesMap(json.getJSONObject("metricTimeSeries")),
                workoutRevision = if (
                    version < FORMAT_VERSION || !json.has("workoutRevision") || json.isNull("workoutRevision")
                ) {
                    null
                } else {
                    json.getString("workoutRevision")
                },
                schemaProvisional = json.getBoolean("schemaProvisional"),
                aggregationProvisional = json.getBoolean("aggregationProvisional"),
            )
            summary.takeIf { validationError(it) == null }
        }.getOrNull()
    }

    fun delete(): Boolean = storage.write(STORAGE_KEY, "")

    companion object {
        private const val FORMAT_VERSION = 4
        private val READABLE_FORMAT_VERSIONS = setOf(3, FORMAT_VERSION)
        private const val STORAGE_KEY = "mi_fitness_cn_health_summary_v2"

        private fun encodeMetric(metric: HealthMetricValue): JSONObject = JSONObject()
            .put("value", metric.value ?: JSONObject.NULL)
            .put("unit", metric.unit.name)
            .put("status", metric.status.name)
            .put("reasonCode", metric.reasonCode ?: JSONObject.NULL)
            .put(
                "provenance",
                JSONObject()
                    .put("sourceId", metric.provenance.sourceId)
                    .put("endpoint", metric.provenance.endpoint)
                    .put("aggregation", metric.provenance.aggregation)
                    .put("vendorKey", metric.provenance.vendorKey ?: JSONObject.NULL)
                    .put("sourceCount", metric.provenance.sourceCount ?: JSONObject.NULL),
            )

        private fun encodeProvenance(provenance: HealthMetricProvenance): JSONObject = JSONObject()
            .put("sourceId", provenance.sourceId)
            .put("endpoint", provenance.endpoint)
            .put("aggregation", provenance.aggregation)
            .put("vendorKey", provenance.vendorKey ?: JSONObject.NULL)
            .put("sourceCount", provenance.sourceCount ?: JSONObject.NULL)

        private fun decodeProvenance(provenance: JSONObject): HealthMetricProvenance = HealthMetricProvenance(
            sourceId = provenance.getString("sourceId"),
            endpoint = provenance.getString("endpoint"),
            aggregation = provenance.getString("aggregation"),
            vendorKey = if (provenance.isNull("vendorKey")) {
                null
            } else {
                provenance.getString("vendorKey").takeIf(String::isNotBlank)
            },
            sourceCount = if (provenance.isNull("sourceCount")) {
                null
            } else {
                provenance.getInt("sourceCount")
            },
        )

        private fun decodeMetrics(json: JSONObject): Map<HealthMetricKey, HealthMetricValue> = buildMap {
            val names = json.keys().asSequence().toList()
            require(names.size <= HealthMetricKey.entries.size)
            names.forEach { name ->
                val key = HealthMetricKey.valueOf(name)
                val encoded = json.getJSONObject(name)
                val provenance = encoded.getJSONObject("provenance")
                put(
                    key,
                    HealthMetricValue(
                        value = if (encoded.isNull("value")) null else encoded.getDouble("value"),
                        unit = HealthMetricUnit.valueOf(encoded.getString("unit")),
                        status = HealthMetricStatus.valueOf(encoded.getString("status")),
                        provenance = HealthMetricProvenance(
                            sourceId = provenance.getString("sourceId"),
                            endpoint = provenance.getString("endpoint"),
                            aggregation = provenance.getString("aggregation"),
                            vendorKey = if (provenance.isNull("vendorKey")) {
                                null
                            } else {
                                provenance.getString("vendorKey").takeIf(String::isNotBlank)
                            },
                            sourceCount = if (provenance.isNull("sourceCount")) {
                                null
                            } else {
                                provenance.getInt("sourceCount")
                            },
                        ),
                        reasonCode = if (encoded.isNull("reasonCode")) {
                            null
                        } else {
                            encoded.getString("reasonCode").takeIf(String::isNotBlank)
                        },
                    ),
                )
            }
        }

        private fun encodeTimeSeries(series: HealthMetricTimeSeries): JSONObject {
            val points = JSONArray()
            series.points.forEach { point ->
                points.put(JSONObject().put("epochMillis", point.epochMillis).put("value", point.value))
            }
            return JSONObject()
                .put("unit", series.unit.name)
                .put("status", series.status.name)
                .put("reasonCode", series.reasonCode ?: JSONObject.NULL)
                .put("provenance", encodeProvenance(series.provenance))
                .put("points", points)
        }

        private fun decodeTimeSeriesMap(json: JSONObject): Map<HealthMetricKey, HealthMetricTimeSeries> = buildMap {
            val names = json.keys().asSequence().toList()
            require(names.size <= 1)
            names.forEach { name ->
                val key = HealthMetricKey.valueOf(name)
                val encoded = json.getJSONObject(name)
                val points = encoded.getJSONArray("points")
                require(points.length() <= MAX_SERIES_POINTS)
                put(
                    key,
                    HealthMetricTimeSeries(
                        unit = HealthMetricUnit.valueOf(encoded.getString("unit")),
                        status = HealthMetricStatus.valueOf(encoded.getString("status")),
                        points = buildList {
                            repeat(points.length()) { index ->
                                val point = points.getJSONObject(index)
                                add(HealthTimeSeriesPoint(point.getLong("epochMillis"), point.getDouble("value")))
                            }
                        },
                        provenance = decodeProvenance(encoded.getJSONObject("provenance")),
                        reasonCode = if (encoded.isNull("reasonCode")) {
                            null
                        } else {
                            encoded.getString("reasonCode").takeIf(String::isNotBlank)
                        },
                    ),
                )
            }
        }

        private fun validationError(summary: MiFitnessStepsSummary): String? {
            val stepMetric = summary.metricValues[HealthMetricKey.STEPS]
            return when {
                summary.period.key.isBlank() -> "缓存时间窗口无效。"
                summary.period.startEpochMillis < 0 -> "缓存起始时间无效。"
                summary.period.endEpochMillis < summary.period.startEpochMillis -> "缓存结束时间无效。"
                !ACCOUNT_SCOPE_PATTERN.matches(summary.accountScope) -> "缓存账号范围无效。"
                summary.steps != null && summary.steps !in 0..MAX_TOTAL_STEPS -> "缓存步数无效。"
                summary.recordCount !in 0..MAX_RECORDS -> "缓存记录数无效。"
                summary.steps != null && summary.recordCount == 0 -> "缓存步数记录数无效。"
                summary.observedAt < 0 || summary.lastSyncAt < 0 -> "缓存同步时间无效。"
                summary.workoutRevision != null && !SHA256_PATTERN.matches(summary.workoutRevision) ->
                    "缓存运动修订值无效。"
                summary.schemaProvisional || summary.aggregationProvisional -> "缓存版本标记无效。"
                summary.metricValues.isEmpty() || summary.metricValues.size > HealthMetricKey.entries.size ->
                    "缓存指标集合无效。"
                stepMetric == null || !stepMetricMatchesSummary(stepMetric, summary.steps) ->
                    "缓存步数来源无效。"
                summary.metricValues.any { (key, value) -> metricValidationError(key, value) != null } ->
                    "缓存指标无效。"
                summary.metricTimeSeries.size > 1 || summary.metricTimeSeries.keys.any { it != HealthMetricKey.STEPS } ->
                    "缓存趋势集合无效。"
                summary.metricTimeSeries.any { (key, series) ->
                    timeSeriesValidationError(key, series, summary.period) != null
                } -> "缓存趋势数据无效。"
                else -> null
            }
        }

        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")

        private fun stepMetricMatchesSummary(metric: HealthMetricValue, steps: Long?): Boolean = if (steps == null) {
            metric.value == null && metric.status in setOf(
                HealthMetricStatus.EMPTY,
                HealthMetricStatus.PARTIAL,
                HealthMetricStatus.ERROR,
            )
        } else {
            metric.status == HealthMetricStatus.AVAILABLE && metric.value?.toLong() == steps
        }

        private fun metricValidationError(key: HealthMetricKey, metric: HealthMetricValue): String? {
            val valueRequired = metric.status in setOf(HealthMetricStatus.AVAILABLE, HealthMetricStatus.STALE)
            val valueForbidden = metric.status in setOf(HealthMetricStatus.EMPTY, HealthMetricStatus.ERROR)
            val allowedVendorKeys = MiFitnessMetricRegistry.byRequestKey.keys + "sport_records"
            return when {
                valueRequired && metric.value == null -> "missing_value"
                valueForbidden && metric.value != null -> "unexpected_value"
                metric.value?.isFinite() == false || (metric.value != null && metric.value < 0.0) -> "invalid_value"
                metric.provenance.sourceId != MiFitnessMetricRegistry.SOURCE_ID -> "invalid_source"
                metric.provenance.vendorKey !in allowedVendorKeys -> "invalid_vendor_key"
                metric.provenance.sourceCount != null && metric.provenance.sourceCount !in 0..MAX_RECORDS ->
                    "invalid_source_count"
                metric.reasonCode != null && !REASON_CODE_PATTERN.matches(metric.reasonCode) -> "invalid_reason"
                expectedUnit(key) != metric.unit -> "invalid_unit"
                else -> null
            }
        }

        private fun timeSeriesValidationError(
            key: HealthMetricKey,
            series: HealthMetricTimeSeries,
            period: HealthPeriod,
        ): String? {
            val requiresPoints = series.status in setOf(
                HealthMetricStatus.AVAILABLE,
                HealthMetricStatus.PARTIAL,
                HealthMetricStatus.STALE,
            )
            val forbidsPoints = series.status in setOf(HealthMetricStatus.EMPTY, HealthMetricStatus.ERROR)
            val points = series.points
            return when {
                key != HealthMetricKey.STEPS || series.unit != HealthMetricUnit.COUNT -> "invalid_series_key"
                requiresPoints && points.isEmpty() -> "missing_series_points"
                forbidsPoints && points.isNotEmpty() -> "unexpected_series_points"
                points.size > MAX_SERIES_POINTS -> "series_point_limit"
                points.zipWithNext().any { (first, second) -> first.epochMillis >= second.epochMillis } ->
                    "unordered_series_points"
                points.any { point ->
                    point.epochMillis !in period.startEpochMillis..period.endEpochMillis ||
                        !point.value.isFinite() || point.value !in 0.0..MAX_SERIES_STEPS ||
                        point.value % 1.0 != 0.0
                } -> "invalid_series_point"
                series.provenance.sourceId != MiFitnessMetricRegistry.SOURCE_ID -> "invalid_series_source"
                series.provenance.endpoint != MiFitnessProtocol.FITNESS_BY_TIME_PATH -> "invalid_series_endpoint"
                series.provenance.aggregation != MiFitnessMetricRegistry.VENDOR_TIME_SERIES_AGGREGATION ->
                    "invalid_series_aggregation"
                series.provenance.vendorKey != "steps" -> "invalid_series_vendor_key"
                series.provenance.sourceCount != points.size -> "invalid_series_source_count"
                series.reasonCode != null && !REASON_CODE_PATTERN.matches(series.reasonCode) -> "invalid_series_reason"
                series.status != HealthMetricStatus.AVAILABLE && series.reasonCode == null -> "missing_series_reason"
                else -> null
            }
        }

        private fun expectedUnit(key: HealthMetricKey): HealthMetricUnit {
            MiFitnessMetricRegistry.definitions.forEach { definition ->
                definition.outputs[key]?.let { return it }
            }
            return checkNotNull(MiFitnessMetricRegistry.workoutDefinition.outputs[key])
        }

        private val ACCOUNT_SCOPE_PATTERN = Regex("[0-9a-f]{32}")
        private val REASON_CODE_PATTERN = Regex("[a-z0-9_]{1,64}")
        private const val MAX_TOTAL_STEPS = 10_000_000L
        private const val MAX_RECORDS = 10_000
        private const val MAX_SERIES_POINTS = 10_000
        private const val MAX_SERIES_STEPS = 1_000_000.0
    }
}

private fun defaultStepMetricValues(steps: Long?): Map<HealthMetricKey, HealthMetricValue> = mapOf(
    HealthMetricKey.STEPS to HealthMetricValue(
        value = steps?.toDouble(),
        unit = HealthMetricUnit.COUNT,
        status = if (steps == null) HealthMetricStatus.EMPTY else HealthMetricStatus.AVAILABLE,
        provenance = MiFitnessMetricRegistry.provenance("steps", null),
        reasonCode = if (steps == null) "no_cloud_data" else null,
    ),
)
