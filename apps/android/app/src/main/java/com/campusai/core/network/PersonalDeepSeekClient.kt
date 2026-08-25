package com.campusai.core.network

import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.ai.AiSystemPolicy
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import com.campusai.core.security.PersonalDeepSeekKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PersonalDeepSeekClient(
    private val keyStore: PersonalDeepSeekKeyStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(130, TimeUnit.SECONDS)
        .build()
    private val activeCall = AtomicReference<Call?>(null)

    suspend fun stream(request: AiRequest, onEvent: suspend (AiEvent) -> Unit) = withContext(Dispatchers.IO) {
        val apiKey = keyStore.read()
        if (apiKey.isBlank()) throw PersonalDeepSeekException("personal_key_missing", "尚未保存个人 DeepSeek Key。请前往“我的 → AI 运行方式”保存后重试。")

        val model = personalDeepSeekModel(request.mode)
        val payload = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("stream_options", JSONObject().put("include_usage", true))
            put("temperature", if (request.mode == AiMode.FAST) .45 else .3)
            put("thinking", JSONObject().put("type", personalDeepSeekThinking(request.mode)))
            put("max_tokens", request.maxOutputTokens.coerceIn(1, 2048))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", AiSystemPolicy.instruction(request.structuredContextJson)))
                if (request.structuredContextJson != "{}") {
                    put(JSONObject().put("role", "system").put("content", "<private_context>${request.structuredContextJson}</private_context>"))
                }
                request.messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
        }
        val httpRequest = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(httpRequest)
        activeCall.set(call)
        currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        val started = System.currentTimeMillis()
        var inputTokens: Long? = null
        var outputTokens: Long? = null
        try {
            onEvent(AiEvent.Meta(model, AiProvider.DEEPSEEK))
            onEvent(AiEvent.Status(if (request.mode == AiMode.DEEP) "planning" else "responding", 0))
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    response.body?.close()
                    throw providerError(response.code)
                }
                val source = response.body?.source() ?: throw PersonalDeepSeekException("empty_response", "DeepSeek 没有返回数据流，请重试。")
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val chunk = PersonalDeepSeekStreamParser.parse(line.substringAfter(':').trim()) ?: continue
                    chunk.delta?.takeIf(String::isNotEmpty)?.let { onEvent(AiEvent.Delta(it)) }
                    if (chunk.inputTokens != null) inputTokens = chunk.inputTokens
                    if (chunk.outputTokens != null) outputTokens = chunk.outputTokens
                }
            }
            onEvent(AiEvent.Done(System.currentTimeMillis() - started, inputTokens, outputTokens))
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    fun cancel() {
        activeCall.getAndSet(null)?.cancel()
    }

    private fun providerError(status: Int): PersonalDeepSeekException = when (status) {
        401 -> PersonalDeepSeekException("personal_key_invalid", "个人 DeepSeek Key 无效或已被撤销。请在设置中重新保存正确的 Key。", false)
        402 -> PersonalDeepSeekException("personal_balance_empty", "个人 DeepSeek 账户余额不足。充值后重试，或切换为本地模型。", false)
        429 -> PersonalDeepSeekException("personal_rate_limited", "个人 DeepSeek 账户请求过于频繁。请稍后重试。")
        in 500..599 -> PersonalDeepSeekException("provider_unavailable", "DeepSeek 服务暂时不可用（$status），请稍后重试。")
        else -> PersonalDeepSeekException("provider_error", "个人 DeepSeek 请求失败（$status）。请检查 Key 和账户状态后重试。")
    }

    companion object {
        private const val API_URL = "https://api.deepseek.com/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class PersonalDeepSeekException(
    val code: String,
    override val message: String,
    val recoverable: Boolean = true,
) : IllegalStateException(message)

internal fun personalDeepSeekModel(mode: AiMode): String =
    if (mode == AiMode.FAST) "deepseek-v4-flash" else "deepseek-v4-pro"

internal fun personalDeepSeekThinking(mode: AiMode): String =
    if (mode == AiMode.FAST) "disabled" else "enabled"

data class PersonalDeepSeekChunk(
    val delta: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
)

object PersonalDeepSeekStreamParser {
    fun parse(raw: String): PersonalDeepSeekChunk? {
        if (raw.isBlank() || raw == "[DONE]") return null
        return runCatching {
            val packet = JSONObject(raw)
            val usage = packet.optJSONObject("usage")
            PersonalDeepSeekChunk(
                delta = packet.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")?.takeIf(String::isNotEmpty),
                inputTokens = usage?.takeIf { it.has("prompt_tokens") }?.optLong("prompt_tokens"),
                outputTokens = usage?.takeIf { it.has("completion_tokens") }?.optLong("completion_tokens"),
            )
        }.getOrNull()
    }
}
