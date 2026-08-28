package com.campusai.core.health.mifitness

import com.campusai.core.health.HealthMetricKey
import com.campusai.core.health.HealthMetricProvenance
import com.campusai.core.health.HealthMetricStatus
import com.campusai.core.health.HealthMetricUnit
import com.campusai.core.health.HealthMetricValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal data class MiFitnessMetricDefinition(
    val requestKey: String,
    val displayName: String,
    val outputs: Map<HealthMetricKey, HealthMetricUnit>,
)

/**
 * Keys and field meanings recovered from Mi Fitness 3.58.0's CloudKey and Cloud*Report models.
 * A key is not added here until its value schema, unit, and daily aggregation semantics are known.
 */
internal object MiFitnessMetricRegistry {
    const val DAILY_TAG = "daily_report"
    const val SOURCE_ID = "mi_fitness_cloud_cn"
    const val VENDOR_DAILY_AGGREGATION = "vendor_daily_aggregate"
    const val VENDOR_SPORT_AGGREGATION = "vendor_sport_records"
    const val VENDOR_TIME_SERIES_AGGREGATION = "vendor_time_series"

    val definitions: List<MiFitnessMetricDefinition> = listOf(
        definition(
            "steps",
            "步数",
            HealthMetricKey.STEPS to HealthMetricUnit.COUNT,
            HealthMetricKey.DISTANCE_METERS to HealthMetricUnit.METERS,
            HealthMetricKey.ACTIVE_CALORIES_KCAL to HealthMetricUnit.KILOCALORIES,
        ),
        definition(
            "calories",
            "活动热量",
            HealthMetricKey.ACTIVE_CALORIES_KCAL to HealthMetricUnit.KILOCALORIES,
        ),
        definition(
            "intensity",
            "中高强度活动时长",
            HealthMetricKey.ACTIVITY_DURATION_MINUTES to HealthMetricUnit.MINUTES,
        ),
        definition(
            "valid_stand",
            "有效站立",
            HealthMetricKey.VALID_STAND_COUNT to HealthMetricUnit.COUNT,
        ),
        definition(
            "sleep",
            "睡眠",
            HealthMetricKey.SLEEP_MINUTES to HealthMetricUnit.MINUTES,
            HealthMetricKey.SLEEP_DEEP_MINUTES to HealthMetricUnit.MINUTES,
            HealthMetricKey.SLEEP_LIGHT_MINUTES to HealthMetricUnit.MINUTES,
            HealthMetricKey.SLEEP_REM_MINUTES to HealthMetricUnit.MINUTES,
            HealthMetricKey.SLEEP_AWAKE_MINUTES to HealthMetricUnit.MINUTES,
            HealthMetricKey.SLEEP_SCORE to HealthMetricUnit.SCORE,
        ),
        definition(
            "heart_rate",
            "心率",
            HealthMetricKey.HEART_RATE_AVERAGE_BPM to HealthMetricUnit.BEATS_PER_MINUTE,
            HealthMetricKey.HEART_RATE_MAXIMUM_BPM to HealthMetricUnit.BEATS_PER_MINUTE,
            HealthMetricKey.HEART_RATE_MINIMUM_BPM to HealthMetricUnit.BEATS_PER_MINUTE,
            HealthMetricKey.RESTING_HEART_RATE_BPM to HealthMetricUnit.BEATS_PER_MINUTE,
        ),
        definition(
            "spo2",
            "血氧",
            HealthMetricKey.OXYGEN_SATURATION_AVERAGE_PERCENT to HealthMetricUnit.PERCENT,
            HealthMetricKey.OXYGEN_SATURATION_MAXIMUM_PERCENT to HealthMetricUnit.PERCENT,
            HealthMetricKey.OXYGEN_SATURATION_MINIMUM_PERCENT to HealthMetricUnit.PERCENT,
        ),
        definition(
            "stress",
            "压力",
            HealthMetricKey.STRESS_AVERAGE to HealthMetricUnit.STRESS_SCORE,
            HealthMetricKey.STRESS_MAXIMUM to HealthMetricUnit.STRESS_SCORE,
            HealthMetricKey.STRESS_MINIMUM to HealthMetricUnit.STRESS_SCORE,
        ),
        definition(
            "vo2_max",
            "最大摄氧量",
            HealthMetricKey.VO2_MAX_AVERAGE to HealthMetricUnit.MILLILITERS_PER_KILOGRAM_PER_MINUTE,
            HealthMetricKey.VO2_MAX_MAXIMUM to HealthMetricUnit.MILLILITERS_PER_KILOGRAM_PER_MINUTE,
            HealthMetricKey.VO2_MAX_MINIMUM to HealthMetricUnit.MILLILITERS_PER_KILOGRAM_PER_MINUTE,
        ),
    )
    val byRequestKey: Map<String, MiFitnessMetricDefinition> = definitions.associateBy { it.requestKey }

