package com.campusai.core.network

import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.ai.CloudAiProvider
import com.campusai.core.ai.CloudProviderConnection
import com.campusai.core.ai.CloudProviderModel
import com.campusai.core.model.AiMode
import com.campusai.core.security.PersonalDeepSeekKeyStore

/** Compatibility facade retained while callers migrate to [PersonalCloudClient]. */
class PersonalDeepSeekClient(
    keyStore: PersonalDeepSeekKeyStore,
) {
    private val delegate = PersonalCloudClient(
        provider = CloudAiProvider.DEEPSEEK,
        credential = keyStore::read,
        selectedModel = keyStore::selectedModel,
        client = defaultCloudHttpClient(),
    )

    suspend fun stream(request: AiRequest, onEvent: suspend (AiEvent) -> Unit) =
        delegate.stream(request, onEvent = onEvent)

    suspend fun listModels(): List<CloudProviderModel> = delegate.listModels()

    suspend fun validateConnection(modelId: String = ""): CloudProviderConnection =
        delegate.validateConnection(modelId)

    fun cancel() = delegate.cancel()
}

typealias PersonalDeepSeekException = CloudProviderException

internal fun personalDeepSeekModel(mode: AiMode): String =
    CloudAiProvider.DEEPSEEK.defaultModel(mode)

internal fun personalDeepSeekThinking(mode: AiMode): String =
    if (mode == AiMode.FAST) "disabled" else "enabled"

data class PersonalDeepSeekChunk(
    val delta: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
)

object PersonalDeepSeekStreamParser {
    fun parse(raw: String): PersonalDeepSeekChunk? = OpenAiCompatibleStreamParser.parse(raw)?.let {
        PersonalDeepSeekChunk(it.delta, it.inputTokens, it.outputTokens)
    }
}
