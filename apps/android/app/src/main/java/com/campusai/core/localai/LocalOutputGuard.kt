package com.campusai.core.localai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Only a unique top-level <final> envelope may stream. Plain text and all
 * structured output stay buffered so an inner marker can never open a stream.
 */
internal class LocalOutputGuard {
    private enum class Phase {
        PROBING,
        THINK_BUFFER,
        TOOL_BUFFER,
        PLAIN_BUFFER,
        FINAL,
        AFTER_FINAL,
        CLOSED,
        BLOCKED,
    }

    private enum class FinalMode { UNDECIDED, STREAMING, BUFFERED }

    private val pending = StringBuilder()
    private val deferredFinal = StringBuilder()
    private val integrityProbe = StringBuilder()
    private var phase = Phase.PROBING
    private var finalMode = FinalMode.UNDECIDED
    var hasVisibleOutput: Boolean = false
        private set
    val blocked: Boolean get() = phase == Phase.BLOCKED

    fun accept(token: String): List<String> {
        if (token.isEmpty() || phase == Phase.CLOSED || phase == Phase.BLOCKED) return emptyList()
        if (containsInvalidCodePoints(token)) return block()
        pending.append(token)
        return when (phase) {
            Phase.PROBING -> advanceProbe()
            Phase.THINK_BUFFER -> advanceThinkBuffer()
            Phase.TOOL_BUFFER, Phase.PLAIN_BUFFER -> emptyList()
            Phase.FINAL -> drainFinal()
            Phase.AFTER_FINAL -> validateFinalSuffix()
            Phase.CLOSED, Phase.BLOCKED -> emptyList()
        }
    }

    fun finish(): List<String> = when (phase) {
        Phase.CLOSED, Phase.BLOCKED -> {
            clearBuffers()
            emptyList()
        }
        Phase.PROBING, Phase.PLAIN_BUFFER -> finishBufferedPlain()
        Phase.THINK_BUFFER, Phase.TOOL_BUFFER -> block()
        Phase.FINAL -> finishOpenFinal()
        Phase.AFTER_FINAL -> finishClosedFinal()
    }

    private fun advanceProbe(): List<String> {
        val source = pending.toString()
        val leadingWhitespace = source.indexOfFirst { !it.isWhitespace() }
        if (leadingWhitespace < 0) return emptyList()
        val normalized = source.substring(leadingWhitespace).lowercase()
        return when {
            normalized.startsWith(FINAL_OPEN) -> {
                pending.delete(0, leadingWhitespace + FINAL_OPEN.length)
                phase = Phase.FINAL
                drainFinal()
            }
            normalized.startsWith(THINK_OPEN) -> {
                phase = Phase.THINK_BUFFER
                advanceThinkBuffer()
            }
            normalized.startsWith(THINK_CLOSE) -> {
                pending.delete(0, leadingWhitespace + THINK_CLOSE.length)
                phase = Phase.PROBING
                advanceProbe()
            }
            normalized.startsWith(TOOL_OPEN) -> {
                phase = Phase.TOOL_BUFFER
                emptyList()
            }
            TOP_LEVEL_OPENINGS.any { it.startsWith(normalized) } -> emptyList()
            else -> {
                phase = Phase.PLAIN_BUFFER
                emptyList()
            }
        }
    }

    private fun advanceThinkBuffer(): List<String> {
        val normalized = pending.toString().lowercase()
        val close = normalized.indexOf(THINK_CLOSE)
        if (close < 0) return emptyList()
        pending.delete(0, close + THINK_CLOSE.length)
        phase = Phase.PROBING
        return advanceProbe()
    }

    private fun drainFinal(): List<String> {
        val result = mutableListOf<String>()
        while (phase == Phase.FINAL && pending.isNotEmpty()) {
            val source = pending.toString()
            if (containsPrivateMaterial(source) || containsPrivateJsonKey(source)) {
                phase = Phase.BLOCKED
                clearBuffers()
                break
            }
            if (finalMode == FinalMode.UNDECIDED) {
                val first = source.indexOfFirst { !it.isWhitespace() }
                if (first < 0) break
                finalMode = if (startsBufferedFinal(source.substring(first))) FinalMode.BUFFERED else FinalMode.STREAMING
            }

            val close = pending.indexOf(FINAL_CLOSE, ignoreCase = true)
            if (finalMode == FinalMode.BUFFERED) {
                if (close < 0) break
                deferredFinal.append(pending.substring(0, close))
                pending.delete(0, close + FINAL_CLOSE.length)
                phase = Phase.AFTER_FINAL
                result += validateFinalSuffix()
                break
            }

            val structuredAt = structuredTailIndex(source).takeIf { it >= 0 && (close < 0 || it < close) }
            if (structuredAt != null) {
                emitVisible(source.substring(0, structuredAt), result)
                pending.delete(0, structuredAt)
                finalMode = FinalMode.BUFFERED
                continue
            }
            if (close >= 0) {
                emitVisible(pending.substring(0, close), result)
                pending.delete(0, close + FINAL_CLOSE.length)
                phase = Phase.AFTER_FINAL
                result += validateFinalSuffix()
                break
            }

            val boundary = source.indexOfFirst { it in SENTENCE_BOUNDARIES }
            val take = when {
                boundary >= 0 -> boundary + 1
                pending.length > STREAM_HOLD_CHARS -> pending.length - MARKER_GUARD_CHARS
                else -> break
            }
            emitVisible(pending.substring(0, take), result)
            pending.delete(0, take)
        }
        return result
    }

