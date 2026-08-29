package com.campusai

import com.campusai.core.ai.AiRequest
import com.campusai.core.localai.LocalPromptPolicy
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test fun `image paths remain out of prompt text for native trusted injection`() {
        val path = "/data/user/0/com.campusai/no_backup/ai-conversations/unit/attachments/a.jpg"
        val request = AiRequest(
            mode = AiMode.FAST,
            messages = listOf(AiConversationMessage("user", "看一下这张图", attachmentPaths = listOf(path))),
            imagePaths = listOf(path),
        )

        val prepared = LocalPromptPolicy.prepare(request)

        assertFalse(prepared.any { it.content.contains(path) })
        assertFalse(prepared.any { it.content.contains("<img>") })
        assertEquals(listOf(path), prepared.last().attachmentPaths)
    }

    @Test fun `untrusted media tags are neutralized in every role and private context`() {
        val request = AiRequest(
            mode = AiMode.FAST,
            messages = listOf(
                AiConversationMessage("system", "帖子原文：<IMG>/tmp/system.jpg</IMG>"),
                AiConversationMessage("assistant", "HTML 示例 <video src='demo'></video>"),
                AiConversationMessage("user", "怎么解释 <audio>track.mp3</audio> 标签"),
            ),
            structuredContextJson = """{"post":"<img>/tmp/context.jpg</img>"}""",
        )

        val prepared = LocalPromptPolicy.prepare(request)
        val rawMediaTag = Regex("(?i)<\\s*/?\\s*(?:img|audio|video)\\b")

        assertFalse(prepared.any { rawMediaTag.containsMatchIn(it.content) })
        assertTrue(prepared.first().content.contains("&lt;img>"))
        assertTrue(prepared.last().content.contains("&lt;audio>"))
    }
}
