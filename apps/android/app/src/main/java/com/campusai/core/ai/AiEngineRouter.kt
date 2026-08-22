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
        online -> cloudRoute(personalKeyAvailable)
        mode == AiMode.DEEP -> AiRouteDecision.Block("deep_requires_network", "DEEP 需要联网使用 DeepSeek。可以切换到本地快速模式。")
        localReady -> AiRouteDecision.Use(AiRoute.LOCAL)
        else -> AiRouteDecision.Block("offline_model_missing", "当前离线，且本地模型尚未下载。联网后下载模型，或恢复网络使用 DeepSeek。")
    }
    AiProvider.DEEPSEEK -> if (online) {
        cloudRoute(personalKeyAvailable)
    } else {
        AiRouteDecision.Block("deepseek_offline", "DeepSeek 云端需要网络连接。恢复网络后重试；不会自动切换到本地模型。")
    }
    AiProvider.LOCAL -> when {
        mode == AiMode.DEEP -> AiRouteDecision.Block("local_fast_only", "本地模型只提供快速模式，不会伪装成深度推理。请切换到 FAST。")
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
    private val localState: () -> LocalModelState,
) : AiEngine {
    private val active = AtomicReference<AiEngine?>(null)

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        val decision = decideAiRoute(
            provider = provider(),
            mode = request.mode,
            online = isOnline(),
            localReady = localState() == LocalModelState.Ready,
            personalKeyAvailable = personalKeyAvailable(),
        )
        val engine = engineFor(decision)
        active.set(engine)
        try {
            engine.stream(request).collect { emit(it) }
        } finally {
            active.compareAndSet(engine, null)
        }
    }

    fun streamCloudOnce(request: AiRequest): Flow<AiEvent> = flow {
        if (!isOnline()) throw AiRoutingException("deepseek_offline", "DeepSeek 云端需要网络连接。恢复网络后重试。")
        val engine = engineFor(cloudRoute(personalKeyAvailable()))
        active.set(engine)
        try {
            engine.stream(request).collect { emit(it) }
        } finally {
            active.compareAndSet(engine, null)
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
        active.getAndSet(null)?.cancel()
    }
}
