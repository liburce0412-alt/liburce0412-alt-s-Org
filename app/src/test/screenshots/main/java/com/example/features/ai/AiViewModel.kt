package com.example.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.TimeRecord
import com.example.core.network.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface AiReportUiState {
    object Idle : AiReportUiState
    object Loading : AiReportUiState
    data class Success(val report: String) : AiReportUiState
    data class Error(val message: String) : AiReportUiState
}

class AiViewModel(private val repository: AiRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AiReportUiState>(AiReportUiState.Idle)
    val uiState: StateFlow<AiReportUiState> = _uiState.asStateFlow()

    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun generateDailyReport(records: List<TimeRecord>) {
        generateReport("当日时间记录 (AI 日报)", records)
    }

    fun generateWeeklyReport(records: List<TimeRecord>) {
        generateReport("本周时间记录摘要 (AI 周报)", records)
    }

    fun generateMonthlyReport(records: List<TimeRecord>) {
        generateReport("本月时间记录趋势 (AI 月报)", records)
    }

    private fun generateReport(title: String, records: List<TimeRecord>) {
        viewModelScope.launch {
            _uiState.value = AiReportUiState.Loading
            
            // Map records cleanly to a readable JSON/Text block
            val recordsSummary = if (records.isEmpty()) {
                "【无时间记录】建议添加日常学习、项目开发和阅读活动日志，以便进行高级 AI 分析洞察！"
            } else {
                records.joinToString("\n") { r ->
                    "- [${r.category}] ${r.title}: ${format.format(Date(r.startTime))} 至 ${format.format(Date(r.endTime))} (时长: ${r.durationMinutes}分钟) 备注: ${r.remark}"
                }
            }

            val inputPayload = """
                类型: $title
                生成时刻: ${format.format(Date())}
                流水明细:
                $recordsSummary
            """.trimIndent()

            try {
                val reportResult = repository.analyzeTimeRecords(inputPayload)
                _uiState.value = AiReportUiState.Success(reportResult)
            } catch (e: Exception) {
                _uiState.value = AiReportUiState.Error(e.localizedMessage ?: "AI引擎请求超时")
            }
        }
    }

    fun clearReport() {
        _uiState.value = AiReportUiState.Idle
    }
}

class AiViewModelFactory(private val repository: AiRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiViewModel::class.java)) {
            return AiViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
