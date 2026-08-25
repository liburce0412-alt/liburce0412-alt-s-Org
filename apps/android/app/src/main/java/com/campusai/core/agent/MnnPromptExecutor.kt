package com.campusai.core.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.campusai.core.ai.AiEngine
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.localai.LocalModelManifest
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

/** Koog executor backed by the app-private MNN engine. No prompt or image is uploaded. */
class MnnPromptExecutor(
    private val engine: AiEngine,
    private val manifestFor: (modelId: String) -> LocalModelManifest,
) : PromptExecutor() {
    private val executionContexts = ConcurrentHashMap<String, AiRequest>()

    fun bind(promptId: String, request: AiRequest) {
        executionContexts[promptId] = request
    }

    fun unbind(promptId: String) {
        executionContexts.remove(promptId)
    }

    fun cancelGeneration() = engine.cancel()

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        val parts = mutableListOf<MessagePart.ResponsePart>()
        val text = StringBuilder()
        var finishReason = "stop"
        executeStreaming(prompt, model, tools).collect { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> text.append(frame.text)
                is StreamFrame.TextComplete -> if (text.isEmpty()) text.append(frame.text)
                is StreamFrame.ToolCallComplete -> {
                    parts += MessagePart.Tool.Call(frame.id, frame.name, frame.content)
                    finishReason = "tool_calls"
                }
                else -> Unit
            }
        }
        if (text.isNotEmpty()) parts.add(0, MessagePart.Text(text.toString()))
        return Message.Assistant(parts, ResponseMetaInfo.Empty, finishReason)
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = flow {
        val base = checkNotNull(executionContexts.remove(prompt.id)) { "No local request is bound to prompt ${prompt.id}" }
        val request = base.copy(
            messages = prompt.messages.mapNotNull(::toCampusMessage),
        )
        engine.stream(request).collect { event ->
            when (event) {
                is AiEvent.Delta -> emit(StreamFrame.TextDelta(event.text))
                is AiEvent.ToolCallRequested -> emit(StreamFrame.ToolCallComplete(null, event.name, event.argumentsJson))
                is AiEvent.Done -> emit(StreamFrame.End("stop", ResponseMetaInfo.Empty))
                is AiEvent.Error -> error("${event.code}: ${event.message}")
                else -> Unit
            }
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("The private MNN executor does not delegate moderation")

    override suspend fun models(): List<LLModel> = LocalModelManifest.availableModelIds()
        .map(manifestFor)
        .map(LocalModelManifest::toKoogModel)
    override fun close() = Unit

    private fun toCampusMessage(message: Message): AiConversationMessage? = when (message) {
        is Message.System -> AiConversationMessage("system", message.textContent())
        is Message.User -> {
            val tool = message.parts.filterIsInstance<MessagePart.Tool.Result>().lastOrNull()
            if (tool != null) AiConversationMessage("tool", tool.output) else AiConversationMessage("user", message.textContent())
        }
        is Message.Assistant -> AiConversationMessage("assistant", message.parts.filterIsInstance<MessagePart.Tool.Call>().lastOrNull()?.toQwenXml() ?: message.textContent())
    }

    private fun MessagePart.Tool.Call.toQwenXml(): String {
        val json = runCatching { JSONObject(args) }.getOrElse { JSONObject() }
        val parameters = json.keys().asSequence().joinToString("") { key ->
            "<parameter=${escape(key)}>${escape(json.opt(key)?.toString().orEmpty())}</parameter>"
        }
        return "<tool_call><function=${escape(tool)}>$parameters</function></tool_call>"
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

}

/** Keeps the current AiEngine API while routing all local generations through Koog's executor contract. */
class KoogMnnAiEngine(
    private val executor: MnnPromptExecutor,
    private val manifestFor: (modelId: String) -> LocalModelManifest,
) : AiEngine {
    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        val manifest = manifestFor(request.localModelId)
        val model = manifest.toKoogModel()
        val promptId = request.sessionId.ifBlank { UUID.randomUUID().toString() } + ":" + UUID.randomUUID().toString().take(8)
        val prompt = Prompt(request.messages.map(::toKoogMessage), promptId)
        executor.bind(promptId, request)
        try {
            emit(AiEvent.Meta("${manifest.displayName} · Koog/MNN", AiProvider.LOCAL))
            executor.executeStreaming(prompt, model, emptyList()).collect { frame ->
                when (frame) {
                    is StreamFrame.TextDelta -> emit(AiEvent.Delta(frame.text))
                    is StreamFrame.TextComplete -> emit(AiEvent.Delta(frame.text))
                    is StreamFrame.ToolCallComplete -> emit(AiEvent.ToolCallRequested(frame.name, frame.content, frame.toQwenXml()))
                    is StreamFrame.End -> emit(AiEvent.Done(0, frame.metaInfo.inputTokensCount?.toLong(), frame.metaInfo.outputTokensCount?.toLong()))
                    else -> Unit
                }
            }
        } finally {
            executor.unbind(promptId)
        }
    }

    override fun cancel() = executor.cancelGeneration()

    private fun toKoogMessage(message: AiConversationMessage): Message = when (message.role) {
        "system" -> Message.System(message.content, RequestMetaInfo.Empty)
        "assistant" -> Message.Assistant(message.content, ResponseMetaInfo.Empty)
        "tool" -> Message.User(MessagePart.Tool.Result(null, "caesar", message.content), RequestMetaInfo.Empty)
        else -> Message.User(message.content, RequestMetaInfo.Empty)
    }

    private fun StreamFrame.ToolCallComplete.toQwenXml(): String {
        val json = runCatching { JSONObject(content) }.getOrElse { JSONObject() }
        val parameters = json.keys().asSequence().joinToString("") { key -> "<parameter=$key>${json.opt(key)}</parameter>" }
        return "<tool_call><function=$name>$parameters</function></tool_call>"
    }

}

private fun LocalModelManifest.toKoogModel(): LLModel = LLModel(
    LLMProvider.Alibaba,
    id,
    contextLength = contextTokens.toLong(),
    maxOutputTokens = maxOutputTokens.toLong(),
)
