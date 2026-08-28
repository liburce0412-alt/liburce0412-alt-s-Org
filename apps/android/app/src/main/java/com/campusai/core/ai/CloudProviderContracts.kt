package com.campusai.core.ai

import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import org.json.JSONObject

enum class CloudAiProvider(
    val appProvider: AiProvider,
    val displayName: String,
    val chatCompletionsUrl: String,
    val modelsUrl: String,
    private val fastDefaultModel: String,
    private val deepDefaultModel: String,
) {
    DEEPSEEK(
        appProvider = AiProvider.DEEPSEEK,
        displayName = "DeepSeek",
        chatCompletionsUrl = "https://api.deepseek.com/chat/completions",
        modelsUrl = "https://api.deepseek.com/models",
        fastDefaultModel = "deepseek-v4-flash",
        deepDefaultModel = "deepseek-v4-pro",
    ),
    GOOGLE_GEMINI(
        appProvider = AiProvider.GOOGLE_GEMINI,
        displayName = "Google Gemini",
        chatCompletionsUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        modelsUrl = "https://generativelanguage.googleapis.com/v1beta/openai/models",
        fastDefaultModel = "gemini-3.7-flash",
        deepDefaultModel = "gemini-3.7-flash",
    );

    fun defaultModel(mode: AiMode): String = if (mode == AiMode.FAST) fastDefaultModel else deepDefaultModel

    fun acceptsModelId(rawModelId: String): Boolean {
        val modelId = normalizeModelId(rawModelId)
        if (!MODEL_ID_PATTERN.matches(modelId)) return false
        return when (this) {
            DEEPSEEK -> modelId.startsWith("deepseek-")
            GOOGLE_GEMINI -> modelId.startsWith("gemini-") && EXCLUDED_GEMINI_MODEL_TERMS.none(modelId::contains)
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

        private val MODEL_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{1,126}[A-Za-z0-9]")
        private val EXCLUDED_GEMINI_MODEL_TERMS = setOf("embedding", "imagen", "veo", "tts", "live")
    }
}

data class CloudProviderConfiguration(
    val provider: CloudAiProvider,
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
