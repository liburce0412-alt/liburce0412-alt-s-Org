package com.campusai.core.localai

import android.content.Context
import com.campusai.core.model.LocalModelState

enum class LocalModelMode(val modelId: String) {
    QUALITY(LocalModelManifest.DEFAULT_MODEL_ID),
    FAST("qwen3.5-2b-mnn"),
}

data class LocalModelSelection(
    val mode: LocalModelMode,
    val manifest: LocalModelManifest,
    val state: LocalModelState,
)

data class LocalModelRuntime(
    val selection: LocalModelSelection,
    val storage: LocalModelStorage,
)

class LocalModelModeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): LocalModelMode = runCatching {
        LocalModelMode.valueOf(preferences.getString(KEY_MODE, null).orEmpty())
    }.getOrDefault(LocalModelMode.QUALITY)

    fun write(mode: LocalModelMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES = "campusai_local_model_mode"
        const val KEY_MODE = "selected_mode"
    }
}
