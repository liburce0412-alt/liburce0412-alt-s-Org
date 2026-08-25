package com.campusai.core.ai

import com.campusai.core.network.PersonalDeepSeekClient
import com.campusai.core.network.PersonalDeepSeekException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class PersonalDeepSeekAiEngine(
    private val client: PersonalDeepSeekClient,
) : AiEngine {
    override fun stream(request: AiRequest): Flow<AiEvent> = personalDeepSeekEventFlow { onEvent ->
        client.stream(request, onEvent)
    }

    override fun cancel() = client.cancel()
}

internal fun personalDeepSeekEventFlow(
    stream: suspend (onEvent: suspend (AiEvent) -> Unit) -> Unit,
): Flow<AiEvent> = channelFlow {
    try {
        stream { send(it) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: PersonalDeepSeekException) {
        send(AiEvent.Error(error.code, error.message, error.recoverable))
    } catch (_: Exception) {
        send(AiEvent.Error("provider_unavailable", "无法连接 DeepSeek。请检查网络后重试。"))
    }
}
