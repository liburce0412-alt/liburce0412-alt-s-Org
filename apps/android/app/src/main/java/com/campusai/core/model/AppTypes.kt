package com.campusai.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MotionMode { ON, OFF }
enum class RenderQuality { AUTO, LOW, HIGH }
enum class SpectraEnvironment { ORIGINAL, OCEAN, ULTRAVIOLET, EMBER }
enum class AiMode { FAST, DEEP }

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Data<T>(val value: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Offline<T>(val value: T) : UiState<T>
    data class Error(val message: String, val canRetry: Boolean = true) : UiState<Nothing>
}

enum class SyncState { LocalOnly, Pending, Synced, Conflict, Failed }

data class AiConversationMessage(val role: String, val content: String)
data class AiReport(
    val id: String,
    val mode: AiMode,
    val title: String,
    val summary: String,
    val messagesJson: String,
    val createdAt: Long,
)
