package com.campusai.core.agent

import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.LocalImageRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MnnPromptExecutorMetadataTest {
    @Test
    fun koogRoundTripKeepsVisionAndSensitivityMetadata() {
        val sha = "b".repeat(64)
        val original = AiConversationMessage(
            role = "user",
            content = "看看这张图",
            attachmentPaths = listOf("C:/private/image.jpg"),
            attachmentRefs = listOf(
                LocalImageRef(
                    assetId = sha,
                    relativePath = "ai-conversations/session/attachments/$sha.jpg",
                    mimeType = "image/jpeg",
                    width = 100,
                    height = 80,
                    byteSize = 512,
                    sha256 = sha,
                ),
            ),
            cloudHealthSensitive = true,
        )

        val merged = mergeExecutionMessageMetadata(
            converted = listOf(AiConversationMessage("user", "看看这张图")),
            original = listOf(original),
        ).single()

        assertEquals(original.attachmentPaths, merged.attachmentPaths)
        assertEquals(original.attachmentRefs, merged.attachmentRefs)
        assertTrue(merged.cloudHealthSensitive)
    }
}
