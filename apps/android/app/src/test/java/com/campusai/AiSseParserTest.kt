package com.campusai

import com.campusai.core.ai.AiEvent
import com.campusai.core.model.AiProvider
import com.campusai.core.network.AiSseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiSseParserTest {
    @Test fun `keeps five DeepSeek SSE event types compatible`() {
        assertEquals(AiEvent.Meta("deepseek-v4-flash", AiProvider.DEEPSEEK), AiSseParser.parse("meta", "{\"model\":\"deepseek-v4-flash\"}"))
        assertEquals(AiEvent.Status("responding", 12), AiSseParser.parse("status", "{\"stage\":\"responding\",\"elapsedMs\":12}"))
        assertEquals(AiEvent.Delta("你好"), AiSseParser.parse("delta", "{\"text\":\"你好\"}"))
        assertEquals(AiEvent.Done(20, 3, 5), AiSseParser.parse("done", "{\"elapsedMs\":20,\"usage\":{\"inputTokens\":3,\"outputTokens\":5}}"))
        assertEquals(AiEvent.Error("quota_exhausted", "稍后重试"), AiSseParser.parse("error", "{\"code\":\"quota_exhausted\",\"message\":\"稍后重试\"}"))
        assertNull(AiSseParser.parse("unknown", "{}"))
    }
}
