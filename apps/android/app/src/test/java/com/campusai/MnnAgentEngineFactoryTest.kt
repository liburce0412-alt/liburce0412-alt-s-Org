package com.campusai

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import androidx.test.core.app.ApplicationProvider
import com.campusai.core.agent.MnnAgentEngineFactory
import com.campusai.core.agent.MnnPromptExecutor
import com.campusai.core.ai.AiEngine
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.localai.LocalModelManifest
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MnnAgentEngineFactoryTest {
    @Test fun `koog metadata and delegate request use the locked fast manifest`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val delegate = RecordingEngine()
        val engine = MnnAgentEngineFactory.create(delegate) { modelId ->
            LocalModelManifest.load(context, modelId.ifBlank { LocalModelManifest.DEFAULT_MODEL_ID })
        }

        val events = engine.stream(
            AiRequest(
                mode = AiMode.FAST,
                messages = listOf(AiConversationMessage("user", "hello")),
                localModelId = "qwen3.5-2b-mnn",
                sessionId = "fast-session",
            ),
        ).toList()

        assertTrue((events.first() as AiEvent.Meta).model.contains("Qwen3.5-2B"))
        assertEquals("qwen3.5-2b-mnn", delegate.lastRequest?.localModelId)
    }

    @Test fun `executor rejects an unbound prompt instead of inventing a fast request`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manifest = LocalModelManifest.load(context, "qwen3.5-2b-mnn")
        val delegate = RecordingEngine()
        val executor = MnnPromptExecutor(delegate) { manifest }
        val prompt = Prompt(listOf(Message.User("hello", RequestMetaInfo.Empty)), "unbound")
        val model = LLModel(
            LLMProvider.Alibaba,
            manifest.id,
            contextLength = manifest.contextTokens.toLong(),
            maxOutputTokens = manifest.maxOutputTokens.toLong(),
        )

        val failure = runCatching { executor.executeStreaming(prompt, model, emptyList()).toList() }.exceptionOrNull()

        assertTrue(failure?.message?.contains("No local request is bound") == true)
        assertEquals(null, delegate.lastRequest)
    }

    private class RecordingEngine : AiEngine {
        var lastRequest: AiRequest? = null
        override fun stream(request: AiRequest): Flow<AiEvent> = flow {
            lastRequest = request
            emit(AiEvent.Done(1))
        }
        override fun cancel() = Unit
    }
}
