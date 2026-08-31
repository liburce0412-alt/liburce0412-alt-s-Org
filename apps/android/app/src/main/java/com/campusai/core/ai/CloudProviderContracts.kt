package com.campusai.core.ai

import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

enum class CloudAiProvider(
    val appProvider: AiProvider,
    val displayName: String,
    val defaultBaseUrl: String,
    val baseUrlConfigurable: Boolean,
    private val fastDefaultModel: String,
    private val deepDefaultModel: String,
) {
    DEEPSEEK(
        appProvider = AiProvider.DEEPSEEK,
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        baseUrlConfigurable = false,
        fastDefaultModel = "deepseek-v4-flash",
        deepDefaultModel = "deepseek-v4-pro",
    ),
    GOOGLE_GEMINI(
        appProvider = AiProvider.GOOGLE_GEMINI,
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        baseUrlConfigurable = false,
        fastDefaultModel = "gemini-3.7-flash",
        deepDefaultModel = "gemini-3.7-flash",
    ),
    CODEX(
        appProvider = AiProvider.CODEX,
        displayName = "Codex",
        defaultBaseUrl = "https://node.tail9a6cbb.ts.net/v1",
        baseUrlConfigurable = true,
        fastDefaultModel = "gpt-5.6-sol",
        deepDefaultModel = "gpt-5.6-sol",
    );

    val chatCompletionsUrl: String get() = chatCompletionsUrl(defaultBaseUrl)
    val modelsUrl: String get() = modelsUrl(defaultBaseUrl)

    fun chatCompletionsUrl(baseUrl: String): String = "${resolveBaseUrl(baseUrl)}/chat/completions"

    fun modelsUrl(baseUrl: String): String = "${resolveBaseUrl(baseUrl)}/models"

    fun resolveBaseUrl(rawBaseUrl: String): String {
        if (!baseUrlConfigurable) return defaultBaseUrl
        val candidate = rawBaseUrl.trim().trimEnd('/')
        val parsed = candidate.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Base URL 格式无效。")
        require(parsed.scheme == "https") { "Base URL 必须使用 HTTPS。" }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "Base URL 不能包含账号信息。" }
        require(parsed.query == null && parsed.fragment == null) { "Base URL 不能包含查询参数或片段。" }
        require(candidate.length <= MAX_BASE_URL_CHARS) { "Base URL 过长。" }
        return candidate
    }

    fun defaultModel(mode: AiMode): String = if (mode == AiMode.FAST) fastDefaultModel else deepDefaultModel

    fun acceptsModelId(rawModelId: String): Boolean {
        val modelId = normalizeModelId(rawModelId)
        if (!MODEL_ID_PATTERN.matches(modelId)) return false
        return when (this) {
            DEEPSEEK -> modelId.startsWith("deepseek-")
            GOOGLE_GEMINI -> modelId.startsWith("gemini-") && EXCLUDED_GEMINI_MODEL_TERMS.none(modelId::contains)
            CODEX -> true
        }
    }

    fun normalizeModelId(rawModelId: String): String = rawModelId.trim().removePrefix("models/")

    fun resolveModel(mode: AiMode, selectedModelId: String): String {
        val selected = normalizeModelId(selectedModelId)
        if (selected.isBlank()) return defaultModel(mode)
        require(acceptsModelId(selected)) { "不支持的 $displayName 模型 ID。" }
        return selected
    }

    companion object {
        fun from(provider: AiProvider): CloudAiProvider? = entries.firstOrNull { it.appProvider == provider }

        private const val MAX_BASE_URL_CHARS = 2_048
        private val MODEL_ID_PATTERN = Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]{0,126}[A-Za-z0-9])?")
        private val EXCLUDED_GEMINI_MODEL_TERMS = setOf("embedding", "imagen", "veo", "tts", "live")
    }
}

data class CloudProviderConfiguration(
    val provider: CloudAiProvider,
    val baseUrl: String,
    val selectedModelId: String,
    val hasCredential: Boolean,
    val maskedCredential: String,
)

data class CloudProviderModel(
    val id: String,
    val displayName: String = id,
)

data class CloudProviderConnection(
    val provider: CloudAiProvider,
    val selectedModelId: String,
    val models: List<CloudProviderModel>,
    val latencyMs: Long,
    val chatVerified: Boolean = false,
    val toolCallsVerified: Boolean = false,
)

/**
 * The only health shape permitted at the cloud request boundary. It deliberately
 * has no source IDs, device IDs, samples, intervals, or raw provider payloads.
 */
data class CloudDailyHealthSummary(
    val localDate: String,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val activityMinutes: Long? = null,
    val sleepMinutes: Long? = null,
    val averageHeartRateBpm: Double? = null,
    val averageOxygenSaturationPercent: Double? = null,
    val averageStressScore: Double? = null,
    val workoutCount: Long? = null,
) {
    internal fun toAllowedJson(): JSONObject = JSONObject()
        .put("localDate", localDate.takeIf(LOCAL_DATE_PATTERN::matches) ?: "unknown")
        .putNonNegative("steps", steps)
        .putNonNegative("distanceMeters", distanceMeters)
        .putNonNegative("activeCaloriesKcal", activeCaloriesKcal)
        .putNonNegative("activityMinutes", activityMinutes)
        .putNonNegative("sleepMinutes", sleepMinutes)
        .putInRange("averageHeartRateBpm", averageHeartRateBpm, 1.0, 300.0)
        .putInRange("averageOxygenSaturationPercent", averageOxygenSaturationPercent, 0.0, 100.0)
        .putInRange("averageStressScore", averageStressScore, 0.0, 100.0)
        .putNonNegative("workoutCount", workoutCount)

    private fun JSONObject.putNonNegative(name: String, value: Long?): JSONObject = apply {
        if (value != null && value >= 0L) put(name, value)
    }

    private fun JSONObject.putNonNegative(name: String, value: Double?): JSONObject = apply {
        if (value != null && value.isFinite() && value >= 0.0) put(name, value)
    }

    private fun JSONObject.putInRange(name: String, value: Double?, minimum: Double, maximum: Double): JSONObject = apply {
        if (value != null && value.isFinite() && value in minimum..maximum) put(name, value)
    }

    private companion object {
        val LOCAL_DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

sealed interface CloudHealthDisclosure {
    data object Excluded : CloudHealthDisclosure

    /** Must be constructed anew for the turn where the user explicitly opts in. */
    data class Included(val summary: CloudDailyHealthSummary) : CloudHealthDisclosure
}
