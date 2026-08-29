package com.campusai.features.ai

import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiExecutionEngine
import com.campusai.core.ai.ResolvedExecution
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import com.campusai.core.model.AiReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGenerationTransactionTest {
    @Test fun `partial output followed by eof is not a complete turn`() {
        val tracker = AiGenerationCompletionTracker()

        tracker.accept(AiEvent.Delta("已经显示的片段"))

        assertFalse(tracker.completedNormally)
    }

    @Test fun `typed terminal errors and failures invalidate an observed done`() {
        val typedError = AiGenerationCompletionTracker().apply {
            accept(AiEvent.Delta("片段"))
            accept(AiEvent.Error("provider_stream_malformed", "流损坏"))
        }
        val lateFailure = AiGenerationCompletionTracker().apply {
            accept(AiEvent.Done(1))
            fail()
        }

        assertFalse(typedError.completedNormally)
        assertFalse(lateFailure.completedNormally)
    }

    @Test fun `only a clean done commits and stale image imports lose ownership`() {
        val tracker = AiGenerationCompletionTracker().apply {
            accept(AiEvent.Delta("完整回答"))
            accept(AiEvent.Done(5))
        }
        val imports = AiImageImportGate()
        val oldTicket = imports.begin("old-conversation")
        imports.invalidate()
        val newTicket = imports.begin("new-conversation")

        assertTrue(tracker.completedNormally)
        assertFalse(imports.owns(oldTicket, "new-conversation"))
        assertTrue(imports.owns(newTicket, "new-conversation"))
    }

    @Test fun `opening cloud history keeps explicitly selected provider`() {
        val history = AiReport(
            id = "history-1",
            provider = AiProvider.GOOGLE_GEMINI,
            mode = AiMode.DEEP,
            model = "gemini-history-model",
            executionEngine = AiExecutionEngine.CLOUD_OPENAI_COMPATIBLE.name,
            requestId = "request-history",
            title = "历史",
            summary = "回答",
            messagesJson = "[]",
            createdAt = 1L,
            updatedAt = 2L,
        )

        val opened = AiUiState(provider = AiProvider.DEEPSEEK).withOpenedConversation(
            report = history,
            messages = emptyList(),
            selectedProvider = AiProvider.DEEPSEEK,
            localModelId = null,
        )

        assertTrue(opened.provider == AiProvider.DEEPSEEK)
        assertTrue(opened.resolvedProvider == AiProvider.GOOGLE_GEMINI)
        assertTrue(opened.execution?.provider == AiProvider.GOOGLE_GEMINI)
    }

    @Test fun `failed turn keeps the execution that actually emitted metadata`() {
        val previous = ResolvedExecution(
            provider = AiProvider.DEEPSEEK,
            model = "deepseek-chat",
            engine = AiExecutionEngine.CLOUD_OPENAI_COMPATIBLE,
            requestId = "previous",
        )
        val actual = ResolvedExecution(
            provider = AiProvider.GOOGLE_GEMINI,
            model = "gemini-2.5-flash",
            engine = AiExecutionEngine.CLOUD_OPENAI_COMPATIBLE,
            requestId = "failed-request",
        )

        val restored = AiUiState(
            execution = actual,
            model = actual.model,
            resolvedProvider = actual.provider,
        ).withFailureExecutionFallback(previous, previous.model, previous.provider)

        assertTrue(restored.execution == actual)
        assertTrue(restored.model == actual.model)
        assertTrue(restored.resolvedProvider == actual.provider)
    }

    @Test fun `failed turn without metadata falls back to the previous identity`() {
        val previous = ResolvedExecution(
            provider = AiProvider.DEEPSEEK,
            model = "deepseek-chat",
            engine = AiExecutionEngine.CLOUD_OPENAI_COMPATIBLE,
            requestId = "previous",
        )

        val restored = AiUiState().withFailureExecutionFallback(previous, previous.model, previous.provider)

        assertTrue(restored.execution == previous)
        assertTrue(restored.model == previous.model)
        assertTrue(restored.resolvedProvider == previous.provider)
    }
}
