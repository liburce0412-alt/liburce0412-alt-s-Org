package com.campusai.core.network

import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.ai.CloudAiProvider
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonalCloudClientTest {
    @Test
    fun `connection validation uses fixed model endpoint and exact account model`() = runTest {
        val requests = mutableListOf<okhttp3.Request>()
        val key = "AIza${"x".repeat(30)}"
        val http = clientResponding(requests) {
            """{"object":"list","data":[{"id":"gemini-3.7-flash"}]}"""
        }
        val client = PersonalCloudClient(
            provider = CloudAiProvider.GOOGLE_GEMINI,
            credential = { key },
            selectedModel = { "gemini-3.7-flash" },
            client = http,
        )

        val result = client.validateConnection()

        assertEquals("gemini-3.7-flash", result.selectedModelId)
        assertEquals(listOf("gemini-3.7-flash"), result.models.map { it.id })
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/models", requests.single().url.toString())
        assertEquals("Bearer $key", requests.single().header("Authorization"))
    }

    @Test
    fun `DeepSeek streams provider metadata text and usage through shared client`() = runTest {
        val sse = """
            data: {"choices":[{"delta":{"reasoning_content":"礼貌回应。","content":"你"}}]}

            data: {"choices":[{"delta":{"content":"好"}}]}

            data: {"choices":[],"usage":{"prompt_tokens":7,"completion_tokens":2}}

            data: [DONE]

        """.trimIndent()
        val client = PersonalCloudClient(
            provider = CloudAiProvider.DEEPSEEK,
            credential = { "unit-deepseek-key-1234567890" },
            selectedModel = { "deepseek-v4-flash" },
            client = clientResponding(mutableListOf()) { sse },
        )
        val events = mutableListOf<AiEvent>()

        client.stream(
            AiRequest(AiMode.DEEP, listOf(AiConversationMessage("user", "你好"))),
            events::add,
        )

        assertEquals(AiEvent.Meta("deepseek-v4-flash", AiProvider.DEEPSEEK), events[0])
        assertTrue(events.contains(AiEvent.Delta("你")))
        assertTrue(events.contains(AiEvent.Delta("好")))
        val done = events.last() as AiEvent.Done
        assertEquals(7L, done.inputTokens)
        assertEquals(2L, done.outputTokens)
        assertEquals("礼貌回应。", done.providerReasoningContent)
    }

    @Test
    fun `provider failures never include response bodies or credentials`() = runTest {
        val key = "unit-deepseek-key-1234567890"
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), 401, "provider-secret-debug-body")
        }.build()
        val client = PersonalCloudClient(
            provider = CloudAiProvider.DEEPSEEK,
            credential = { key },
            selectedModel = { "" },
            client = http,
        )

        val error = try {
            client.listModels()
            throw AssertionError("request must fail")
        } catch (failure: CloudProviderException) {
            failure
        }

        assertEquals("provider_key_invalid", error.code)
        assertFalse(error.message.contains(key))
        assertFalse(error.message.contains("provider-secret-debug-body"))
    }

    @Test
    fun `rate limits remain recoverable and preserve a stable diagnostic code`() = runTest {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), 429, "account-private-quota-details")
        }.build()
        val client = PersonalCloudClient(
            provider = CloudAiProvider.GOOGLE_GEMINI,
            credential = { "AIza${"x".repeat(30)}" },
            selectedModel = { "gemini-3.7-flash" },
            client = http,
        )

        val error = runCatching { client.listModels() }.exceptionOrNull() as CloudProviderException

        assertEquals("provider_rate_limited", error.code)
        assertTrue(error.recoverable)
        assertFalse(error.message.contains("account-private-quota-details"))
    }

    @Test
    fun `truncated streams and in-band provider errors fail explicitly without leaking details`() = runTest {
        val interrupted = cloudClientWithBody(
            """
                data: {"choices":[{"delta":{"content":"partial"}}]}

            """.trimIndent(),
        )
        val interruption = runCatching {
            interrupted.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))) { }
        }.exceptionOrNull() as CloudProviderException
        assertEquals("provider_stream_interrupted", interruption.code)

        val secret = "provider-internal-secret"
        val inBandError = cloudClientWithBody("data: {\"error\":{\"message\":\"$secret\"}}\n\ndata: [DONE]\n\n")
        val streamError = runCatching {
            inBandError.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))) { }
        }.exceptionOrNull() as CloudProviderException
        assertEquals("provider_stream_error", streamError.code)
        assertFalse(streamError.message.contains(secret))
    }

    @Test
    fun `cancel interrupts the active provider call`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val observedCancellation = CompletableDeferred<Unit>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            entered.complete(Unit)
            while (!chain.call().isCanceled()) Thread.sleep(1)
            observedCancellation.complete(Unit)
            throw IOException("cancelled unit call")
        }.build()
        val client = PersonalCloudClient(
            provider = CloudAiProvider.DEEPSEEK,
            credential = { "unit-deepseek-key-1234567890" },
            selectedModel = { "deepseek-v4-flash" },
            client = http,
        )
        val streaming = launch {
            runCatching {
                client.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))) { }
            }
        }

        entered.await()
        client.cancel()

        withContext(Dispatchers.Default) {
            withTimeout(2_000) {
                observedCancellation.await()
                streaming.join()
            }
        }
        assertTrue(streaming.isCompleted)
    }

    private fun cloudClientWithBody(body: String) = PersonalCloudClient(
        provider = CloudAiProvider.DEEPSEEK,
        credential = { "unit-deepseek-key-1234567890" },
        selectedModel = { "deepseek-v4-flash" },
        client = clientResponding(mutableListOf()) { body },
    )

    private fun clientResponding(
        requests: MutableList<okhttp3.Request>,
        body: () -> String,
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request()
        response(chain.request(), 200, body())
    }.build()

    private fun response(request: okhttp3.Request, status: Int, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message("unit")
        .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
        .build()
}