    val workoutDefinition = definition(
        "sport_records",
        "运动记录",
        HealthMetricKey.WORKOUT_COUNT to HealthMetricUnit.COUNT,
    )

    fun definition(requestKey: String): MiFitnessMetricDefinition =
        byRequestKey[requestKey] ?: throw IllegalArgumentException("Unsupported Mi Fitness aggregate key")

    fun unavailableValues(
        definition: MiFitnessMetricDefinition,
        status: HealthMetricStatus,
        reasonCode: String,
    ): Map<HealthMetricKey, HealthMetricValue> {
        require(status != HealthMetricStatus.AVAILABLE && status != HealthMetricStatus.STALE)
        val provenance = provenance(definition.requestKey, null)
        return definition.outputs.mapValues { (_, unit) ->
            HealthMetricValue(null, unit, status, provenance, reasonCode)
        }
    }

    fun provenance(vendorKey: String, sourceCount: Int?): HealthMetricProvenance = HealthMetricProvenance(
        sourceId = SOURCE_ID,
        endpoint = if (vendorKey == workoutDefinition.requestKey) {
            MiFitnessProtocol.SPORT_RECORDS_PATH
        } else {
            MiFitnessProtocol.AGGREGATE_FITNESS_PATH
        },
        aggregation = if (vendorKey == workoutDefinition.requestKey) {
            VENDOR_SPORT_AGGREGATION
        } else {
            VENDOR_DAILY_AGGREGATION
        },
        vendorKey = vendorKey,
        sourceCount = sourceCount,
    )

    fun stepSeriesProvenance(pointCount: Int): HealthMetricProvenance = HealthMetricProvenance(
        sourceId = SOURCE_ID,
        endpoint = MiFitnessProtocol.FITNESS_BY_TIME_PATH,
        aggregation = VENDOR_TIME_SERIES_AGGREGATION,
        vendorKey = "steps",
        sourceCount = pointCount,
    )

    private fun definition(
        requestKey: String,
        displayName: String,
        vararg outputs: Pair<HealthMetricKey, HealthMetricUnit>,
    ) = MiFitnessMetricDefinition(requestKey, displayName, linkedMapOf(*outputs))
}

internal data class MiFitnessAggregateRecord(
    val tag: String,
    val key: String,
    val epochSeconds: Long,
    val zoneOffsetSeconds: Int?,
    val sourceCount: Int,
    val value: JSONObject,
    val canonicalFingerprint: String,
)

internal data class MiFitnessAggregatePage(
    val records: List<MiFitnessAggregateRecord>,
    val hasMore: Boolean,
    val nextKey: String?,
)

internal object MiFitnessAggregateParser {
    fun parse(rawJson: String, expectedKey: String): Result<MiFitnessAggregatePage> = try {
        Result.success(parseOrThrow(rawJson, MiFitnessMetricRegistry.definition(expectedKey)))
    } catch (_: Exception) {
        // org.json exceptions may include payload fragments. Never expose decrypted health data.
        Result.failure(IllegalArgumentException("小米运动健康日聚合响应格式无效。"))
    }

    fun metricsFor(record: MiFitnessAggregateRecord): Result<Map<HealthMetricKey, HealthMetricValue>> = try {
        val definition = MiFitnessMetricRegistry.definition(record.key)
        Result.success(parseMetricValues(definition, record))
    } catch (_: Exception) {
        Result.failure(IllegalArgumentException("小米运动健康日聚合指标格式无效。"))
    }

    fun selectDaily(
        records: List<MiFitnessAggregateRecord>,
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
    ): Result<MiFitnessAggregateRecord?> = try {
        require(startEpochSeconds < endEpochSecondsExclusive)
        require(records.all { it.epochSeconds in startEpochSeconds until endEpochSecondsExclusive })
        val distinct = records.distinctBy(MiFitnessAggregateRecord::canonicalFingerprint)
        require(distinct.size <= 1) { "Conflicting daily aggregate records" }
        Result.success(distinct.singleOrNull())
    } catch (_: Exception) {
        Result.failure(IllegalArgumentException("小米运动健康日聚合记录冲突。"))
    }

