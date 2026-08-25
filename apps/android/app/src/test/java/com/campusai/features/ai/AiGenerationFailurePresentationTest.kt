package com.campusai.features.ai

import com.campusai.core.ai.AiRoutingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiGenerationFailurePresentationTest {
    @Test
    fun `routing guidance remains visible`() {
        assertEquals(
            "请先下载本地模型。",
            generationFailureMessage(AiRoutingException("local_model_not_ready", "请先下载本地模型。")),
        )
    }

    @Test
    fun `unexpected technical details are hidden`() {
        val message = generationFailureMessage(
            IllegalStateException("Flow invariant is violated at Dispatchers.IO"),
        )

        assertEquals("生成中断，请重试。", message)
        assertFalse(message.contains("Flow"))
        assertFalse(message.contains("Dispatchers"))
    }
}
