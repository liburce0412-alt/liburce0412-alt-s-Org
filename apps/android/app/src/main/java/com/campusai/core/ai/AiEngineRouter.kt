package com.campusai.core.ai

import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import com.campusai.core.model.LocalModelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicReference

enum class AiRoute { PERSONAL_DEEPSEEK, LOCAL }

sealed interface AiRouteDecision {
    data class Use(val route: AiRoute) : AiRouteDecision
    data class Block(val code: String, val message: String, val canUseCloudOnce: Boolean = false) : AiRouteDecision
}

fun decideAiRoute(
    provider: AiProvider,
    mode: AiMode,
    online: Boolean,
    localReady: Boolean,
    personalKeyAvailable: Boolean = false,
): AiRouteDecision = when (provider) {
    AiProvider.AUTO -> when {
        localReady -> AiRouteDecision.Use(AiRoute.LOCAL)
        online && personalKeyAvailable -> AiRouteDecision.Block(
            "local_model_not_ready",
            "本地模型尚未就绪。你可以先下载模型，或只为本次明确使用 DeepSeek。",
            canUseCloudOnce = true,
        )
        else -> AiRouteDecision.Block("offline_model_missing", "本地模型尚未下载。请先在“我的 → AI 运行方式”下载并校验。")
    }
    AiProvider.DEEPSEEK -> if (online) {
        cloudRoute(personalKeyAvailable)
    } else {
        AiRouteDecision.Block("deepseek_offline", "DeepSeek 云端需要网络连接。恢复网络后重试；不会自动切换到本地模型。")
    }
    AiProvider.LOCAL -> when {
        localReady -> AiRouteDecision.Use(AiRoute.LOCAL)
        else -> AiRouteDecision.Block("local_model_not_ready", "本地模型尚未就绪。请先在“我的 → AI 运行方式”下载并完成校验。", canUseCloudOnce = online)
    }
}

private fun cloudRoute(personalKeyAvailable: Boolean): AiRouteDecision = when {
    personalKeyAvailable -> AiRouteDecision.Use(AiRoute.PERSONAL_DEEPSEEK)
    else -> AiRouteDecision.Block(
        "personal_key_missing",
        "已选择“我的 DeepSeek Key”，但设备上尚未保存 Key。请前往“我的 → AI 运行方式”保存后重试。",
    )
}

class AiEngineRouter(
    private val personalDeepSeek: AiEngine,
    private val local: AiEngine,
    private val provider: () -> AiProvider,
    private val personalKeyAvailable: () -> Boolean,
    private val isOnline: () -> Boolean,
    private val localState: (modelId: String) -> LocalModelState,
) : AiEngine {
    private data class ActiveRoute(val engine: AiEngine)

    private val active = AtomicReference<ActiveRoute?>(null)

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        val selectedLocalState = localState(request.localModelId)
        val decision = if (request.requiresLocal) {
            if (selectedLocalState == LocalModelState.Ready) AiRouteDecision.Use(AiRoute.LOCAL)
            else AiRouteDecision.Block("local_required", "本会话锁定的本地模型尚未就绪。请完成该模型下载与校验，或新建会话后选择其他档位。")
        } else decideAiRoute(
            provider = provider(),
            mode = request.mode,
            online = isOnline(),
            localReady = selectedLocalState == LocalModelState.Ready,
            personalKeyAvailable = personalKeyAvailable(),
        )
        val engine = engineFor(decision)
        val route = ActiveRoute(engine)
        active.set(route)
        try {
            engine.stream(request).collect { emit(it) }
        } finally {
            active.compareAndSet(route, null)
        }
    }

    fun streamCloudOnce(request: AiRequest): Flow<AiEvent> = flow {
        if (!isOnline()) throw AiRoutingException("deepseek_offline", "DeepSeek 云端需要网络连接。恢复网络后重试。")
        val engine = engineFor(cloudRoute(personalKeyAvailable()))
        val route = ActiveRoute(engine)
        active.set(route)
        try {
            engine.stream(request).collect { emit(it) }
        } finally {
            active.compareAndSet(route, null)
        }
    }

    private fun engineFor(decision: AiRouteDecision): AiEngine = when (decision) {
        is AiRouteDecision.Use -> when (decision.route) {
            AiRoute.PERSONAL_DEEPSEEK -> personalDeepSeek
            AiRoute.LOCAL -> local
        }
        is AiRouteDecision.Block -> throw AiRoutingException(decision.code, decision.message, decision.canUseCloudOnce)
    }

    override fun cancel() {
        active.getAndSet(null)?.engine?.cancel()
    }
}
