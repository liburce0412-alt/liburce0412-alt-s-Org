package com.campusai

import com.campusai.core.agent.CaesarAgentEngine
import com.campusai.core.agent.CaesarTool
import com.campusai.core.agent.CaesarToolRegistry
import com.campusai.core.agent.CaesarToolResult
import com.campusai.core.agent.ToolDefinition
import com.campusai.core.agent.ToolRiskLevel
import com.campusai.core.ai.AiEngine
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaesarAgentOutputRetryTest {
    @Test fun `empty rejected local output retries once`() = runTest {
        val attempts = AtomicInteger()
        val requests = CopyOnWriteArrayList<AiRequest>()
        val delegate = object : AiEngine {
            override fun stream(request: AiRequest): Flow<AiEvent> = flow {
                requests += request
                if (attempts.incrementAndGet() == 1) emit(AiEvent.Error("local_output_rejected", "未形成回答"))
                else {
                    emit(AiEvent.Delta("我是 Caesar∞。"))
                    emit(AiEvent.Done(12))
                }
            }
            override fun cancel() = Unit
        }
        val events = CaesarAgentEngine(delegate, CaesarToolRegistry(emptyList())).stream(request()).toList()

        assertEquals(2, attempts.get())
        assertEquals("[]", requests[1].caesarToolsJson)
        assertEquals(192, requests[1].maxOutputTokens)
        assertTrue(requests[1].messages.last().content.contains("直接回答上面的原始问题"))
        assertEquals("你是谁", requests[1].userPrompt)
        assertTrue(events.any { it is AiEvent.Delta && it.text == "我是 Caesar∞。" })
        assertFalse(events.any { it is AiEvent.Error })
    }

    @Test fun `rejected output is not retried after visible text`() = runTest {
        val attempts = AtomicInteger()
        val delegate = object : AiEngine {
            override fun stream(request: AiRequest): Flow<AiEvent> = flow {
                attempts.incrementAndGet()
                emit(AiEvent.Delta("已经可见"))
                emit(AiEvent.Error("local_output_rejected", "后续被拦截"))
            }
            override fun cancel() = Unit
        }
        val events = CaesarAgentEngine(delegate, CaesarToolRegistry(emptyList())).stream(request()).toList()

        assertEquals(1, attempts.get())
        assertTrue(events.any { it is AiEvent.Error && it.code == "local_output_rejected" })
    }

    @Test fun `tool projected output rejection is not converted into a chat repair`() = runTest {
        val attempts = AtomicInteger()
        val delegate = object : AiEngine {
            override fun stream(request: AiRequest): Flow<AiEvent> = flow {
                attempts.incrementAndGet()
                emit(AiEvent.Error("local_output_rejected", "未形成回答"))
            }
            override fun cancel() = Unit
        }
        val registry = CaesarToolRegistry(
            listOf(
                CaesarTool(
                    ToolDefinition(
                        name = "health.get_snapshot",
                        description = "health",
                        parameters = emptyList(),
                        riskLevel = ToolRiskLevel.READ_ONLY,
                        keywords = setOf("健康概览"),
                    ),
                ) { _, _ -> CaesarToolResult.Success("{}") },
            ),
        )

        val events = CaesarAgentEngine(delegate, registry).stream(request("查看健康概览")).toList()

        assertEquals(1, attempts.get())
        assertTrue(events.any { it is AiEvent.Error && it.code == "local_output_rejected" })
    }

    private fun request(prompt: String = "你是谁") = AiRequest(
        mode = AiMode.FAST,
        messages = listOf(AiConversationMessage("user", prompt)),
        sessionId = "session",
        userPrompt = prompt,
    )
}
