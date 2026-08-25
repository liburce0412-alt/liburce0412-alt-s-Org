package com.campusai

import com.campusai.core.ai.AiRequest
import com.campusai.core.localai.LocalPromptPolicy
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPromptPolicyTest {
    @Test fun `preserves precomputed statistics and enforces bounded context`() {
        val exact = "{\"totalMinutes\":137,\"goalRate\":62.5,\"streakDays\":9}"
        val messages = List(40) { AiConversationMessage("user", "学习记录 $it " + "x".repeat(800)) }
        val prepared = LocalPromptPolicy.prepare(AiRequest(AiMode.FAST, messages, exact))
        assertTrue(prepared.first().content.contains(exact))
        assertTrue(prepared.first().content.contains("禁止重新估算"))
        assertTrue(prepared.first().content.contains("唯一外层 <final>"))
        assertTrue(prepared.first().content.contains("只输出最终答案正文"))
        assertTrue(prepared.first().content.contains("唯一外层 <tool_call>"))
        assertTrue(prepared.first().content.contains("160 个 token"))
        assertTrue(prepared.size <= 32)
        assertTrue(prepared.sumOf { it.content.length } <= 24_000)
    }

    @Test fun `fast 2b uses its smaller manifest context budget`() {
        val messages = List(40) { AiConversationMessage("user", "message $it " + "x".repeat(800)) }
        val prepared = LocalPromptPolicy.prepare(AiRequest(AiMode.FAST, messages), contextTokens = 4_096)

        assertTrue(prepared.size <= 24)
        assertTrue(prepared.sumOf { it.content.length } <= 12_288)
    }
}
