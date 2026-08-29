package com.campusai.core.network

import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.ai.CloudAiProvider
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
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
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
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
    fun `DeepSeek model catalog uses its fixed endpoint and provider credential`() = runTest {
        val requests = mutableListOf<okhttp3.Request>()
        val key = "unit-deepseek-key-1234567890"
        val client = PersonalCloudClient(
            provider = CloudAiProvider.DEEPSEEK,
            credential = { key },
            selectedModel = { "deepseek-v4-flash" },
            client = clientResponding(requests) {
                """{"object":"list","data":[{"id":"deepseek-v4-flash"},{"id":"deepseek-v4-pro"}]}"""
            },
        )

        val models = client.listModels()

        assertEquals(listOf("deepseek-v4-flash", "deepseek-v4-pro"), models.map { it.id })
        assertEquals("https://api.deepseek.com/models", requests.single().url.toString())
        assertEquals("Bearer $key", requests.single().header("Authorization"))
    }

    @Test
    fun `connection validation rejects a selected model missing from the live catalog`() = runTest {
        val client = PersonalCloudClient(
            provider = CloudAiProvider.DEEPSEEK,
            credential = { "unit-deepseek-key-1234567890" },
            selectedModel = { "deepseek-v4-pro" },
            client = clientResponding(mutableListOf()) {
                """{"object":"list","data":[{"id":"deepseek-v4-flash"}]}"""
            },
        )

        val error = runCatching { client.validateConnection() }.exceptionOrNull() as CloudProviderException

        assertEquals("model_unavailable", error.code)
        assertFalse(error.recoverable)
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
            request = AiRequest(AiMode.DEEP, listOf(AiConversationMessage("user", "你好"))),
            onEvent = events::add,
        )

        val meta = events[0] as AiEvent.Meta
        assertEquals("deepseek-v4-flash", meta.execution.model)
        assertEquals(AiProvider.DEEPSEEK, meta.execution.provider)
        assertTrue(meta.execution.requestId.isNotBlank())
        assertEquals("你好", events.filterIsInstance<AiEvent.Delta>().joinToString("") { it.text })
        val done = events.last() as AiEvent.Done
        assertEquals(7L, done.inputTokens)
        assertEquals(2L, done.outputTokens)
        assertEquals("礼貌回应。", done.providerReasoningContent)
    }

    @Test
    fun `rejected submission lease never executes the provider request`() = runTest {
        val requests = mutableListOf<okhttp3.Request>()
        val client = PersonalCloudClient(
            provider = CloudAiProvider.DEEPSEEK,
            credential = { "unit-deepseek-key-1234567890" },
            selectedModel = { "deepseek-v4-flash" },
            client = clientResponding(requests) { "data: [DONE]\n\n" },
        )

        val failure = runCatching {
            client.stream(
                request = AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test"))),
                withSubmissionLease = { false },
                onEvent = {},
            )
        }.exceptionOrNull() as CloudProviderException

        assertEquals("request_superseded", failure.code)
        assertTrue(requests.isEmpty())
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
    fun `malformed model catalogs fail as protocol errors instead of appearing empty`() = runTest {
        val client = PersonalCloudClient(
            provider = CloudAiProvider.GOOGLE_GEMINI,
            credential = { "AIza${"x".repeat(30)}" },
            selectedModel = { "gemini-3.7-flash" },
            client = clientResponding(mutableListOf()) { "not-json" },
        )

        val error = runCatching { client.listModels() }.exceptionOrNull() as CloudProviderException

        assertEquals("provider_response_invalid", error.code)
        assertFalse(error.recoverable)
    }

    @Test
    fun `truncated streams and in-band provider errors fail explicitly without leaking details`() = runTest {
        val partialOutput = mutableListOf<String>()
        // Use non-repeating semantic text so this fixture exercises the missing SSE
        // terminator, rather than correctly tripping the repetition/garble guard first.
        val validVisiblePrefix = buildString {
            repeat(220) { offset -> append((0x4E00 + offset).toChar()) }
        }
        val interrupted = cloudClientWithBody(
            """
                data: {"choices":[{"delta":{"content":"$validVisiblePrefix"}}]}

            """.trimIndent(),
        )
        val interruption = runCatching {
            interrupted.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))) { event ->
                if (event is AiEvent.Delta) partialOutput += event.text
            }
        }.exceptionOrNull() as CloudProviderException
        assertEquals("provider_stream_interrupted", interruption.code)
        assertTrue(partialOutput.joinToString("").isNotEmpty())

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

    @Test
    fun `cancelling the coroutine immediately cancels the active provider call`() = runTest {
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
            client.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))) { }
        }

        entered.await()
        streaming.cancel()

        withContext(Dispatchers.Default) {
            withTimeout(2_000) {
                observedCancellation.await()
                streaming.join()
            }
        }
        assertTrue(streaming.isCancelled)
    }

    @Test
    fun `cancelling while SSE body is stalled cancels the call immediately`() = runTest {
        val bodyReadStarted = CompletableDeferred<Unit>()
        val observedCancellation = CompletableDeferred<Unit>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            blockingSseResponse(
                request = chain.request(),
                call = chain.call(),
                bodyReadStarted = bodyReadStarted,
                observedCancellation = observedCancellation,
            )
        }.build()
        val client = PersonalCloudClient(
            provider = CloudAiProvider.DEEPSEEK,
            credential = { "unit-deepseek-key-1234567890" },
            selectedModel = { "deepseek-v4-flash" },
            client = http,
        )
        val streaming = launch {
            client.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))) { }
        }

        bodyReadStarted.await()
        streaming.cancel()

        withContext(Dispatchers.Default) {
            withTimeout(2_000) {
                observedCancellation.await()
                streaming.join()
            }
        }
        assertTrue(streaming.isCancelled)
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

    private fun blockingSseResponse(
        request: okhttp3.Request,
        call: okhttp3.Call,
        bodyReadStarted: CompletableDeferred<Unit>,
        observedCancellation: CompletableDeferred<Unit>,
    ): Response {
        val closed = AtomicBoolean()
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                bodyReadStarted.complete(Unit)
                while (!call.isCanceled() && !closed.get()) Thread.sleep(1)
                if (call.isCanceled()) observedCancellation.complete(Unit)
                throw IOException("cancelled stalled SSE body")
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                closed.set(true)
            }
        }
        val responseBody = object : ResponseBody() {
            private val bufferedSource = source.buffer()
            override fun contentType() = "text/event-stream; charset=utf-8".toMediaType()
            override fun contentLength(): Long = -1L
            override fun source(): BufferedSource = bufferedSource
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("unit")
            .body(responseBody)
            .build()
    }
}