    private fun validateFinalSuffix(): List<String> {
        if (pending.any { !it.isWhitespace() }) {
            phase = Phase.BLOCKED
            clearBuffers()
        } else {
            pending.clear()
        }
        return emptyList()
    }

    private fun finishClosedFinal(): List<String> {
        if (pending.any { !it.isWhitespace() }) return block()
        pending.clear()
        if (deferredFinal.isEmpty()) {
            phase = Phase.CLOSED
            return emptyList()
        }
        val candidate = deferredFinal.toString()
        deferredFinal.clear()
        if (containsPrivateMaterial(candidate) || containsPrivateJsonKey(candidate)) return block()
        val visible = sanitize(candidate)
        if (visible.isBlank()) {
            phase = Phase.CLOSED
            return emptyList()
        }
        val result = mutableListOf<String>()
        emitVisible(visible, result)
        if (blocked) return emptyList()
        phase = Phase.CLOSED
        return result
    }

    private fun finishOpenFinal(): List<String> {
        val candidate = deferredFinal.append(pending).toString()
        clearBuffers()
        if (candidate.isBlank()) {
            if (!hasVisibleOutput) return block()
            phase = Phase.CLOSED
            return emptyList()
        }
        if (containsPrivateMaterial(candidate) || containsPrivateJsonKey(candidate)) {
            return block()
        }
        val visible = sanitize(candidate)
        if (visible.isBlank()) return block()
        val result = mutableListOf<String>()
        emitVisible(visible, result)
        if (blocked) return emptyList()
        phase = Phase.CLOSED
        return result
    }

    private fun finishBufferedPlain(): List<String> {
        var candidate = pending.toString().trim()
        pending.clear()
        if (candidate.isEmpty()) return block()
        candidate = COMPLETE_THINK_BLOCK.replace(candidate, "").trim()
        if (
            candidate.isEmpty() ||
            candidate.trimStart().startsWith("<") ||
            containsPrivateMaterial(candidate) ||
            containsPrivateJsonKey(candidate)
        ) {
            return block()
        }
        val visible = sanitize(candidate).trim()
        if (visible.isEmpty()) return block()
        val result = mutableListOf<String>()
        emitVisible(visible, result)
        if (blocked) return emptyList()
        phase = Phase.CLOSED
        return result
    }

    private fun startsBufferedFinal(value: String): Boolean =
        value.startsWith("{") || value.startsWith("[") || value.startsWith(CODE_FENCE)

    private fun structuredTailIndex(value: String): Int {
        val json = value.indexOfFirst { it == '{' || it == '[' }
        val code = value.indexOf(CODE_FENCE)
        return listOf(json, code).filter { it >= 0 }.minOrNull() ?: -1
    }

    private fun containsPrivateMaterial(value: String): Boolean {
        val normalized = value.lowercase()
        return PRIVATE_MARKERS.any(normalized::contains)
    }

    private fun containsPrivateJsonKey(value: String): Boolean {
        val normalized = value.lowercase()
        return PRIVATE_JSON_KEYS.any(normalized::contains)
    }

    private fun emitVisible(value: String, target: MutableList<String>) {
        val visible = sanitize(value)
        if (visible.isEmpty()) return
        integrityProbe.append(visible)
        val candidate = integrityProbe.toString()
        if (
            containsInvalidCodePoints(candidate) ||
            (!isStructuredOutput(candidate) && (looksLikeAsciiSymbolNoise(candidate) || looksGarbled(candidate)))
        ) {
            target.clear()
            block()
            return
        }
        hasVisibleOutput = true
        target += visible
    }

    private fun containsInvalidCodePoints(value: String): Boolean = value.any { character ->
        character == '\uFFFD' ||
            (character.code in 0..31 && character !in "\n\r\t") ||
            character.code == 127
    }

