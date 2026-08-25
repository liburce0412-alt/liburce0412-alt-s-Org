package com.campusai.core.agent

import org.json.JSONObject

data class ParsedToolCall(val name: String, val arguments: JSONObject, val rawContent: String)

object CaesarToolCallParser {
    private const val MAX_OUTPUT_CHARS = 32_768
    private val callPattern = Regex("(?s)<tool_call>\\s*<function=([a-zA-Z0-9_.-]+)>\\s*(.*?)</function>\\s*</tool_call>")
    private val parameterPattern = Regex("(?s)<parameter=([a-zA-Z0-9_.-]+)>\\s*(.*?)\\s*</parameter>")

    fun parse(output: String): ParsedToolCall? {
        if (output.length > MAX_OUTPUT_CHARS) return null
        val call = callPattern.matchEntire(output.trim()) ?: return null
        val name = call.groupValues[1]
        if (!name.matches(Regex("[a-z][a-z0-9_.]{2,80}"))) return null
        val arguments = JSONObject()
        val body = call.groupValues[2]
        var consumedUntil = 0
        parameterPattern.findAll(body).forEach { match ->
            if (body.substring(consumedUntil, match.range.first).isNotBlank()) return null
            val parameterName = match.groupValues[1]
            if (arguments.has(parameterName)) return null
            val rawValue = match.groupValues[2].trim()
            if (TRANSPORT_MARKERS.any(rawValue::contains)) return null
            arguments.put(parameterName, parseValue(rawValue) ?: return null)
            consumedUntil = match.range.last + 1
        }
        if (body.substring(consumedUntil).isNotBlank()) return null
        return ParsedToolCall(name, arguments, call.value)
    }

    private fun parseValue(value: String): Any? = when {
        value == "null" -> JSONObject.NULL
        value.startsWith("{") -> runCatching { JSONObject(value) }.getOrNull()
        value.startsWith("[") -> runCatching { org.json.JSONArray(value) }.getOrNull()
        value == "true" || value == "false" -> value.toBoolean()
        value.matches(INTEGER) -> value.toLongOrNull()
        value.matches(NUMBER) -> value.toDoubleOrNull()?.takeIf(Double::isFinite)
        else -> value
    }

    private val INTEGER = Regex("-?(?:0|[1-9]\\d*)")
    private val NUMBER = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
    private val TRANSPORT_MARKERS = listOf(
        "<tool_call", "</tool_call", "<function=", "</function", "<parameter=", "<think", "</think", "<final", "</final",
    )
}
