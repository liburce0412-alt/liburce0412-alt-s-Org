package com.example.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ==========================================
// Gemini / DeepSeek API Moshi Models
// ==========================================

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<ContentJson>,
    @Json(name = "systemInstruction") val systemInstruction: ContentJson? = null,
    @Json(name = "generationConfig") val generationConfig: GenerationConfigJson? = null
)

@JsonClass(generateAdapter = true)
data class ContentJson(
    @Json(name = "parts") val parts: List<PartJson>
)

@JsonClass(generateAdapter = true)
data class PartJson(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfigJson(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<CandidateJson>? = null
)

@JsonClass(generateAdapter = true)
data class CandidateJson(
    @Json(name = "content") val content: ContentJson? = null
)

// ==========================================
// Update Checker Models
// ==========================================

@JsonClass(generateAdapter = true)
data class UpdateConfig(
    @Json(name = "versionCode") val versionCode: Int,
    @Json(name = "versionName") val versionName: String,
    @Json(name = "updateLog") val updateLog: String,
    @Json(name = "apkUrl") val apkUrl: String,
    @Json(name = "isForceUpdate") val isForceUpdate: Boolean,
    @Json(name = "isGrayUpdate") val isGrayUpdate: Boolean = false
)