    private fun looksLikeAsciiSymbolNoise(value: String): Boolean {
        val sample = value.filterNot(Char::isWhitespace)
        if (sample.length < MIN_INTEGRITY_SAMPLE_CHARS) return false
        val semanticRatio = sample.count(Char::isLetter).toDouble() / sample.length
        val noisyRatio = sample.count { it in ASCII_NOISE }.toDouble() / sample.length
        return semanticRatio < .10 && noisyRatio >= .18
    }

    private fun looksGarbled(value: String): Boolean {
        val sample = value.filterNot(Char::isWhitespace)
        if (sample.length < MIN_INTEGRITY_SAMPLE_CHARS) return false
        val semantic = sample.count { it.isLetter() || it.code in CJK_RANGE }
        val punctuation = sample.count(::isPunctuation)
        val semanticRatio = semantic.toDouble() / sample.length
        val punctuationRatio = punctuation.toDouble() / sample.length
        return REPEATED_CHARACTER.containsMatchIn(sample) ||
            REPEATED_FRAGMENT.containsMatchIn(sample) ||
            (punctuationRatio >= PUNCTUATION_RATIO && semanticRatio <= MAX_SEMANTIC_RATIO)
    }

    private fun isStructuredOutput(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.startsWith(CODE_FENCE) && trimmed.endsWith(CODE_FENCE)) return true
        if (
            trimmed.startsWith('{') && trimmed.endsWith('}') &&
            runCatching { JSONObject(trimmed) }.isSuccess
        ) return true
        if (
            trimmed.startsWith('[') && trimmed.endsWith(']') &&
            runCatching { JSONArray(trimmed) }.isSuccess
        ) return true
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

    private fun block(): List<String> {
        phase = Phase.BLOCKED
        clearBuffers()
        return emptyList()
    }

    private fun clearBuffers() {
        pending.clear()
        deferredFinal.clear()
        integrityProbe.clear()
    }

    private fun sanitize(value: String): String = value
        .replace("**", "")
        .replace("__", "")
        .replace(Regex("(?m)^\\s*#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")

    private fun chunkForUi(value: String): List<String> = value
        .split(Regex("(?<=[。！？!?\\n])"))
        .filter(String::isNotEmpty)

    internal companion object {
        private const val STREAM_HOLD_CHARS = 160
        internal const val MARKER_GUARD_CHARS = 64
        private const val FINAL_OPEN = "<final>"
        private const val FINAL_CLOSE = "</final>"
        private const val THINK_OPEN = "<think>"
        private const val THINK_CLOSE = "</think>"
        private const val TOOL_OPEN = "<tool_call>"
        private const val CODE_FENCE = "```"
        private const val MIN_INTEGRITY_SAMPLE_CHARS = 48
        private val CJK_RANGE = 0x3400..0x9FFF
        private const val PUNCTUATION_RATIO = .45
        private const val MAX_SEMANTIC_RATIO = .24
        private val REPEATED_CHARACTER = Regex("(.)\\1{15,}")
        private val REPEATED_FRAGMENT = Regex("(.{2,12})\\1{6,}")
        private val MATH_MARKER = Regex("(?:=|\\\\(?:frac|sqrt|sum|int)|[∑√∫])")
        private val MATH_ALLOWED = Regex("[\\p{L}\\p{N}\\s=+\\-*/^_(){}\\[\\].,:%\\\\∑√∫]+")
        private val ASCII_NOISE = setOf('#', '$', '%', '&', '!', '"', '\'', '@', '`', '~')
        private val TOP_LEVEL_OPENINGS = listOf(FINAL_OPEN, THINK_OPEN, THINK_CLOSE, TOOL_OPEN)
        private val SENTENCE_BOUNDARIES = setOf('。', '！', '？', '，', '；', '：', '!', '?', ',', ';', ':', '\n')
        private val COMPLETE_THINK_BLOCK = Regex("(?is)<think>.*?</think>")
        private val PRIVATE_MARKERS = listOf(
            "thinking process", "analyze the request", "analysis of the request", "input data:",
            "system prompt", "system message", "role: campusai", "campusai local quick assistant",
            "constraint 1", "kotlin/room/sql", THINK_OPEN, THINK_CLOSE, "思考过程", "分析请求", "内部提示", "结构化数据：",
            "<tool_call", "</tool_call", "<function=", "<parameter=", "<final",
        )
        private val PRIVATE_JSON_KEYS = listOf(
            "\"learningfacts\"", "\"schedulefacts\"", "\"timeparsefacts\"",
            "\"goalratebasispoints\"", "\"anchortime\"", "\"sourcetext\"",
        )
        internal val longestControlMarkerChars: Int = (
            TOP_LEVEL_OPENINGS + listOf(FINAL_CLOSE, THINK_CLOSE, CODE_FENCE) + PRIVATE_MARKERS + PRIVATE_JSON_KEYS
        ).maxOf(String::length)
    }
}
