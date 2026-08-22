package com.campusai.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusai.core.database.AiReportEntity
import com.campusai.core.database.CampusDao
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import com.campusai.core.model.AiReport
import com.campusai.core.network.AiEdgeClient
import com.campusai.core.network.AiStreamEvent
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

data class AiUiState(
    val mode: AiMode = AiMode.FAST,
    val messages: List<AiConversationMessage> = emptyList(),
    val streaming: Boolean = false,
    val stage: String = "",
    val model: String = "",
    val elapsedMs: Long = 0,
    val error: String? = null,
)

class AiViewModel(private val dao: CampusDao, private val client: AiEdgeClient = AiEdgeClient()) : ViewModel() {
    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()
    val history: StateFlow<List<AiReport>> = dao.getAiReportsFlow().map { rows -> rows.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMode(mode: AiMode) { if (!_state.value.streaming) _state.value = _state.value.copy(mode=mode,error=null) }
    fun clearError() { _state.value = _state.value.copy(error=null) }
    fun newConversation() { if (!_state.value.streaming) _state.value = AiUiState(mode=_state.value.mode) }

    fun send(prompt: String, timeMinutes: Long, recordCount: Int) {
        if (prompt.isBlank() || _state.value.streaming) return
        val initial = _state.value.messages + AiConversationMessage("user",prompt.trim()) + AiConversationMessage("assistant","")
        _state.value = _state.value.copy(messages=initial,streaming=true,stage="连接安全网关",error=null,elapsedMs=0)
        viewModelScope.launch {
            runCatching {
                client.stream(
                    mode = if (_state.value.mode==AiMode.FAST) "fast" else "deep",
                    messages = initial.dropLast(1).map { it.role to it.content },
                    context = JSONObject().put("locale","zh-CN").put("timeSummary",JSONObject().put("todayMinutes",timeMinutes).put("recordCount",recordCount)),
                ) { event ->
                    when(event) {
                        is AiStreamEvent.Meta -> _state.value=_state.value.copy(model=event.model)
                        is AiStreamEvent.Status -> _state.value=_state.value.copy(stage=stageLabel(event.stage),elapsedMs=event.elapsedMs)
                        is AiStreamEvent.Delta -> _state.value=_state.value.copy(messages=_state.value.messages.toMutableList().also { list -> val last=list.last();list[list.lastIndex]=last.copy(content=last.content+event.text) })
                        is AiStreamEvent.Done -> _state.value=_state.value.copy(streaming=false,stage="",elapsedMs=event.elapsedMs)
                        is AiStreamEvent.Error -> _state.value=_state.value.copy(streaming=false,error=event.message,stage="")
                    }
                }
            }.onFailure { _state.value=_state.value.copy(streaming=false,stage="",error=it.message ?: "连接中断，请重试。") }
            val final = _state.value
            val summary = final.messages.lastOrNull { it.role=="assistant" }?.content.orEmpty()
            if (summary.isNotBlank()) {
                val report=AiReport(UUID.randomUUID().toString(),final.mode,prompt.take(42),summary,JSONArray(final.messages.map { JSONObject().put("role",it.role).put("content",it.content) }).toString(),System.currentTimeMillis())
                dao.insertAiReport(AiReportEntity.fromDomain(report))
            }
        }
    }

    private fun stageLabel(stage:String)=when(stage){"planning"->"整理公开阶段与数据依据";"responding"->"生成可执行建议";else->"处理中"}
}

class AiViewModelFactory(private val dao: CampusDao):ViewModelProvider.Factory{
    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=AiViewModel(dao) as T
}
