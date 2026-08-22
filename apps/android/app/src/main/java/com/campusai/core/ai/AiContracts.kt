package com.campusai.core.ai

import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import kotlinx.coroutines.flow.Flow

data class AiRequest(
    val mode: AiMode,
    val messages: List<AiConversationMessage>,
    /** Pre-computed, size-limited structured context. Engines must not recalculate facts. */
    val structuredContextJson: String = "{}",
    val maxOutputTokens: Int = 512,
)

sealed interface AiEvent {
    data class Meta(val model: String, val provider: AiProvider) : AiEvent
    data class Status(val stage: String, val elapsedMs: Long) : AiEvent
    data class Delta(val text: String) : AiEvent
    data class Done(
        val elapsedMs: Long,
        val inputTokens: Long? = null,
        val outputTokens: Long? = null,
    ) : AiEvent
    data class Error(val code: String, val message: String, val recoverable: Boolean = true) : AiEvent
}

interface AiEngine {
    fun stream(request: AiRequest): Flow<AiEvent>
    fun cancel()
}

class AiRoutingException(
    val code: String,
    override val message: String,
    val canUseCloudOnce: Boolean = false,
) : IllegalStateException(message)
