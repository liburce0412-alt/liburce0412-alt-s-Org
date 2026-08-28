package com.campusai.features.ai

import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaesarTurnAssemblyTest {
    @Test
    fun `speech transcript and image remain in one local locked request`() {
        val request = assembleCaesarTurnRequest(
            mode = AiMode.FAST,
            existingMessages = listOf(AiConversationMessage("assistant", "我在。")),
            prompt = "这张图里的课程是什么",
            displayPrompt = "这张图里的课程是什么",
            structuredContext = JSONObject().put("inputPart", "voice_transcript").put("onDevice", true),
            attachments = listOf(CaesarImageAttachment("C:/cache/lesson.jpg", "image/jpeg", "高等数学 A-203")),
            sessionId = "session-voice-image",
            ownerUserId = "owner",
            localModelId = "qwen3.5-2b-mnn",
        )

        assertEquals("qwen3.5-2b-mnn", request.localModelId)
        assertEquals(listOf("C:/cache/lesson.jpg"), request.imagePaths)
        assertTrue(request.requiresLocal)
        assertEquals("这张图里的课程是什么", request.messages.last().content)
        val context = JSONObject(request.structuredContextJson)
        assertEquals("voice_transcript", context.getString("inputPart"))
        assertEquals("高等数学 A-203", context.getJSONArray("imageOcr").getJSONObject(0).getString("text"))
    }

    @Test
    fun `conversation codec preserves surfaces attachments and rejects malformed recovery`() {
        val original = listOf(
            AiConversationMessage("user", "查看图片", attachmentPaths = listOf("C:/cache/a.jpg")),
            AiConversationMessage(
                "assistant",
                "已读取",
                presentationJson = "{\"schema\":\"caesar.surface.v1\"}",
                providerReasoningContent = "hidden-provider-state",
                cloudHealthSensitive = true,
            ),
        )

        val restored = AiConversationCodec.decode(AiConversationCodec.encode(original))
        assertEquals(original.map { it.copy(providerReasoningContent = null) }, restored)
        assertTrue(restored.last().cloudHealthSensitive)
        assertTrue(AiConversationCodec.decode("not-json").isEmpty())
        assertFalse(AiConversationCodec.decode("[{\"role\":\"root\",\"content\":\"hidden\"}]").any())
    }
}
