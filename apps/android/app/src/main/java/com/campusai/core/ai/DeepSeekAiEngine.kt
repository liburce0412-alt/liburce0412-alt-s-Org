package com.campusai.core.ai

import com.campusai.core.model.AiMode
import com.campusai.core.network.AiEdgeClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

class DeepSeekAiEngine(private val client: AiEdgeClient = AiEdgeClient()) : AiEngine {
    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        client.stream(
            mode = if (request.mode == AiMode.FAST) "fast" else "deep",
            messages = request.messages.map { it.role to it.content },
            context = JSONObject(request.structuredContextJson),
        ) { emit(it) }
    }

    override fun cancel() = client.cancel()
}