    private fun parseOrThrow(
        rawJson: String,
        definition: MiFitnessMetricDefinition,
    ): MiFitnessAggregatePage {
        require(rawJson.length <= MAX_RESPONSE_CHARS)
        val envelope = JSONObject(rawJson)
        require(successCode(exactLong(envelope.get("code"), "code")))
        val result = envelope.getJSONObject("result")
        val list = result.getJSONArray("data_list")
        require(list.length() <= MAX_RECORDS_PER_PAGE)
        val records = buildList {
            repeat(list.length()) { index ->
                val item = list.getJSONObject(index)
                val tag = item.getString("tag")
                val key = item.getString("key")
                require(tag == MiFitnessMetricRegistry.DAILY_TAG)
                require(key == definition.requestKey)
                val epochSeconds = exactLong(item.get("time"), "time")
                require(epochSeconds in 0..MAX_EPOCH_SECONDS)
                val zoneOffset = if (item.has("zone_offset") && !item.isNull("zone_offset")) {
                    exactLong(item.get("zone_offset"), "zone_offset").also {
                        require(it in MIN_ZONE_OFFSET_SECONDS..MAX_ZONE_OFFSET_SECONDS)
                    }
                } else {
                    null
                }
                val value = jsonObjectValue(item.get("value"))
                val sourceCount = sourceCount(item)
                val fingerprint = listOf(
                    tag,
                    key,
                    epochSeconds.toString(),
                    zoneOffset?.toString() ?: "absent",
                    sourceCount.toString(),
                    canonicalJson(value),
                ).joinToString("|")
                add(
                    MiFitnessAggregateRecord(
                        tag = tag,
                        key = key,
                        epochSeconds = epochSeconds,
                        zoneOffsetSeconds = zoneOffset?.toInt(),
                        sourceCount = sourceCount,
                        value = value,
                        canonicalFingerprint = fingerprint,
                    ),
                )
            }
        }
        val hasMore = result.get("has_more")
        require(hasMore is Boolean)
        val nextKey = result.optString("next_key").takeIf(String::isNotBlank)
        require(!hasMore || nextKey != null)
        return MiFitnessAggregatePage(records, hasMore, nextKey)
    }

    private fun parseMetricValues(
        definition: MiFitnessMetricDefinition,
        record: MiFitnessAggregateRecord,
    ): Map<HealthMetricKey, HealthMetricValue> {
        require(record.tag == MiFitnessMetricRegistry.DAILY_TAG)
        require(record.key == definition.requestKey)
        val values = linkedMapOf<HealthMetricKey, HealthMetricValue>()
        fun available(key: HealthMetricKey, value: Double, min: Double, max: Double) {
            require(value.isFinite() && value in min..max)
            values[key] = HealthMetricValue(
                value = value,
                unit = checkNotNull(definition.outputs[key]),
                status = HealthMetricStatus.AVAILABLE,
                provenance = MiFitnessMetricRegistry.provenance(definition.requestKey, record.sourceCount),
            )
        }
        fun requiredLong(field: String, key: HealthMetricKey, min: Long, max: Long) {
            val number = exactLong(record.value.get(field), field)
            require(number in min..max)
            available(key, number.toDouble(), min.toDouble(), max.toDouble())
        }
        fun optionalLong(field: String, key: HealthMetricKey, min: Long, max: Long) {
            if (!record.value.has(field) || record.value.isNull(field)) {
                values[key] = partial(definition, record, key)
            } else {
                requiredLong(field, key, min, max)
            }
        }
        fun requiredDouble(field: String, key: HealthMetricKey, min: Double, max: Double) {
            available(key, exactDouble(record.value.get(field), field), min, max)
        }

        when (definition.requestKey) {
            "steps" -> {
                requiredLong("steps", HealthMetricKey.STEPS, 0L, MAX_DAILY_STEPS)
                requiredLong("distance", HealthMetricKey.DISTANCE_METERS, 0L, MAX_DAILY_DISTANCE_METERS)
                requiredLong("calories", HealthMetricKey.ACTIVE_CALORIES_KCAL, 0L, MAX_DAILY_CALORIES)
            }
            "calories" -> requiredLong(
                "calories",
                HealthMetricKey.ACTIVE_CALORIES_KCAL,
                0L,
                MAX_DAILY_CALORIES,
            )
            "intensity" -> requiredLong(
                "duration",
                HealthMetricKey.ACTIVITY_DURATION_MINUTES,
                0L,
                MAX_DAILY_MINUTES,
            )
            "valid_stand" -> requiredLong("count", HealthMetricKey.VALID_STAND_COUNT, 0L, 24L)
            "sleep" -> {
                requiredLong("total_duration", HealthMetricKey.SLEEP_MINUTES, 0L, MAX_DAILY_MINUTES)
                optionalLong("sleep_deep_duration", HealthMetricKey.SLEEP_DEEP_MINUTES, 0L, MAX_DAILY_MINUTES)
                optionalLong("sleep_light_duration", HealthMetricKey.SLEEP_LIGHT_MINUTES, 0L, MAX_DAILY_MINUTES)
                optionalLong("sleep_rem_duration", HealthMetricKey.SLEEP_REM_MINUTES, 0L, MAX_DAILY_MINUTES)
                optionalLong("sleep_awake_duration", HealthMetricKey.SLEEP_AWAKE_MINUTES, 0L, MAX_DAILY_MINUTES)
                optionalLong("sleep_score", HealthMetricKey.SLEEP_SCORE, 0L, 100L)
            }
            "heart_rate" -> {
                requiredLong("avg_hr", HealthMetricKey.HEART_RATE_AVERAGE_BPM, 0L, 300L)
                requiredLong("max_hr", HealthMetricKey.HEART_RATE_MAXIMUM_BPM, 0L, 300L)
                requiredLong("min_hr", HealthMetricKey.HEART_RATE_MINIMUM_BPM, 0L, 300L)
                optionalLong("avg_rhr", HealthMetricKey.RESTING_HEART_RATE_BPM, 0L, 300L)
            }
            "spo2" -> {
                requiredDouble("avg_spo2", HealthMetricKey.OXYGEN_SATURATION_AVERAGE_PERCENT, 0.0, 100.0)
                requiredDouble("max_spo2", HealthMetricKey.OXYGEN_SATURATION_MAXIMUM_PERCENT, 0.0, 100.0)
                requiredDouble("min_spo2", HealthMetricKey.OXYGEN_SATURATION_MINIMUM_PERCENT, 0.0, 100.0)
            }
            "stress" -> {
                requiredLong("avg_stress", HealthMetricKey.STRESS_AVERAGE, 0L, 100L)
                requiredLong("max_stress", HealthMetricKey.STRESS_MAXIMUM, 0L, 100L)
                requiredLong("min_stress", HealthMetricKey.STRESS_MINIMUM, 0L, 100L)
            }
            "vo2_max" -> {
                requiredDouble("avg_vo2_max", HealthMetricKey.VO2_MAX_AVERAGE, 0.0, 100.0)
                requiredDouble("max_vo2_max", HealthMetricKey.VO2_MAX_MAXIMUM, 0.0, 100.0)
                requiredDouble("min_vo2_max", HealthMetricKey.VO2_MAX_MINIMUM, 0.0, 100.0)
            }
            else -> throw IllegalArgumentException("Unsupported Mi Fitness aggregate key")
        }
        require(values.keys == definition.outputs.keys)
        return values
    }

