package com.campusai.core.network

import org.json.JSONArray
import org.json.JSONObject

/** Holds the beginning of a cloud reply until it looks safe to render and persist. */
internal class CloudOutputIntegrityGuard(
    private val maxOutputTokens: Int,
    private val initialBufferChars: Int = INITIAL_BUFFER_CHARS,
) {
    private val content = StringBuilder()
    private var released = false

    fun accept(delta: String): List<String> {
        if (delta.isEmpty()) return emptyList()
        content.append(delta)
        rejectIfInvalid(nearLimit = false, complete = false)
        if (released) return listOf(delta)
        if (content.length < initialBufferChars) return emptyList()
        released = true
        return listOf(content.toString())
    }

    fun finish(outputTokens: Long?): List<String> {
        val nearLimit = outputTokens != null && outputTokens >= (maxOutputTokens * NEAR_LIMIT_RATIO).toLong()
        rejectIfInvalid(nearLimit, complete = true)
        if (released || content.isEmpty()) return emptyList()
        released = true
        return listOf(content.toString())
    }

    private fun rejectIfInvalid(nearLimit: Boolean, complete: Boolean) {
        val value = content.toString()
        val tail = value.takeLast(TAIL_WINDOW_CHARS)
        val structured = isStructuredContent(value, complete)
        if (
            containsInvalidCodePoints(value) ||
            (!structured && (
                looksLikeAsciiSymbolNoise(value) ||
                    looksGarbled(value, nearLimit) ||
                    looksGarbled(tail, nearLimit)
                ))
        ) {
            throw CloudProviderException(
                code = "provider_output_invalid",
                message = "模型返回了无法可靠解码的内容，已清除本次回复，请重试。",
            )
        }
    }

    private fun containsInvalidCodePoints(value: String): Boolean = value.any { character ->
        character == '\uFFFD' || (character.code in 0..31 && character !in "\n\r\t") || character.code == 127
    }

    private fun looksGarbled(value: String, nearLimit: Boolean): Boolean {
        val sample = value.filterNot(Char::isWhitespace)
        if (sample.length < MIN_SAMPLE_CHARS) return false
        val semantic = sample.count { it.isLetter() || it.code in CJK_RANGE }
        val punctuation = sample.count(::isPunctuation)
        val semanticRatio = semantic.toDouble() / sample.length
        val punctuationRatio = punctuation.toDouble() / sample.length
        val repeated = REPEATED_CHARACTER.containsMatchIn(sample) || REPEATED_FRAGMENT.containsMatchIn(sample)
        val symbolFlood = punctuationRatio >= PUNCTUATION_RATIO && semanticRatio <= MAX_SEMANTIC_RATIO
        val nearLimitNoise = nearLimit && punctuationRatio >= NEAR_LIMIT_PUNCTUATION_RATIO && semanticRatio <= NEAR_LIMIT_SEMANTIC_RATIO
        return repeated || symbolFlood || nearLimitNoise
    }

    private fun looksLikeAsciiSymbolNoise(value: String): Boolean {
        val sample = value.filterNot(Char::isWhitespace)
        if (sample.length < MIN_SAMPLE_CHARS) return false
        val semanticRatio = sample.count(Char::isLetter).toDouble() / sample.length
        val noisyRatio = sample.count { it in ASCII_NOISE }.toDouble() / sample.length
        return semanticRatio < 0.10 && noisyRatio >= 0.18
    }

    private fun isStructuredContent(value: String, complete: Boolean): Boolean {
        val trimmed = value.trim()
        if (trimmed.startsWith("```") && trimmed.count { it == '\n' } >= 2) return true
        val completeObject = trimmed.startsWith('{') &&
            trimmed.endsWith('}') &&
            runCatching { JSONObject(trimmed) }.isSuccess
        val completeArray = trimmed.startsWith('[') &&
            trimmed.endsWith(']') &&
            runCatching { JSONArray(trimmed) }.isSuccess
        if (completeObject || completeArray) return true
        if (!complete && (trimmed.startsWith('{') || trimmed.startsWith('['))) return true
        return MATH_MARKER.containsMatchIn(trimmed) && MATH_ALLOWED.matches(trimmed)
    }

    private fun isPunctuation(character: Char): Boolean = when (Character.getType(character)) {
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt(), -> true
        else -> false
    }

    private companion object {
        const val INITIAL_BUFFER_CHARS = 192
        const val TAIL_WINDOW_CHARS = 320
        const val MIN_SAMPLE_CHARS = 48
        val CJK_RANGE = 0x3400..0x9FFF
        const val PUNCTUATION_RATIO = 0.45
        const val MAX_SEMANTIC_RATIO = 0.24
        const val NEAR_LIMIT_PUNCTUATION_RATIO = 0.40
        const val NEAR_LIMIT_SEMANTIC_RATIO = 0.32
        const val NEAR_LIMIT_RATIO = 0.90
        val REPEATED_CHARACTER = Regex("(.)\\1{15,}")
        val REPEATED_FRAGMENT = Regex("(.{2,12})\\1{6,}")
        val MATH_MARKER = Regex("(?:=|\\\\(?:frac|sqrt|sum|int)|[∑√∫])")
        val MATH_ALLOWED = Regex("[\\p{L}\\p{N}\\s=+\\-*/^_(){}\\[\\].,:%\\\\∑√∫]+")
        val ASCII_NOISE = setOf('#', '$', '%', '&', '!', '"', '\'', '@', '`', '~')
    }
}
