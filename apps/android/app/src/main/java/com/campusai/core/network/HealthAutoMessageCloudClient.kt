package com.campusai.core.network

import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.ai.CloudAiProvider
import com.campusai.core.ai.CloudDailyHealthSummary
import com.campusai.core.ai.CloudHealthDisclosure
import com.campusai.core.ai.ResolvedExecution
import com.campusai.core.automation.AutoMessageBatch
import com.campusai.core.automation.HealthAutoMessageClient
import com.campusai.core.automation.HealthTaskException
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import com.campusai.core.security.PersonalAiProviderStore

class PersonalCloudHealthAutoMessageClient(
    private val store: PersonalAiProviderStore,
) : HealthAutoMessageClient {
    override suspend fun validate(provider: CloudAiProvider, modelId: String): Result<Unit> = runCatching {
        val lockedModel = requireLockedModel(provider, modelId)
        val client = lockedClient(provider, lockedModel)
        try {
            validateLockedConnection(client, lockedModel)
        } catch (failure: CloudProviderException) {
            throw HealthTaskException(failure.code, failure.message)
        } finally {
            client.cancel()
        }
    }

    override suspend fun generate(
        provider: CloudAiProvider,
        modelId: String,
        summary: CloudDailyHealthSummary,
        withSubmissionLease: suspend (submit: () -> Unit) -> Boolean,
    ): Result<AutoMessageBatch> = runCatching {
        val lockedModel = requireLockedModel(provider, modelId)
        val client = lockedClient(provider, lockedModel)
        try {
            validateLockedConnection(client, lockedModel)
            val output = StringBuilder()
            var completed = false
            var execution: ResolvedExecution? = null
            client.stream(
                request = request(summary),
                withSubmissionLease = withSubmissionLease,
                onEvent = { event ->
                    when (event) {
                        is AiEvent.Meta -> {
                            if (event.provider != provider.appProvider || event.model != lockedModel) {
                                throw HealthTaskException("task_model_mismatch", "定时任务模型锁定校验失败。")
                            }
                            execution = event.execution
                        }
                        is AiEvent.Delta -> {
                            if (output.length + event.text.length > MAX_OUTPUT_CHARS) {
                                throw HealthTaskException("task_output_too_large", "AI 返回的自动消息过长。")
                            }
                            output.append(event.text)
                        }
                        is AiEvent.Done -> completed = true
                        is AiEvent.Error -> throw HealthTaskException(event.code, event.message)
                        is AiEvent.ToolCallRequested -> throw HealthTaskException(
                            "task_tool_call_rejected",
                            "定时健康任务不允许调用工具。",
                        )
                        else -> Unit
                    }
                },
            )
            if (!completed) throw HealthTaskException("task_stream_incomplete", "AI 自动消息在完成前中断。")
            val parsed = AutoMessageBatch.parse(output.toString()).getOrElse {
                throw HealthTaskException("task_output_invalid", "AI 返回的自动消息格式无效。")
            }
            parsed.copy(execution = execution ?: throw HealthTaskException(
                "task_execution_missing",
                "AI 自动消息缺少执行标识。",
            ))
        } catch (failure: CloudProviderException) {
            throw HealthTaskException(failure.code, failure.message)
        } finally {
            client.cancel()
        }
    }

    private fun requireLockedModel(provider: CloudAiProvider, modelId: String): String {
        val lockedModel = provider.normalizeModelId(modelId)
        if (!provider.acceptsModelId(lockedModel)) {
            throw HealthTaskException("task_model_invalid", "定时任务保存的模型无效。")
        }
        if (!store.hasCredential(provider)) {
            throw HealthTaskException("task_provider_key_missing", "定时任务的 Provider Key 已不可用。")
        }
        return lockedModel
    }

    private fun lockedClient(provider: CloudAiProvider, modelId: String) = PersonalCloudClient(
        provider = provider,
        credential = { store.readCredential(provider)?.value.orEmpty() },
        selectedModel = { modelId },
        baseUrl = { store.baseUrl(provider) },
        client = defaultCloudHttpClient(),
    )

    private suspend fun validateLockedConnection(client: PersonalCloudClient, lockedModel: String) {
        val connection = client.validateConnection(lockedModel)
        if (connection.selectedModelId != lockedModel || connection.models.none { it.id == lockedModel }) {
            throw HealthTaskException("task_model_unavailable", "定时任务保存的模型当前不可用。")
        }
    }

    private fun request(summary: CloudDailyHealthSummary): AiRequest = AiRequest(
        mode = AiMode.FAST,
        messages = listOf(AiConversationMessage("user", CX330_LIGHT_PROMPT)),
        maxOutputTokens = 96,
        caesarToolsJson = "[]",
        requiresLocal = false,
        sessionId = "automation-health",
        userPrompt = "",
        cloudHealthDisclosure = CloudHealthDisclosure.Included(summary),
    )

    private companion object {
        const val MAX_OUTPUT_CHARS = 2_048
        const val CX330_LIGHT_PROMPT = """
            只根据 health_summary 生成轻松、自然的中文短消息。
            语气像熟人聊天：短句、少句号、不说教，不模仿任何真实人。
            不做医疗诊断，不猜测缺失数据，不提 Provider、模型或任务。
            只输出一个 JSON 对象，不要 Markdown：{"messages":["短句1","短句2"]}
            messages 必须恰好 2 或 3 条，每条 4–28 个中文字符，总长不超过 84 字。
        """
    }
}