    private fun partial(
        definition: MiFitnessMetricDefinition,
        record: MiFitnessAggregateRecord,
        key: HealthMetricKey,
    ) = HealthMetricValue(
        value = null,
        unit = checkNotNull(definition.outputs[key]),
        status = HealthMetricStatus.PARTIAL,
        provenance = MiFitnessMetricRegistry.provenance(definition.requestKey, record.sourceCount),
        reasonCode = "field_missing",
    )

    private fun sourceCount(item: JSONObject): Int {
        if (item.has("source_sid_list") && !item.isNull("source_sid_list")) {
            val sources = item.getJSONArray("source_sid_list")
            require(sources.length() <= MAX_SOURCE_COUNT)
            repeat(sources.length()) { index -> require(sources.get(index) is String) }
            return sources.length()
        }
        return if (item.has("sid") && !item.isNull("sid")) {
            require(item.get("sid") is String)
            1
        } else {
            0
        }
    }

    private fun jsonObjectValue(value: Any): JSONObject = when (value) {
        is JSONObject -> value
        is String -> JSONObject(value)
        else -> throw IllegalArgumentException("value must be an object")
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { key ->
            JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { index ->
            canonicalJson(value.get(index))
        }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    internal fun exactLong(value: Any, field: String): Long {
        val text = when (value) {
            is Byte, is Short, is Int, is Long -> value.toString()
            is String -> value
            else -> throw IllegalArgumentException("$field must be an integer")
        }
        require(INTEGER_PATTERN.matches(text))
        return text.toLongOrNull() ?: throw IllegalArgumentException("$field is out of range")
    }

    private fun exactDouble(value: Any, field: String): Double {
        val text = when (value) {
            is Number, is String -> value.toString()
            else -> throw IllegalArgumentException("$field must be numeric")
        }
        require(DECIMAL_PATTERN.matches(text))
        return text.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: throw IllegalArgumentException("$field is out of range")
    }

    private fun successCode(code: Long): Boolean = code == 0L || code == 200L

    private const val MAX_RESPONSE_CHARS = 2_000_000
    private const val MAX_RECORDS_PER_PAGE = 1_000
    private const val MAX_SOURCE_COUNT = 1_000
    private const val MAX_DAILY_STEPS = 10_000_000L
    private const val MAX_DAILY_DISTANCE_METERS = 10_000_000L
    private const val MAX_DAILY_CALORIES = 10_000_000L
    private const val MAX_DAILY_MINUTES = 1_440L
    private const val MAX_EPOCH_SECONDS = 32_503_680_000L
    private const val MIN_ZONE_OFFSET_SECONDS = -64_800L
    private const val MAX_ZONE_OFFSET_SECONDS = 64_800L
    private val INTEGER_PATTERN = Regex("-?(0|[1-9][0-9]*)")
    private val DECIMAL_PATTERN = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")
}

internal data class MiFitnessSportRecord(
    val idDigest: String,
    val epochSeconds: Long,
    val deleted: Boolean,
)

internal data class MiFitnessSportPage(
    val records: List<MiFitnessSportRecord>,
    val hasMore: Boolean,
    val nextKey: String?,
)

internal object MiFitnessSportParser {
    fun parse(rawJson: String): Result<MiFitnessSportPage> = try {
        require(rawJson.length <= 2_000_000)
        val envelope = JSONObject(rawJson)
        val code = MiFitnessAggregateParser.exactLong(envelope.get("code"), "code")
        require(code == 0L || code == 200L)
        val result = envelope.getJSONObject("result")
        val list = result.getJSONArray("sport_records")
        require(list.length() <= 1_000)
        val records = buildList {
            repeat(list.length()) { index ->
                val item = list.getJSONObject(index)
                val sid = item.getString("sid")
                require(sid.isNotBlank() && sid.length <= 1_024)
                val time = MiFitnessAggregateParser.exactLong(item.get("time"), "time")
                require(time in 0..32_503_680_000L)
                val deleted = item.get("deleted")
                require(deleted is Boolean)
                add(MiFitnessSportRecord(digest(sid), time, deleted))
            }
        }
        val hasMore = result.get("has_more")
        require(hasMore is Boolean)
        val nextKey = result.optString("next_key").takeIf(String::isNotBlank)
        require(!hasMore || nextKey != null)
        Result.success(MiFitnessSportPage(records, hasMore, nextKey))
    } catch (_: Exception) {
        Result.failure(IllegalArgumentException("小米运动健康运动记录响应格式无效。"))
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal data class MiFitnessStepSeriesPoint(val epochSeconds: Long, val steps: Long)

internal data class MiFitnessStepSeriesPage(
    val points: List<MiFitnessStepSeriesPoint>,
    val hasMore: Boolean,
    val nextKey: String?,
)

/** Parser for get_fitness_data_by_time. These points are trends only and are never a daily total. */
internal object MiFitnessStepSeriesParser {
    fun parse(rawJson: String): Result<List<MiFitnessStepSeriesPoint>> =
        parsePage(rawJson).map(MiFitnessStepSeriesPage::points)

    fun parsePage(rawJson: String): Result<MiFitnessStepSeriesPage> = try {
        require(rawJson.length <= 2_000_000)
        val envelope = JSONObject(rawJson)
        val code = MiFitnessAggregateParser.exactLong(envelope.get("code"), "code")
        require(code == 0L || code == 200L)
        val result = envelope.getJSONObject("result")
        val list = result.getJSONArray("data_list")
        require(list.length() <= 1_000)
        val points = buildList {
            repeat(list.length()) { index ->
                val item = list.getJSONObject(index)
                if (item.has("key") && !item.isNull("key")) require(item.getString("key") == "steps")
                val time = MiFitnessAggregateParser.exactLong(item.get("time"), "time")
                require(time in 0..4_102_444_800L)
                val value = when (val raw = item.get("value")) {
                    is JSONObject -> raw
                    is String -> JSONObject(raw)
                    else -> throw IllegalArgumentException("invalid series value")
                }
                val steps = MiFitnessAggregateParser.exactLong(value.get("steps"), "steps")
                require(steps in 0..1_000_000L)
                add(MiFitnessStepSeriesPoint(time, steps))
            }
        }
        val hasMore = result.get("has_more")
        require(hasMore is Boolean)
        val nextKey = result.optString("next_key").takeIf(String::isNotBlank)
        require(!hasMore || nextKey != null)
        Result.success(MiFitnessStepSeriesPage(points, hasMore, nextKey))
    } catch (_: Exception) {
        Result.failure(IllegalArgumentException("小米运动健康步数趋势响应格式无效。"))
    }
}
