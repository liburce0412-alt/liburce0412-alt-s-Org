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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEngineRouterTest {
    @Test fun `auto follows online and offline rules`() {
        assertEquals(AiRouteDecision.Use(AiRoute.DEEPSEEK), decideAiRoute(AiProvider.AUTO, AiMode.FAST, true, false))
        assertEquals(AiRouteDecision.Use(AiRoute.LOCAL), decideAiRoute(AiProvider.AUTO, AiMode.FAST, false, true))
        assertEquals("offline_model_missing", (decideAiRoute(AiProvider.AUTO, AiMode.FAST, false, false) as AiRouteDecision.Block).code)
        assertEquals("deep_requires_network", (decideAiRoute(AiProvider.AUTO, AiMode.DEEP, false, true) as AiRouteDecision.Block).code)
    }

    @Test fun `explicit providers never silently fall back`() {
        assertEquals("deepseek_offline", (decideAiRoute(AiProvider.DEEPSEEK, AiMode.FAST, false, true) as AiRouteDecision.Block).code)
        assertEquals("local_fast_only", (decideAiRoute(AiProvider.LOCAL, AiMode.DEEP, true, true) as AiRouteDecision.Block).code)
        val missing = decideAiRoute(AiProvider.LOCAL, AiMode.FAST, true, false) as AiRouteDecision.Block
        assertTrue(missing.canUseCloudOnce)
    }

    @Test fun `local engine failure does not call cloud`() = runTest {
        val cloud = FakeEngine()
        val local = FakeEngine(fail = true)
        val router = AiEngineRouter(cloud, local, { AiProvider.LOCAL }, { true }) { LocalModelState.Ready }
        runCatching { router.stream(AiRequest(AiMode.FAST, listOf(AiConversationMessage("user", "test")))).toList() }
        assertEquals(0, cloud.calls)
        assertEquals(1, local.calls)
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
}
