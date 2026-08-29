package com.campusai.core.network

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseAndCloudOutputIntegrityTest {
    @Test
    fun `reader preserves UTF-8 across byte writes and joins multiline data`() {
        val source = Buffer()
        val bytes = ": heartbeat\r\ndata: {\"text\":\"你\"\r\ndata: ,\"tail\":\"好\"}\r\n\r\n".toByteArray()
        bytes.forEach { source.writeByte(it.toInt()) }

        assertEquals("{\"text\":\"你\"\n,\"tail\":\"好\"}", SseEventReader(source).next()?.data)
    }

    @Test
    fun `reader ignores non data fields and dispatches final event at EOF`() {
        val source = Buffer().writeUtf8("event: message\nid: 7\ndata: [DONE]")

        assertEquals("[DONE]", SseEventReader(source).next()?.data)
    }

    @Test
    fun `reader rejects oversized lines before materializing an event`() {
        val source = Buffer().writeUtf8("data: " + "x".repeat(300 * 1024))

        val failure = runCatching { SseEventReader(source).next() }.exceptionOrNull() as CloudProviderException

        assertEquals("provider_response_too_large", failure.code)
    }

    @Test
    fun `guard buffers a short natural reply until completion`() {
        val guard = CloudOutputIntegrityGuard(maxOutputTokens = 512)

        assertTrue(guard.accept("今天先完成最重要的一件事").isEmpty())
        assertEquals(listOf("今天先完成最重要的一件事"), guard.finish(outputTokens = 18))
    }

    @Test
    fun `guard rejects punctuation floods and replacement characters`() {
        val punctuation = CloudOutputIntegrityGuard(maxOutputTokens = 512)
        val failure = runCatching {
            punctuation.accept("!@#%&~".repeat(40))
        }.exceptionOrNull() as CloudProviderException
        assertEquals("provider_output_invalid", failure.code)

        val replacement = runCatching {
            CloudOutputIntegrityGuard(512).accept("一段看似正常但已经损坏的文字\uFFFD")
        }.exceptionOrNull() as CloudProviderException
        assertEquals("provider_output_invalid", replacement.code)
    }

    @Test
    fun `guard keeps valid JSON code and math`() {
        listOf(
            "{\"message\":\"可以继续\",\"count\":2}",
            "{\"\$\":\"\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\$\",\"%\":\"%%%%%%%%%%%%%%%%%%%%%%%%\"}",
            "```kotlin\nval answer = 42\nprintln(answer)\n```",
            "f(x) = x^2 + 2*x + 1",
        ).forEach { value ->
            val guard = CloudOutputIntegrityGuard(maxOutputTokens = 64)
            guard.accept(value)
            assertEquals(listOf(value), guard.finish(outputTokens = 64))
        }
    }
}
