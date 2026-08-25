package com.campusai

import com.campusai.core.ai.AiEngine
import com.campusai.core.ai.AiEngineRouter
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.ai.AiRoute
import com.campusai.core.ai.AiRouteDecision
import com.campusai.core.ai.decideAiRoute
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import com.campusai.core.model.LocalModelState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEngineRouterTest {
    @Test fun `auto follows online and offline rules`() {
        assertEquals(AiRouteDecision.Use(AiRoute.LOCAL), decideAiRoute(AiProvider.AUTO, AiMode.FAST, true, true, true))
        val cloudConsent = decideAiRoute(AiProvider.AUTO, AiMode.FAST, true, false, true) as AiRouteDecision.Block
        assertEquals("local_model_not_ready", cloudConsent.code)
        assertTrue(cloudConsent.canUseCloudOnce)
        assertEquals(AiRouteDecision.Use(AiRoute.LOCAL), decideAiRoute(AiProvider.AUTO, AiMode.FAST, false, true))
        assertEquals("offline_model_missing", (decideAiRoute(AiProvider.AUTO, AiMode.FAST, false, false) as AiRouteDecision.Block).code)
        assertEquals(AiRouteDecision.Use(AiRoute.LOCAL), decideAiRoute(AiProvider.AUTO, AiMode.DEEP, false, true))
    }

    @Test fun `explicit providers never silently fall back`() {
        assertEquals("deepseek_offline", (decideAiRoute(AiProvider.DEEPSEEK, AiMode.FAST, false, true) as AiRouteDecision.Block).code)
        assertEquals(AiRouteDecision.Use(AiRoute.LOCAL), decideAiRoute(AiProvider.LOCAL, AiMode.DEEP, true, true))
        val missing = decideAiRoute(AiProvider.LOCAL, AiMode.FAST, true, false) as AiRouteDecision.Block
        assertTrue(missing.canUseCloudOnce)
    }

    @Test fun `explicit cloud route requires the users own key`() {
        val missing = decideAiRoute(AiProvider.DEEPSEEK, AiMode.FAST, true, true, false) as AiRouteDecision.Block
        assertEquals("personal_key_missing", missing.code)
        assertEquals(
            AiRouteDecision.Use(AiRoute.PERSONAL_DEEPSEEK),
            decideAiRoute(AiProvider.DEEPSEEK, AiMode.DEEP, true, false, true),
        )
    }

    @Test fun `local engine failure does not call cloud`() = runTest {
        val personalDeepSeek = FakeEngine()
        val local = FakeEngine(fail = true)
        val router = AiEngineRouter(
            personalDeepSeek = personalDeepSeek,
            local = local,
            provider = { AiProvider.LOCAL },
            personalKeyAvailable = { true },
            isOnline = { true },
            localState = { LocalModelState.Ready },
        )
        runCatching { router.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))).toList() }
        assertEquals(0, personalDeepSeek.calls)
        assertEquals(1, local.calls)
    }

    @Test fun `missing personal key never calls any engine`() = runTest {
        val personalDeepSeek = FakeEngine()
        val local = FakeEngine()
        val router = AiEngineRouter(
            personalDeepSeek = personalDeepSeek,
            local = local,
            provider = { AiProvider.DEEPSEEK },
            personalKeyAvailable = { false },
            isOnline = { true },
            localState = { LocalModelState.Ready },
        )
        runCatching { router.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))).toList() }
        assertEquals(0, personalDeepSeek.calls)
        assertEquals(0, local.calls)
    }

    @Test fun `locked unavailable quality model never falls back to ready fast model`() = runTest {
        val personalDeepSeek = FakeEngine()
        val local = FakeEngine()
        val requestedIds = mutableListOf<String>()
        val router = AiEngineRouter(
            personalDeepSeek = personalDeepSeek,
            local = local,
            provider = { AiProvider.LOCAL },
            personalKeyAvailable = { true },
            isOnline = { true },
            localState = { modelId ->
                requestedIds += modelId
                if (modelId == "qwen3.5-2b-mnn") LocalModelState.Ready else LocalModelState.Downloading(.5f, 1, 2)
            },
        )

        val failure = runCatching {
            router.stream(
                AiRequest(
                    AiMode.FAST,
                    listOf(AiConversationMessage("user", "test")),
                    requiresLocal = true,
                    localModelId = "qwen3.5-4b-mnn",
                ),
            ).toList()
        }.exceptionOrNull()

        assertTrue(failure?.message?.contains("锁定的本地模型") == true)
        assertEquals(listOf("qwen3.5-4b-mnn"), requestedIds)
        assertEquals(0, local.calls)
        assertEquals(0, personalDeepSeek.calls)
    }

    @Test fun `locked ready fast model routes locally`() = runTest {
        val local = FakeEngine()
        val router = AiEngineRouter(
            personalDeepSeek = FakeEngine(),
            local = local,
            provider = { AiProvider.LOCAL },
            personalKeyAvailable = { false },
            isOnline = { false },
            localState = { modelId -> if (modelId == "qwen3.5-2b-mnn") LocalModelState.Ready else LocalModelState.NotDownloaded },
        )

        router.stream(
            AiRequest(
                AiMode.FAST,
                listOf(AiConversationMessage("user", "test")),
                requiresLocal = true,
                localModelId = "qwen3.5-2b-mnn",
            ),
        ).toList()

        assertEquals(1, local.calls)
    }

    @Test fun `older flow completion does not clear a newer route using the same engine`() = runTest {
        val local = ControlledEngine()
        val router = AiEngineRouter(
            personalDeepSeek = FakeEngine(),
            local = local,
            provider = { AiProvider.LOCAL },
            personalKeyAvailable = { false },
            isOnline = { false },
            localState = { LocalModelState.Ready },
        )
        val request = AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")), localModelId = "qwen3.5-2b-mnn")

        val first = launch { router.stream(request).toList() }
        assertEquals(0, local.started.receive())
        val second = launch { router.stream(request).toList() }
        assertEquals(1, local.started.receive())

        local.complete(0)
        first.join()
        router.cancel()

        assertEquals(1, local.cancelCalls)
        local.complete(1)
        second.join()
    }

    private class FakeEngine(private val fail: Boolean = false) : AiEngine {
        var calls = 0
        override fun stream(request: AiRequest): Flow<AiEvent> = flow {
            calls++
            if (fail) error("local failed")
            emit(AiEvent.Done(1))
        }
        override fun cancel() = Unit
    }

    private class ControlledEngine : AiEngine {
        private val nextCall = AtomicInteger(0)
        private val completions = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
        val started = Channel<Int>(Channel.UNLIMITED)
        var cancelCalls = 0

        override fun stream(request: AiRequest): Flow<AiEvent> = flow {
            val call = nextCall.getAndIncrement()
            val completion = CompletableDeferred<Unit>()
            completions[call] = completion
            started.send(call)
            completion.await()
            emit(AiEvent.Done(1))
        }

        fun complete(call: Int) {
            completions.getValue(call).complete(Unit)
        }

        override fun cancel() {
            cancelCalls++
        }
    }
}
