package com.campusai.core.network

import okio.BufferedSource

internal data class SseEvent(val data: String)

/** Minimal WHATWG-compatible SSE reader for provider streams. */
internal class SseEventReader(
    private val source: BufferedSource,
) {
    fun next(): SseEvent? {
        val data = mutableListOf<String>()
        var eventBytes = 0L
        while (true) {
            val line = readBoundedLine()
            if (line == null) return data.takeIf(List<String>::isNotEmpty)?.let { SseEvent(it.joinToString("\n")) }
            if (line.isEmpty()) {
                if (data.isNotEmpty()) return SseEvent(data.joinToString("\n"))
                continue
            }
            if (line.startsWith(':')) continue
            val separator = line.indexOf(':')
            val field = if (separator < 0) line else line.substring(0, separator)
            if (field != "data") continue
            var value = if (separator < 0) "" else line.substring(separator + 1)
            if (value.startsWith(' ')) value = value.substring(1)
            eventBytes += value.toByteArray(Charsets.UTF_8).size + 1L
            if (eventBytes > MAX_EVENT_BYTES) throw streamTooLarge()
            data += value
        }
    }

    private fun readBoundedLine(): String? {
        if (source.exhausted()) return null
        val newline = source.indexOf('\n'.code.toByte(), 0L, MAX_LINE_BYTES + 1L)
        if (newline >= 0L) return source.readUtf8Line()
        if (source.request(MAX_LINE_BYTES + 1L)) throw streamTooLarge()
        return source.readUtf8Line()
    }

    private fun streamTooLarge() = CloudProviderException(
        code = "provider_response_too_large",
        message = "Provider 数据流过大，已安全停止。",
        recoverable = false,
    )

    private companion object {
        const val MAX_LINE_BYTES = 256L * 1_024L
        const val MAX_EVENT_BYTES = 512L * 1_024L
    }
}
