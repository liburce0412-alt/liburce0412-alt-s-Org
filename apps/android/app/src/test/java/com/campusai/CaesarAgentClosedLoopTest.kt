package com.campusai

import com.campusai.core.agent.CaesarAgentEngine
import com.campusai.core.agent.CaesarComponent
import com.campusai.core.agent.CaesarSurface
import com.campusai.core.agent.CaesarTool
import com.campusai.core.agent.CaesarToolRegistry
import com.campusai.core.agent.CaesarToolResult
import com.campusai.core.agent.IdempotencyPolicy
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaesarAgentClosedLoopTest {
    @Test
    fun `read tool executes fake use case returns result and renders native card`() = runTest {
        val useCase = FakeHealthUseCase()
        val delegate = ScriptedToolDelegate(
            toolName = "health.get_snapshot",
            arguments = JSONObject().put("period", "today"),
            finalAnswer = "今天平均心率 68，步数和睡眠暂缺。",
        )
        val registry = CaesarToolRegistry(
            listOf(
                CaesarTool(
                    ToolDefinition(
                        name = "health.get_snapshot",
                        description = "读取健康概览",
                        parameters = listOf(ToolParameter("period", "string", "today/week/month")),
                        riskLevel = ToolRiskLevel.READ_ONLY,
                        keywords = setOf("健康概览"),
                    ),
                ) { arguments, context -> useCase.execute(arguments, context.idempotencyKey) },
            ),
        )

        val events = CaesarAgentEngine(delegate, registry).stream(request("读取今天健康概览")).toList()

        assertEquals(1, useCase.calls.get())
        assertTrue(delegate.requests.first().caesarToolsJson.contains("health.get_snapshot"))
        assertTrue(delegate.requests.last().messages.any { it.role == "tool" && it.content.contains("\"heartRate\":68") })
        assertTrue(events.any { it is AiEvent.ToolStarted && it.name == "health.get_snapshot" })
        assertTrue(events.any { it is AiEvent.ToolFinished && it.success })
        val surface = events.filterIsInstance<AiEvent.Surface>().singleOrNull()?.json?.let(CaesarSurface::fromJson)
        assertNotNull(surface)
        assertEquals("今日健康", surface?.title)
        assertTrue(surface?.components?.any { it is CaesarComponent.Metric && it.value == "68 bpm" } == true)
        assertEquals("今天平均心率 68，步数和睡眠暂缺。", events.filterIsInstance<AiEvent.Delta>().joinToString("") { it.text })
        assertTrue(events.last() is AiEvent.Done)
    }

    @Test
    fun `invalid parameters fail before fake use case and return error to model`() = runTest {
        val calls = AtomicInteger()
        val delegate = ScriptedToolDelegate(
            toolName = "time.create_record",
            arguments = JSONObject().put("minutes", "25"),
            finalAnswer = "时长参数格式不正确，没有创建记录。",
        )
        val registry = CaesarToolRegistry(
            listOf(
                CaesarTool(
                    ToolDefinition(
                        name = "time.create_record",
                        description = "创建记录",
                        parameters = listOf(ToolParameter("minutes", "integer", "分钟")),
                        riskLevel = ToolRiskLevel.REVERSIBLE_WRITE,
                        keywords = setOf("创建记录"),
                    ),
                ) { _, _ ->
                    calls.incrementAndGet()
                    CaesarToolResult.Success("{\"created\":true}")
                },
            ),
        )

        val events = CaesarAgentEngine(delegate, registry).stream(request("创建记录")).toList()

        assertEquals(0, calls.get())
        assertTrue(delegate.requests.last().messages.any {
            it.role == "tool" && it.content.contains("invalid_arguments") && it.content.contains("integer")
        })
        assertEquals("时长参数格式不正确，没有创建记录。", events.filterIsInstance<AiEvent.Delta>().joinToString("") { it.text })
    }

    @Test
    fun `irreversible fake use case requires confirmation executes once and returns card`() = runTest {
        val useCase = FakeDeleteUseCase()
        val delegate = ScriptedToolDelegate(
            toolName = "memory.forget",
            arguments = JSONObject().put("id", "memory-7"),
            finalAnswer = "已删除这条记忆。",
        )
        val registry = CaesarToolRegistry(
            listOf(
                CaesarTool(
                    ToolDefinition(
                        name = "memory.forget",
                        description = "删除记忆",
                        parameters = listOf(ToolParameter("id", "string", "记忆 ID")),
                        riskLevel = ToolRiskLevel.IRREVERSIBLE,
                        idempotencyPolicy = IdempotencyPolicy.PERSISTED,
                        keywords = setOf("删除记忆"),
                    ),
                ) { arguments, context -> useCase.execute(arguments.getString("id"), context.idempotencyKey) },
            ),
        )
        val engine = CaesarAgentEngine(delegate, registry)

        val proposed = engine.stream(request("删除记忆 memory-7")).toList()
        assertEquals(0, useCase.calls.get())
        val confirmation = proposed.filterIsInstance<AiEvent.Surface>().single().json.let(CaesarSurface::fromJson)
        val actionId = confirmation?.components?.filterIsInstance<CaesarComponent.Button>()?.single()?.actionId
        assertNotNull(actionId)

        val confirmed = engine.confirm(checkNotNull(actionId)).toList()
        assertEquals(1, useCase.calls.get())
        assertTrue(confirmed.any { it is AiEvent.ToolFinished && it.success })
        val resultCard = confirmed.filterIsInstance<AiEvent.Surface>().singleOrNull()?.json?.let(CaesarSurface::fromJson)
        assertEquals("记忆已删除", resultCard?.title)
        assertEquals("已删除这条记忆。", confirmed.filterIsInstance<AiEvent.Delta>().joinToString("") { it.text })

        val repeated = engine.confirm(actionId).toList()
        assertEquals(1, useCase.calls.get())
        assertTrue(repeated.single() is AiEvent.Error && (repeated.single() as AiEvent.Error).code == "confirmation_expired")
    }

    private fun request(prompt: String) = AiRequest(
        mode = AiMode.FAST,
        messages = listOf(AiConversationMessage("user", prompt)),
        sessionId = "closed-loop-session",
        ownerUserId = "owner",
        userPrompt = prompt,
    )

    private class ScriptedToolDelegate(
        private val toolName: String,
        private val arguments: JSONObject,
        private val finalAnswer: String,
    ) : AiEngine {
        val requests = CopyOnWriteArrayList<AiRequest>()

        override fun stream(request: AiRequest): Flow<AiEvent> = flow {
            requests += request
            if (request.messages.lastOrNull()?.role == "tool") {
                emit(AiEvent.Delta(finalAnswer))
                emit(AiEvent.Done(5))
            } else {
                val raw = "<tool_call><function=$toolName></function></tool_call>"
                emit(AiEvent.ToolCallRequested(toolName, arguments.toString(), raw))
            }
        }

        override fun cancel() = Unit
    }

    private class FakeHealthUseCase {
        val calls = AtomicInteger()

        fun execute(arguments: JSONObject, idempotencyKey: String): CaesarToolResult {
            calls.incrementAndGet()
            val content = JSONObject()
                .put("period", arguments.getString("period"))
                .put("heartRate", 68)
                .put("missing", org.json.JSONArray(listOf("steps", "sleep")))
                .put("idempotencyKey", idempotencyKey)
                .toString()
            return CaesarToolResult.Success(
                contentJson = content,
                surface = CaesarSurface(
                    id = "health-today",
                    title = "今日健康",
                    components = listOf(
                        CaesarComponent.Metric("平均心率", "68 bpm", "fresh"),
                        CaesarComponent.Text("步数和睡眠暂无可验证数据"),
                    ),
                ),
            )
        }
    }

    private class FakeDeleteUseCase {
        val calls = AtomicInteger()
        private val completed = mutableMapOf<String, CaesarToolResult.Success>()

        fun execute(id: String, idempotencyKey: String): CaesarToolResult = completed.getOrPut(idempotencyKey) {
            calls.incrementAndGet()
            CaesarToolResult.Success(
                contentJson = JSONObject().put("id", id).put("deleted", true).toString(),
                surface = CaesarSurface(
                    id = "deleted-$id",
                    title = "记忆已删除",
                    components = listOf(CaesarComponent.Text("可在记忆页查看剩余内容。")),
                ),
            )
        }
    }
}
