package com.campusai.core.agent

import android.os.Build
import com.campusai.core.ai.AiEngine
import com.campusai.core.localai.LocalModelManifest

/** Koog 1.0's Android artifact requires API 35; older devices keep the raw MNN path. */
object MnnAgentEngineFactory {
    fun create(engine: AiEngine, manifestFor: (modelId: String) -> LocalModelManifest): AiEngine {
        if (Build.VERSION.SDK_INT < 35) return engine
        return runCatching {
            val executorClass = Class.forName("com.campusai.core.agent.MnnPromptExecutor")
            val executor = executorClass.getConstructor(AiEngine::class.java, Function1::class.java).newInstance(engine, manifestFor)
            val adapterClass = Class.forName("com.campusai.core.agent.KoogMnnAiEngine")
            adapterClass.getConstructor(executorClass, Function1::class.java)
                .newInstance(executor, manifestFor) as AiEngine
        }.getOrDefault(engine)
    }
}
