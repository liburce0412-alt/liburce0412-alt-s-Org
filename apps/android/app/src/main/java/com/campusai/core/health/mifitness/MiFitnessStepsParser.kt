package com.campusai.core.health.mifitness

import org.json.JSONObject
import java.lang.Math.addExact

data class MiFitnessStepRecord(
    val epochSeconds: Long,
    val steps: Long,
)

data class MiFitnessStepsPage(
    val records: List<MiFitnessStepRecord>,
    val hasMore: Boolean,
    val nextKey: String?,
)

data class MiFitnessStepsAggregate(
    val steps: Long,
    val recordCount: Int,
    val latestRecordEpochSeconds: Long?,
    /** Open-source behavior supports summing buckets, but the vendor schema remains undocumented. */
    val aggregationProvisional: Boolean = true,
)

object MiFitnessStepsParser {
    fun parse(rawJson: String): Result<MiFitnessStepsPage> = try {
        Result.success(parseOrThrow(rawJson))
    } catch (_: Exception) {
        // org.json errors can quote source fragments; do not let decrypted health payloads escape.
        Result.failure(IllegalArgumentException("小米运动健康步数响应格式无效。"))
    }

    private fun parseOrThrow(rawJson: String): MiFitnessStepsPage {
        require(rawJson.length <= MAX_RESPONSE_CHARS) { "小米运动健康响应过大。" }
        val envelope = JSONObject(rawJson)
        val code = exactLong(envelope.get("code"), "code")
        require(code == 0L || code == 200L) { "小米运动健康返回失败状态。" }
        val result = envelope.getJSONObject("result")
        val list = result.getJSONArray("data_list")
        require(list.length() <= MAX_RECORDS_PER_PAGE) { "单页步数记录过多。" }
        val records = buildList {
            repeat(list.length()) { index ->
                val item = list.getJSONObject(index)
                if (item.getString("key") != "steps") return@repeat
                val epochSeconds = exactLong(item.get("time"), "time")
                require(epochSeconds in 0..MAX_EPOCH_SECONDS) { "步数记录时间无效。" }
                val value = when (val rawValue = item.get("value")) {
                    is JSONObject -> rawValue
                    is String -> JSONObject(rawValue)
                    else -> throw IllegalArgumentException("步数 value 格式无效。")
                }
                val steps = exactLong(value.get("steps"), "steps")
                require(steps in 0..MAX_STEPS_PER_RECORD) { "单条步数记录超出范围。" }
                add(MiFitnessStepRecord(epochSeconds, steps))
            }
        }
        val hasMoreValue = result.get("has_more")
        require(hasMoreValue is Boolean) { "has_more 必须是布尔值。" }
        val hasMore = hasMoreValue
        val nextKey = result.optString("next_key").takeIf(String::isNotBlank)
        require(!hasMore || nextKey != null) { "分页响应缺少 next_key。" }
        return MiFitnessStepsPage(records, hasMore, nextKey)
    }

    private fun exactLong(value: Any, field: String): Long {
        val text = when (value) {
            is Byte, is Short, is Int, is Long -> value.toString()
            is String -> value
            else -> throw IllegalArgumentException("$field 必须是整数。")
        }
        require(INTEGER_PATTERN.matches(text)) { "$field 必须是整数。" }
        return text.toLongOrNull() ?: throw IllegalArgumentException("$field 超出整数范围。")
    }

    private const val MAX_RESPONSE_CHARS = 2_000_000
    private const val MAX_RECORDS_PER_PAGE = 1_000
    private const val MAX_STEPS_PER_RECORD = 1_000_000L
    private const val MAX_EPOCH_SECONDS = 32_503_680_000L
    private val INTEGER_PATTERN = Regex("-?(0|[1-9][0-9]*)")
}

/**
 * Provisional v1 aggregation: each parsed record is treated as an incremental bucket and summed.
 * Callers must surface reduced confidence until a user comparison confirms the account's behavior.
 */
object MiFitnessStepsAggregator {
    fun sumIncremental(records: Iterable<MiFitnessStepRecord>): Result<MiFitnessStepsAggregate> = runCatching {
        var total = 0L
        var count = 0
        var latest: Long? = null
        records.forEach { record ->
            require(record.epochSeconds in 0..MAX_EPOCH_SECONDS) { "步数记录时间无效。" }
            require(record.steps in 0..MAX_STEPS_PER_RECORD) { "单条步数记录超出范围。" }
            require(count < MAX_RECORDS_PER_REFRESH) { "本次步数记录过多。" }
            total = try {
                addExact(total, record.steps)
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("步数合计溢出。")
            }
            require(total <= MAX_TOTAL_STEPS) { "步数合计超出范围。" }
            count += 1
            latest = maxOf(latest ?: record.epochSeconds, record.epochSeconds)
        }
        MiFitnessStepsAggregate(total, count, latest)
    }

    private const val MAX_RECORDS_PER_REFRESH = 10_000
    private const val MAX_STEPS_PER_RECORD = 1_000_000L
    private const val MAX_TOTAL_STEPS = 10_000_000L
    private const val MAX_EPOCH_SECONDS = 32_503_680_000L
}
