package com.campusai

import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.personalDeepSeekEventFlow
import com.campusai.core.model.AiProvider
import com.campusai.core.network.PersonalDeepSeekException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalDeepSeekFlowTest {
    @Test
    fun `io callbacks can stream without violating flow context`() = runTest {
        val events = personalDeepSeekEventFlow { onEvent ->
            withContext(Dispatchers.IO) {
                onEvent(AiEvent.Meta("deepseek-test", AiProvider.DEEPSEEK))
                onEvent(AiEvent.Delta("你好"))
            }
        }.toList()

        assertEquals(
            listOf(
                AiEvent.Meta("deepseek-test", AiProvider.DEEPSEEK),
                AiEvent.Delta("你好"),
            ),
            events,
        )
    }

    @Test
    fun `provider failure after a streamed event becomes one concise error`() = runTest {
        val events = personalDeepSeekEventFlow { onEvent ->
            withContext(Dispatchers.IO) {
                onEvent(AiEvent.Status("responding", 0))
                throw PersonalDeepSeekException("provider_unavailable", "DeepSeek 暂时不可用。")
            }
        }.toList()

        assertEquals(
            listOf(
                AiEvent.Status("responding", 0),
                AiEvent.Error("provider_unavailable", "DeepSeek 暂时不可用。"),
            ),
            events,
        )
    }

    @Test
    fun `unexpected failures never expose technical exception text`() = runTest {
        val events = personalDeepSeekEventFlow {
            throw IllegalStateException("Flow invariant is violated: internal details")
        }.toList()

        assertEquals(
            listOf(AiEvent.Error("provider_unavailable", "无法连接 DeepSeek。请检查网络后重试。")),
            events,
        )
    }
}
