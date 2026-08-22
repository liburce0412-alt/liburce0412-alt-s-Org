package com.campusai.core.ai

import com.campusai.core.network.PersonalDeepSeekClient
import com.campusai.core.network.PersonalDeepSeekException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PersonalDeepSeekAiEngine(
    private val client: PersonalDeepSeekClient,
) : AiEngine {
    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        try {
            client.stream(request) { emit(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: PersonalDeepSeekException) {
            emit(AiEvent.Error(error.code, error.message, error.recoverable))
        } catch (_: Exception) {
            emit(AiEvent.Error("provider_unavailable", "无法连接 DeepSeek。请检查网络后重试。"))
        }
    }

    override fun cancel() = client.cancel()
}
