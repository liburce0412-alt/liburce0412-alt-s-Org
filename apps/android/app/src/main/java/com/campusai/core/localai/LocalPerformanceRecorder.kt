package com.campusai.core.localai

import android.content.Context
import android.os.Build
import org.json.JSONObject

data class LocalPerformanceSample(
    val recordedAt: Long,
    val device: String,
    val backend: String,
    val threads: Int,
    val loadMs: Long,
    val firstTokenMs: Double,
    val decodeTokensPerSecond: Double,
    val peakPssKb: Long,
    val outputTokens: Long,
    val elapsedMs: Long,
    val batteryTemperatureStartC: Double?,
    val batteryTemperatureEndC: Double?,
)

class LocalPerformanceRecorder(context: Context) {
    private val preferences = context.getSharedPreferences("campusai_local_ai_performance", Context.MODE_PRIVATE)

    fun record(sample: LocalPerformanceSample) {
        val json = JSONObject()
            .put("recordedAt", sample.recordedAt)
            .put("device", sample.device)
            .put("backend", sample.backend)
            .put("threads", sample.threads)
            .put("loadMs", sample.loadMs)
            .put("firstTokenMs", sample.firstTokenMs)
            .put("decodeTokensPerSecond", sample.decodeTokensPerSecond)
            .put("peakPssKb", sample.peakPssKb)
            .put("outputTokens", sample.outputTokens)
            .put("elapsedMs", sample.elapsedMs)
            .put("batteryTemperatureStartC", sample.batteryTemperatureStartC ?: JSONObject.NULL)
            .put("batteryTemperatureEndC", sample.batteryTemperatureEndC ?: JSONObject.NULL)
        preferences.edit().putString(KEY_LATEST, json.toString()).apply()
    }

    fun latestJson(): String? = preferences.getString(KEY_LATEST, null)

    companion object {
        const val KEY_LATEST = "latest_sample"
        fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
}
