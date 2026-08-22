package com.campusai.features.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusai.core.ai.AiEngine
import com.campusai.core.ai.AiEngineRouter
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.ai.AiRoutingException
import com.campusai.core.ai.PersonalDeepSeekAiEngine
import com.campusai.core.ai.NetworkAvailability
import com.campusai.core.database.AiReportEntity
import com.campusai.core.database.CampusDao
import com.campusai.core.localai.LocalMnnAiEngine
import com.campusai.core.localai.LocalModelManager
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiProvider
import com.campusai.core.model.AiReport
import com.campusai.core.model.CourseSchedule
import com.campusai.core.model.TimeRecord
import com.campusai.core.preferences.UserPreferencesRepository
import com.campusai.core.network.PersonalDeepSeekClient
import com.campusai.core.security.PersonalDeepSeekKeyStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class AiUiState(
    val mode: AiMode = AiMode.FAST,
    val provider: AiProvider = AiProvider.AUTO,
    val messages: List<AiConversationMessage> = emptyList(),
    val streaming: Boolean = false,
    val stage: String = "",
    val model: String = "",
    val elapsedMs: Long = 0,
    val error: String? = null,
    val errorCode: String? = null,
    val canUseCloudOnce: Boolean = false,
    val pendingCloudPrompt: String? = null,
    val pendingContextJson: String? = null,
)

