package com.campusai.core.agent

enum class AutonomyMode { OWNER_DIRECT, CONFIRM_EXTERNAL, READ_ONLY }

sealed interface InputPart {
    data class Text(val text: String) : InputPart
    data class Image(val localPath: String, val mimeType: String, val ocrText: String = "") : InputPart
    data class VoiceTranscript(val text: String, val onDevice: Boolean) : InputPart
}

data class AgentRequest(
    val sessionId: String,
    val parts: List<InputPart>,
    val autonomyMode: AutonomyMode = AutonomyMode.OWNER_DIRECT,
    val currentScreen: String = "ai",
    val ownerUserId: String = "",
)

sealed interface AgentEvent {
    data class Token(val text: String) : AgentEvent
    data class ToolStarted(val name: String) : AgentEvent
    data class ToolCompleted(val name: String, val success: Boolean) : AgentEvent
    data class Surface(val surface: CaesarSurface) : AgentEvent
    data class MemoryProposal(val proposal: CaesarMemoryProposal) : AgentEvent
    data object Completed : AgentEvent
    data class Error(val code: String, val message: String) : AgentEvent
}

enum class ToolRiskLevel { READ_ONLY, REVERSIBLE_WRITE, EXTERNAL_SIDE_EFFECT, IRREVERSIBLE }
enum class IdempotencyPolicy { NONE, BY_REQUEST, PERSISTED }

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val maxLength: Int? = null,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val riskLevel: ToolRiskLevel,
    val idempotencyPolicy: IdempotencyPolicy = IdempotencyPolicy.NONE,
    val requiredCapabilities: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
)

data class ToolExecutionContext(
    val sessionId: String,
    val ownerUserId: String,
    val userPrompt: String,
    val autonomyMode: AutonomyMode,
    val explicitUserIntent: Boolean,
    val idempotencyKey: String,
    val confirmationGranted: Boolean = false,
)

sealed interface CaesarToolResult {
    data class Success(
        val contentJson: String,
        val surface: CaesarSurface? = null,
    ) : CaesarToolResult

    data class NeedsConfirmation(
        val title: String,
        val description: String,
        val actionId: String,
    ) : CaesarToolResult

    data class RetryableError(val code: String, val message: String) : CaesarToolResult
    data class Denied(val code: String, val message: String) : CaesarToolResult
    data class Unavailable(val code: String, val message: String) : CaesarToolResult
}

data class CaesarMemoryProposal(
    val id: String,
    val type: String,
    val content: String,
    val source: String,
    val confidence: Double,
    val expiresAt: Long? = null,
)
