package com.campusai.features.ai

/**
 * Keeps device-private turns on the local model even when the user has selected a cloud provider.
 * This is intentionally deterministic: model-generated tool choices must never decide whether
 * local images or health data are allowed to leave the device.
 */
object CaesarLocalityPolicy {
    private val healthTerms = setOf(
        "健康",
        "身体状态",
        "手环",
        "心率",
        "心跳",
        "步数",
        "睡眠",
        "血氧",
        "静息心率",
        "hrv",
        "rr",
        "压力数据",
        "运动记录",
        "训练记录",
        "health connect",
        "实时监测",
    )

    fun requiresLocal(prompt: String, hasImages: Boolean): Boolean =
        hasImages || healthTerms.any { term -> prompt.contains(term, ignoreCase = true) }
}