class AiViewModel(
    private val dao: CampusDao,
    context: Context,
    private val preferences: UserPreferencesRepository,
    private val modelManager: LocalModelManager,
    private val localEngine: LocalMnnAiEngine,
    private val personalKeyStore: PersonalDeepSeekKeyStore,
    private val personalDeepSeekEngine: AiEngine = PersonalDeepSeekAiEngine(PersonalDeepSeekClient(personalKeyStore)),
) : ViewModel() {
    private val provider = AtomicReference(AiProvider.AUTO)
    private val network = NetworkAvailability(context.applicationContext)
    private val router = AiEngineRouter(
        personalDeepSeek = personalDeepSeekEngine,
        local = localEngine,
        provider = provider::get,
        personalKeyAvailable = personalKeyStore::hasKey,
        isOnline = network::isOnline,
        localState = { modelManager.state.value },
    )
    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()
    val history: StateFlow<List<AiReport>> = dao.getAiReportsFlow().map { rows -> rows.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var generation: Job? = null

    init {
        viewModelScope.launch {
            preferences.preferences.collect { value ->
                provider.set(value.aiProvider)
                _state.value = _state.value.copy(provider = value.aiProvider)
            }
        }
    }

    fun setMode(mode: AiMode) { if (!_state.value.streaming) _state.value = _state.value.copy(mode = mode, error = null, errorCode = null) }
    fun setProvider(value: AiProvider) { if (!_state.value.streaming) viewModelScope.launch { preferences.setAiProvider(value) } }
    fun clearError() { _state.value = _state.value.copy(error = null, errorCode = null, canUseCloudOnce = false, pendingCloudPrompt = null, pendingContextJson = null) }
    fun newConversation() { if (!_state.value.streaming) _state.value = AiUiState(mode = _state.value.mode, provider = provider.get()) }

    fun send(prompt: String, records: List<TimeRecord>, courses: List<CourseSchedule>) = sendInternal(
        CampusAiTaskFactory.create(CampusAiTask.CHAT, records, courses, prompt), cloudOnce = false,
    )

    fun sendTask(task: CampusAiTask, records: List<TimeRecord>, courses: List<CourseSchedule>, userInput: String = "") =
        sendInternal(CampusAiTaskFactory.create(task, records, courses, userInput), cloudOnce = false)

    fun useCloudOnce() {
        val prompt = _state.value.pendingCloudPrompt ?: return
        val context = JSONObject(_state.value.pendingContextJson ?: "{}")
        sendInternal(CampusAiPayload(prompt, context), cloudOnce = true)
    }

    fun cancel() {
        router.cancel()
        personalDeepSeekEngine.cancel()
        localEngine.cancel()
        generation?.cancel()
        generation = null
        _state.value = _state.value.copy(streaming = false, stage = "", error = "已停止本次生成。", errorCode = "cancelled")
    }

    private fun sendInternal(payload: CampusAiPayload, cloudOnce: Boolean) {
        val prompt = payload.prompt
        if (prompt.isBlank() || _state.value.streaming) return
        if (cloudOnce && !network.isOnline()) {
            _state.value = _state.value.copy(error = "当前没有可用网络，无法在本次切换到 DeepSeek。", errorCode = "deepseek_offline")
            return
        }
        val existing = if (cloudOnce) {
            val withoutPlaceholder = _state.value.messages.dropLastWhile { it.role == "assistant" && it.content.isBlank() }
            if (withoutPlaceholder.lastOrNull()?.let { it.role == "user" && it.content == prompt.trim() } == true) withoutPlaceholder.dropLast(1) else withoutPlaceholder
        } else _state.value.messages
        val initial = existing + AiConversationMessage("user", prompt.trim()) + AiConversationMessage("assistant", "")
        _state.value = _state.value.copy(
            messages = initial,
            streaming = true,
            stage = if (cloudOnce) "正在使用你的 DeepSeek Key" else "选择运行方式",
            error = null,
            errorCode = null,
            canUseCloudOnce = false,
            pendingCloudPrompt = null,
            pendingContextJson = null,
            elapsedMs = 0,
        )
        val context = payload.structuredContext.put("locale", "zh-CN")
        val request = AiRequest(
            mode = _state.value.mode,
            messages = initial.dropLast(1),
            structuredContextJson = context.toString(),
            maxOutputTokens = 512,
        )
        generation = viewModelScope.launch {
            runCatching {
                if (cloudOnce) router.streamCloudOnce(request).collect(::consumeEvent)
                else router.stream(request).collect(::consumeEvent)
            }.onFailure { error ->
                val routing = error as? AiRoutingException
                _state.value = _state.value.copy(
                    streaming = false,
                    stage = "",
                    error = error.message ?: "生成中断，请重试。",
                    errorCode = routing?.code ?: "generation_failed",
                    canUseCloudOnce = routing?.canUseCloudOnce == true,
                    pendingCloudPrompt = prompt.takeIf { routing?.canUseCloudOnce == true },
                    pendingContextJson = context.toString().takeIf { routing?.canUseCloudOnce == true },
                )
            }
            saveReport(prompt)
            generation = null
        }
    }

    private fun consumeEvent(event: AiEvent) {
        when (event) {
            is AiEvent.Meta -> _state.value = _state.value.copy(model = event.model)
            is AiEvent.Status -> _state.value = _state.value.copy(stage = stageLabel(event.stage), elapsedMs = event.elapsedMs)
            is AiEvent.Delta -> _state.value = _state.value.copy(messages = _state.value.messages.toMutableList().also { list ->
                val last = list.last()
                list[list.lastIndex] = last.copy(content = last.content + event.text)
            })
            is AiEvent.Done -> _state.value = _state.value.copy(streaming = false, stage = "", elapsedMs = event.elapsedMs)
            is AiEvent.Error -> _state.value = _state.value.copy(streaming = false, error = event.message, errorCode = event.code, stage = "")
        }
    }

    private suspend fun saveReport(prompt: String) {
        val final = _state.value
        val summary = final.messages.lastOrNull { it.role == "assistant" }?.content.orEmpty()
        if (summary.isBlank()) return
        val report = AiReport(
            UUID.randomUUID().toString(), final.mode, prompt.take(42), summary,
            JSONArray(final.messages.map { JSONObject().put("role", it.role).put("content", it.content) }).toString(),
            System.currentTimeMillis(),
        )
        dao.insertAiReport(AiReportEntity.fromDomain(report))
    }

    private fun stageLabel(stage: String) = when (stage) {
        "planning" -> "整理公开阶段与数据依据"
        "responding" -> "生成可执行建议"
        "local_loading" -> "在设备上加载模型"
        "local_generating" -> "完全离线生成"
        else -> "处理中"
    }

    override fun onCleared() {
        router.cancel()
        super.onCleared()
    }
}

class AiViewModelFactory(
    private val dao: CampusDao,
    private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val modelManager: LocalModelManager,
    private val localEngine: LocalMnnAiEngine,
    private val personalKeyStore: PersonalDeepSeekKeyStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AiViewModel(dao, context, preferences, modelManager, localEngine, personalKeyStore) as T
}
