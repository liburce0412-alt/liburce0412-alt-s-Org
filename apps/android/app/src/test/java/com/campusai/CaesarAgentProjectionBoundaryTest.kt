package com.campusai

import com.campusai.core.agent.CaesarAgentEngine
import com.campusai.core.agent.CaesarComponent
import com.campusai.core.agent.CaesarSurface
import com.campusai.core.agent.CaesarTool
import com.campusai.core.agent.CaesarToolRegistry
import com.campusai.core.agent.CaesarToolResult
import com.campusai.core.agent.ToolDefinition
import com.campusai.core.agent.ToolParameter
import com.campusai.core.agent.ToolRiskLevel
import com.campusai.core.ai.AiEngine
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaesarAgentProjectionBoundaryTest {
    @Test
    fun `known registered tool outside this turn projection is denied`() = runTest {
        val blockedCalls = AtomicInteger()
        val delegate = UnprojectedToolDelegate("profile.get_private", "{malformed")
        val registry = CaesarToolRegistry(
            listOf(
                readTool("health.get_snapshot", setOf("健康概览")),
                readTool("profile.get_private", setOf("私密资料")) {
                    blockedCalls.incrementAndGet()
                    CaesarToolResult.Success("{}")
                },
            ),
        )

        val events = CaesarAgentEngine(delegate, registry)
            .stream(request("查看健康概览", requiresLocal = true))
            .toList()

        assertEquals(0, blockedCalls.get())
        assertTrue(delegate.requests.first().caesarToolsJson.contains("health.get_snapshot"))
        assertFalse(delegate.requests.first().caesarToolsJson.contains("profile.get_private"))
        assertEquals("tool_not_projected", delegate.lastToolFailureCode())
        assertTrue(events.any { it is AiEvent.ToolFinished && it.name == "profile.get_private" && !it.success })
    }

    @Test
    fun `cloud-capable turn never projects or executes device health tools`() = runTest {
        val healthCalls = AtomicInteger()
        val delegate = UnprojectedToolDelegate("health.get_snapshot", "{}")
        val registry = CaesarToolRegistry(
            listOf(
                readTool("health.get_snapshot", setOf("健康概览")) {
                    healthCalls.incrementAndGet()
                    CaesarToolResult.Success("{\"originPackages\":[\"private.source\"]}")
                },
            ),
        )

        val events = CaesarAgentEngine(delegate, registry)
            .stream(request("查看健康概览", requiresLocal = false))
            .toList()

        assertEquals(0, healthCalls.get())
        assertFalse(delegate.requests.first().caesarToolsJson.contains("health.get_snapshot"))
        assertEquals("tool_not_projected", delegate.lastToolFailureCode())
        assertTrue(events.any { it is AiEvent.ToolFinished && it.name == "health.get_snapshot" && !it.success })
    }

    @Test
    fun `confirmation resumes with only the original projected tools`() = runTest {
        val confirmedCalls = AtomicInteger()
        val blockedCalls = AtomicInteger()
        val delegate = ConfirmationBoundaryDelegate()
        val registry = CaesarToolRegistry(
            listOf(
                CaesarTool(
                    ToolDefinition(
                        name = "memory.forget",
                        description = "删除记忆",
                        parameters = listOf(ToolParameter("id", "string", "记忆 ID")),
                        riskLevel = ToolRiskLevel.IRREVERSIBLE,
                        keywords = setOf("删除记忆"),
                    ),
                ) { _, _ ->
                    confirmedCalls.incrementAndGet()
                    CaesarToolResult.Success("{\"forgot\":true}")
                },
                readTool("profile.get_private", setOf("私密资料")) {
                    blockedCalls.incrementAndGet()
                    CaesarToolResult.Success("{}")
                },
            ),
        )
        val engine = CaesarAgentEngine(delegate, registry)

        val proposed = engine.stream(request("删除记忆 memory-7")).toList()
        val confirmation = proposed.filterIsInstance<AiEvent.Surface>().single().json.let(CaesarSurface::fromJson)
        val actionId = confirmation
            ?.components
            ?.filterIsInstance<CaesarComponent.Button>()
            ?.single()
            ?.actionId
        assertNotNull(actionId)
        assertEquals(0, confirmedCalls.get())

        val confirmed = engine.confirm(checkNotNull(actionId)).toList()

        assertEquals(1, confirmedCalls.get())
        assertEquals(0, blockedCalls.get())
        assertEquals("tool_not_projected", delegate.lastToolFailureCode())
        assertTrue(confirmed.any { it is AiEvent.ToolFinished && it.name == "profile.get_private" && !it.success })
        assertTrue(delegate.requests.all { !it.caesarToolsJson.contains("profile.get_private") })
    }

    private fun readTool(
        name: String,
        keywords: Set<String>,
        execute: suspend () -> CaesarToolResult = { CaesarToolResult.Success("{}") },
    ) = CaesarTool(
        ToolDefinition(
            name = name,
            description = name,
            parameters = emptyList(),
            riskLevel = ToolRiskLevel.READ_ONLY,
            keywords = keywords,
        ),
    ) { _, _ -> execute() }

    private fun request(prompt: String, requiresLocal: Boolean = false) = AiRequest(
        mode = AiMode.FAST,
        messages = listOf(AiConversationMessage("user", prompt)),
        sessionId = "projection-boundary-session",
        ownerUserId = "owner",
        userPrompt = prompt,
        requiresLocal = requiresLocal,
    )

    private class UnprojectedToolDelegate(
        private val toolName: String,
        private val argumentsJson: String,
    ) : AiEngine {
        val requests = CopyOnWriteArrayList<AiRequest>()

        override fun stream(request: AiRequest): Flow<AiEvent> = flow {
            requests += request
            if (request.messages.lastOrNull()?.role == "tool") {
                emit(AiEvent.Delta("已拒绝未授权工具。"))
                emit(AiEvent.Done(1))
            } else {
                emit(AiEvent.ToolCallRequested(toolName, argumentsJson, "<$toolName>"))
            }
        }

        override fun cancel() = Unit

        fun lastToolFailureCode(): String = JSONObject(
            requests.last().messages.last { it.role == "tool" }.content,
        ).getString("code")
    }

    private class ConfirmationBoundaryDelegate : AiEngine {
        val requests = CopyOnWriteArrayList<AiRequest>()

        override fun stream(request: AiRequest): Flow<AiEvent> = flow {
            requests += request
            val lastTool = request.messages.lastOrNull { it.role == "tool" }?.content
            when {
                lastTool == null -> emit(
                    AiEvent.ToolCallRequested(
                        name = "memory.forget",
                        argumentsJson = "{\"id\":\"memory-7\"}",
                        rawContent = "<memory.forget>",
                    ),
                )
                lastTool.contains("\"forgot\":true") -> emit(
                    AiEvent.ToolCallRequested(
                        name = "profile.get_private",
                        argumentsJson = "{}",
                        rawContent = "<profile.get_private>",
                    ),
                )
                else -> {
                    emit(AiEvent.Delta("已完成原操作并拒绝额外工具。"))
                    emit(AiEvent.Done(1))
                }
            }
        }

        override fun cancel() = Unit

        fun lastToolFailureCode(): String = JSONObject(
            requests.last().messages.last { it.role == "tool" }.content,
        ).getString("code")
    }
}
