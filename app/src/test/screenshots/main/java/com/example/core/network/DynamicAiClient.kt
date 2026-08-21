package com.example.core.network

import android.content.Context
import android.util.Log
import com.example.core.security.SecurePreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AiConfig(
    val provider: String,      // "DeepSeek", "OpenAI", "Gemini", "SiliconFlow"
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int
)

object DynamicAiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private const val PREFS_NAME = "ai_config_prefs"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_BASE_URL = "ai_base_url"
    private const val KEY_MODEL = "ai_model"
    private const val KEY_TEMP = "ai_temp"
    private const val KEY_MAX_TOKENS = "ai_max_tokens"

    fun loadConfig(context: Context): AiConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val provider = prefs.getString(KEY_PROVIDER, "DeepSeek") ?: "DeepSeek"
        val apiKey = SecurePreferences.decrypt(context, "ai_api_key")
        val defaultUrl = when (provider) {
            "DeepSeek" -> "https://api.deepseek.com"
            "OpenAI" -> "https://api.openai.com"
            "SiliconFlow" -> "https://api.siliconflow.cn"
            else -> "https://api.deepseek.com"
        }
        val baseUrl = prefs.getString(KEY_BASE_URL, defaultUrl) ?: defaultUrl
        val defaultModel = when (provider) {
            "DeepSeek" -> "deepseek-chat"
            "OpenAI" -> "gpt-4o-mini"
            "SiliconFlow" -> "deepseek-ai/DeepSeek-V3"
            else -> "deepseek-chat"
        }
        val model = prefs.getString(KEY_MODEL, defaultModel) ?: defaultModel
        val temperature = prefs.getFloat(KEY_TEMP, 0.7f)
        val maxTokens = prefs.getInt(KEY_MAX_TOKENS, 2048)

        return AiConfig(provider, apiKey, baseUrl, model, temperature, maxTokens)
    }

    fun saveConfig(context: Context, config: AiConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_MODEL, config.model)
            .putFloat(KEY_TEMP, config.temperature)
            .putInt(KEY_MAX_TOKENS, config.maxTokens)
            .apply()

        SecurePreferences.encrypt(context, "ai_api_key", config.apiKey)
    }

    suspend fun testConnection(context: Context, config: AiConfig): Result<String> = withContext(Dispatchers.IO) {
        val baseUrlSanitized = config.baseUrl.trim().removeSuffix("/")
        val url = if (baseUrlSanitized.endsWith("/chat/completions")) {
            baseUrlSanitized
        } else {
            "$baseUrlSanitized/v1/chat/completions"
        }

        if (config.apiKey.isEmpty()) {
            return@withContext Result.failure(Exception("请先在设置中填写有效的 API Key！"))
        }

        // Extremely light prompt to verify connectivity & authorization quickly
        val requestJson = """
            {
              "model": "${config.model}",
              "messages": [
                {"role": "user", "content": "Hello, answer with single word 'OK'"}
              ],
              "temperature": 0.3,
              "max_tokens": 10
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("连接成功！模型 ${config.model} 响应正常。")
                } else {
                    Result.failure(Exception("HTTP 状态码: ${response.code}\n错误响应: ${bodyStr.take(150)}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接服务器网络超时或失败！\n原因: ${e.localizedMessage}"))
        }
    }

    suspend fun generateChat(context: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val config = loadConfig(context)
        if (config.apiKey.isEmpty()) {
            return@withContext "⚠️ AI 调试提示：未激活高级 AI。请前往「首页 -> 个人头像 -> 设置中心」配置您的独立 AI 密钥（DeepSeek, OpenAI 或 SiliconFlow 均可），配置立刻生效，安全保存在您的本地 Android Keystore 加密存储中。"
        }

        val baseUrlSanitized = config.baseUrl.trim().removeSuffix("/")
        val url = if (baseUrlSanitized.endsWith("/chat/completions")) {
            baseUrlSanitized
        } else {
            "$baseUrlSanitized/v1/chat/completions"
        }

        val requestJson = """
            {
              "model": "${config.model}",
              "messages": [
                {"role": "user", "content": ${escapeJsonString(prompt)}}
              ],
              "temperature": ${config.temperature},
              "max_tokens": ${config.maxTokens}
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    parseChoiceContent(bodyStr) ?: "AI 返回格式异常，原始响应: $bodyStr"
                } else {
                    "❌ 智能生成请求失败！\n原因: HTTP ${response.code}\n错误信息: ${bodyStr.take(200)}"
                }
            }
        } catch (e: Exception) {
            "❌ 网络连接发生异常，请检查配置或联网状态！\n原因: ${e.localizedMessage}"
        }
    }

    private fun escapeJsonString(str: String): String {
        return "\"" + str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

    private fun parseChoiceContent(json: String): String? {
        try {
            // Standard OpenAI response parsing without needing full serialization libraries
            val matches = "\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex().find(json)
            if (matches != null) {
                val rawContent = matches.groupValues[1]
                // Simple escape characters unescaper
                return rawContent.replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
            }
        } catch (e: Exception) {
            Log.e("DynamicAiClient", "Failed to parse content string inline", e)
        }
        return null
    }
}
