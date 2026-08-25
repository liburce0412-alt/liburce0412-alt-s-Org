package com.campusai.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MotionMode { ON, OFF }
enum class RenderQuality { AUTO, LOW, HIGH }
enum class SpectraEnvironment { ORIGINAL, OCEAN, ULTRAVIOLET, EMBER }
enum class AiMode { FAST, DEEP }
enum class AiProvider { AUTO, DEEPSEEK, LOCAL }

sealed interface LocalModelState {
    data object NotDownloaded : LocalModelState
    data object Checking : LocalModelState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : LocalModelState
    data class Paused(val downloadedBytes: Long, val totalBytes: Long) : LocalModelState
    data object Verifying : LocalModelState
    data object Ready : LocalModelState
    data object Loading : LocalModelState
    data class Error(val code: String, val recoverable: Boolean, val message: String) : LocalModelState
    data class Incompatible(val reason: String) : LocalModelState
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Data<T>(val value: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Offline<T>(val value: T) : UiState<T>
    data class Error(val message: String, val canRetry: Boolean = true) : UiState<Nothing>
}

enum class SyncState { LocalOnly, Pending, Synced, Conflict, Failed }

data class AiConversationMessage(
    val role: String,
    val content: String,
    val presentationJson: String? = null,
    val attachmentPaths: List<String> = emptyList(),
)
data class AiReport(
    val id: String,
    val provider: AiProvider,
    val mode: AiMode,
    val model: String,
    val title: String,
    val summary: String,
    val messagesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class DailyGreeting(
    val localDate: String,
    val text: String,
    val provider: AiProvider,
    val generatedAt: Long,
)
