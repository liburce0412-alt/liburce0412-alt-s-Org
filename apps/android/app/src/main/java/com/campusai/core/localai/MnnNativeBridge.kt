package com.campusai.core.localai

import androidx.annotation.Keep

data class NativeGenerationMetrics(
    val inputTokens: Long,
    val outputTokens: Long,
    val prefillMicros: Long,
    val decodeMicros: Long,
    val firstTokenMicros: Long,
    val elapsedMs: Long,
)

@Keep
fun interface MnnTokenListener {
    /** Return true to request cancellation. */
    fun onToken(text: String): Boolean
}

@Keep
object MnnNativeBridge {
    init { System.loadLibrary("campusai_mnn") }

    private external fun nativeCreate(configPath: String, cachePath: String): Long
    private external fun nativeGenerate(pointer: Long, roles: Array<String>, contents: Array<String>, maxTokens: Int, listener: MnnTokenListener): LongArray
    private external fun nativeCancel(pointer: Long)
    private external fun nativeRelease(pointer: Long)

    fun create(configPath: String, cachePath: String): Long = nativeCreate(configPath, cachePath)
    fun generate(pointer: Long, roles: Array<String>, contents: Array<String>, maxTokens: Int, listener: MnnTokenListener): NativeGenerationMetrics {
        val values = nativeGenerate(pointer, roles, contents, maxTokens, listener)
        check(values.size == 6) { "MNN returned incomplete generation metrics" }
        return NativeGenerationMetrics(values[0], values[1], values[2], values[3], values[4], values[5])
    }
    fun cancel(pointer: Long) { if (pointer != 0L) nativeCancel(pointer) }
    fun release(pointer: Long) { if (pointer != 0L) nativeRelease(pointer) }
}
