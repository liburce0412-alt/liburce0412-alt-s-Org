package com.campusai.core.network

import com.campusai.core.ai.AiEvent
import com.campusai.core.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AiEdgeClient {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(130, TimeUnit.SECONDS).build()
    private val activeCall = AtomicReference<Call?>(null)

    suspend fun stream(mode: String, messages: List<Pair<String,String>>, context: JSONObject, onEvent: suspend (AiEvent) -> Unit) = withContext(Dispatchers.IO) {
        if (!SupabaseClient.isConfigured()) error("Supabase 尚未配置，AI 请求不会在客户端降级到不安全的直连模式。")
        if (SupabaseClient.userJwt.isBlank()) error("请先登录，再使用 AI 洞察。")
        val payload = JSONObject().apply {
            put("mode", mode)
            put("messages", JSONArray().apply { messages.forEach { (role, content) -> put(JSONObject().put("role",role).put("content",content)) } })
            put("context", context)
        }
        val request = Request.Builder()
            .url("${SupabaseClient.supabaseUrl}/functions/v1/ai-chat")
            .header("apikey", SupabaseClient.supabaseAnonKey)
            .header("Authorization", "Bearer ${SupabaseClient.userJwt}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val call = client.newCall(request)
        activeCall.set(call)
        currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    val message = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrElse { "AI 请求失败（${response.code}），请稍后重试。" }
                    error(message)
                }
                val source = response.body?.source() ?: error("AI 没有返回数据流。")
                var eventName = ""
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                        line.startsWith("data:") -> AiSseParser.parse(eventName, line.substringAfter(':').trim())?.let { onEvent(it) }
                        line.isBlank() -> eventName = ""
                    }
                }
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    fun cancel() {
        activeCall.getAndSet(null)?.cancel()
    }
}

object AiSseParser {
    fun parse(name: String, raw: String): AiEvent? = runCatching {
        val json = JSONObject(raw)
        when (name) {
            "meta" -> AiEvent.Meta(json.optString("model"), AiProvider.DEEPSEEK)
            "status" -> AiEvent.Status(json.optString("stage"), json.optLong("elapsedMs"))
            "delta" -> AiEvent.Delta(json.optString("text"))
            "done" -> json.optJSONObject("usage").let { usage ->
                AiEvent.Done(
                    elapsedMs = json.optLong("elapsedMs"),
                    inputTokens = usage?.optLong("inputTokens"),
                    outputTokens = usage?.optLong("outputTokens"),
                )
            }
            "error" -> AiEvent.Error(json.optString("code"), json.optString("message"))
            else -> null
        }
    }.getOrNull()
}
