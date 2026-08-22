package com.campusai.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface AiStreamEvent {
    data class Meta(val model: String) : AiStreamEvent
    data class Status(val stage: String, val elapsedMs: Long) : AiStreamEvent
    data class Delta(val text: String) : AiStreamEvent
    data class Done(val elapsedMs: Long) : AiStreamEvent
    data class Error(val code: String, val message: String) : AiStreamEvent
}

class AiEdgeClient {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(130, TimeUnit.SECONDS).build()

    suspend fun stream(mode: String, messages: List<Pair<String,String>>, context: JSONObject, onEvent: (AiStreamEvent) -> Unit) = withContext(Dispatchers.IO) {
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
        currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
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
                    line.startsWith("data:") -> parseEvent(eventName, line.substringAfter(':').trim())?.let(onEvent)
                    line.isBlank() -> eventName = ""
                }
            }
        }
    }

    private fun parseEvent(name: String, raw: String): AiStreamEvent? = runCatching {
        val json = JSONObject(raw)
        when (name) {
            "meta" -> AiStreamEvent.Meta(json.optString("model"))
            "status" -> AiStreamEvent.Status(json.optString("stage"), json.optLong("elapsedMs"))
            "delta" -> AiStreamEvent.Delta(json.optString("text"))
            "done" -> AiStreamEvent.Done(json.optLong("elapsedMs"))
            "error" -> AiStreamEvent.Error(json.optString("code"), json.optString("message"))
            else -> null
        }
    }.getOrNull()
}
