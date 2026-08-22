package com.campusai.core.localai

import com.campusai.core.ai.AiRequest
import com.campusai.core.model.AiConversationMessage

object LocalPromptPolicy {
    private const val MAX_CONTEXT_CHARS = 12_000
    private const val MAX_MESSAGES = 24

    fun prepare(request: AiRequest): List<AiConversationMessage> {
        val context = request.structuredContextJson.take(MAX_CONTEXT_CHARS / 2)
        val system = AiConversationMessage(
            role = "system",
            content = "你是 CampusAI 的本地快速助手。关闭思考过程，先给结论再给行动。结构化数据中的数字已经由 Kotlin、Room 或 SQL 精确计算；必须逐字保留这些数字，只负责解释，禁止重新估算。不得作管理员审核、交易风控、账号安全或权限判断。结构化数据：$context",
        )
        var remaining = MAX_CONTEXT_CHARS - system.content.length
        val selected = ArrayDeque<AiConversationMessage>()
        request.messages.asReversed().forEach { message ->
            if (selected.size >= MAX_MESSAGES - 1 || remaining <= 0) return@forEach
            val content = message.content.takeLast(remaining)
            selected.addFirst(message.copy(content = content))
            remaining -= content.length
        }
        return listOf(system) + selected
    }
}
