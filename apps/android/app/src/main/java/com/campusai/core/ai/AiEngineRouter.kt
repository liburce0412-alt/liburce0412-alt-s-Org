package com.campusai.core.ai

import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import com.campusai.core.model.LocalModelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicReference

enum class AiRoute { PERSONAL_DEEPSEEK, PERSONAL_GOOGLE_GEMINI, LOCAL }

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
    geminiKeyAvailable: Boolean = false,
): AiRouteDecision = when (provider) {
    AiProvider.AUTO -> when {
        localReady -> AiRouteDecision.Use(AiRoute.LOCAL)
        online && (personalKeyAvailable || geminiKeyAvailable) -> AiRouteDecision.Block(
            "local_model_not_ready",
            "本地模型尚未就绪。你可以先下载模型，或只为本次明确选择一个已配置的云端 Provider。",
            canUseCloudOnce = true,
        )
        else -> AiRouteDecision.Block("offline_model_missing", "本地模型尚未下载。请先在“我的 → AI 运行方式”下载并校验。")
    }
    AiProvider.DEEPSEEK -> if (online) {
        cloudRoute(CloudAiProvider.DEEPSEEK, personalKeyAvailable)
    } else {
        AiRouteDecision.Block("deepseek_offline", "DeepSeek 云端需要网络连接。恢复网络后重试；不会自动切换到本地模型。")
    }
    AiProvider.GOOGLE_GEMINI -> if (online) {
        cloudRoute(CloudAiProvider.GOOGLE_GEMINI, geminiKeyAvailable)
    } else {
        AiRouteDecision.Block("gemini_offline", "Google Gemini 云端需要网络连接。恢复网络后重试；不会自动切换到其他模型。")
    }
    AiProvider.LOCAL -> when {
        localReady -> AiRouteDecision.Use(AiRoute.LOCAL)
        else -> AiRouteDecision.Block("local_model_not_ready", "本地模型尚未就绪。请先在“我的 → AI 运行方式”下载并完成校验。", canUseCloudOnce = online)
    }
}

private fun cloudRoute(provider: CloudAiProvider, keyAvailable: Boolean): AiRouteDecision = when {
    keyAvailable -> AiRouteDecision.Use(
        when (provider) {
            CloudAiProvider.DEEPSEEK -> AiRoute.PERSONAL_DEEPSEEK
            CloudAiProvider.GOOGLE_GEMINI -> AiRoute.PERSONAL_GOOGLE_GEMINI
        },
    )
    else -> AiRouteDecision.Block(
        if (provider == CloudAiProvider.DEEPSEEK) "personal_key_missing" else "gemini_key_missing",
        "已选择“我的 ${provider.displayName} Key”，但设备上尚未保存 Key。请前往“我的 → AI 运行方式”保存后重试。",
    )
}

class AiEngineRouter(
    private val personalDeepSeek: AiEngine,
    private val local: AiEngine,
    private val provider: () -> AiProvider,
    private val personalKeyAvailable: () -> Boolean,
    private val isOnline: () -> Boolean,
    private val localState: (modelId: String) -> LocalModelState,
    private val personalGoogleGemini: AiEngine? = null,
    private val geminiKeyAvailable: () -> Boolean = { false },
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
            geminiKeyAvailable = geminiKeyAvailable(),
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

    fun streamCloudOnce(request: AiRequest, provider: AiProvider = AiProvider.DEEPSEEK): Flow<AiEvent> = flow {
        if (
            request.requiresLocal ||
            request.imagePaths.isNotEmpty() ||
            request.messages.any { it.attachmentPaths.isNotEmpty() || it.attachmentRefs.isNotEmpty() }
        ) {
            throw AiRoutingException("cloud_local_content_forbidden", "图片和其他仅限本机的内容不能发送到云端。")
        }
        val cloudProvider = CloudAiProvider.from(provider)
            ?: throw AiRoutingException("cloud_provider_invalid", "本次云端请求未指定受支持的 Provider。")
        if (!isOnline()) {
            val code = if (cloudProvider == CloudAiProvider.DEEPSEEK) "deepseek_offline" else "gemini_offline"
            throw AiRoutingException(code, "${cloudProvider.displayName} 云端需要网络连接。恢复网络后重试。")
        }
        val keyAvailable = if (cloudProvider == CloudAiProvider.DEEPSEEK) personalKeyAvailable() else geminiKeyAvailable()
        val engine = engineFor(cloudRoute(cloudProvider, keyAvailable))
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
            AiRoute.PERSONAL_GOOGLE_GEMINI -> personalGoogleGemini
                ?: throw AiRoutingException("gemini_not_configured", "Google Gemini 引擎尚未接入当前运行时。")
            AiRoute.LOCAL -> local
        }
        is AiRouteDecision.Block -> throw AiRoutingException(decision.code, decision.message, decision.canUseCloudOnce)
    }

    override fun cancel() {
        active.getAndSet(null)?.engine?.cancel()
    }
}
