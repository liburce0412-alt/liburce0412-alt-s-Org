package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TimeRecord
import com.example.features.ai.AiReportUiState
import com.example.features.ai.AiViewModel
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassmorphicCard
import com.example.ui.components.StreamingTypeText

@Composable
fun AiAnalysisScreen(
    aiViewModel: AiViewModel,
    timeRecords: List<TimeRecord>
) {
    val reportState by aiViewModel.uiState.collectAsStateWithLifecycle()

    AuroraBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Elegant Header Card
            item {
                AiConsultantHeader()
            }

            // Quick Actions Hub
            item {
                ReportActionGrid(
                    records = timeRecords,
                    aiViewModel = aiViewModel
                )
            }

            // Dynamic Report Content Render
            item {
                AiReportContentWrapper(
                    reportState = reportState,
                    recordsCount = timeRecords.size,
                    onClear = { aiViewModel.clearReport() }
                )
            }

            // Bottom spacer for better screen margins
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AiConsultantHeader() {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF8B5CF6), // Purple
                                Color(0xFF4285F4)  // Blue
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "AI Sparkle Logo",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Gemini 智能大脑顾问",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "深度挖掘您的时间日志，量身提炼敏捷而有自律温度的成长报告！",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ReportActionGrid(
    records: List<TimeRecord>,
    aiViewModel: AiViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Daily Report Button
        Button(
            onClick = { aiViewModel.generateDailyReport(records) },
            modifier = Modifier
                .weight(1f)
                .testTag("btn_daily_report")
                .minimumInteractiveComponentSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("AI 日报", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Weekly Report Button
        Button(
            onClick = { aiViewModel.generateWeeklyReport(records) },
            modifier = Modifier
                .weight(1f)
                .testTag("btn_weekly_report")
                .minimumInteractiveComponentSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("AI 周报", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Monthly Report Button
        Button(
            onClick = { aiViewModel.generateMonthlyReport(records) },
            modifier = Modifier
                .weight(1f)
                .testTag("btn_monthly_report")
                .minimumInteractiveComponentSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("AI 月报", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AiReportContentWrapper(
    reportState: AiReportUiState,
    recordsCount: Int,
    onClear: () -> Unit
) {
    AnimatedContent(
        targetState = reportState,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "report_state_toggle"
    ) { state ->
        when (state) {
            is AiReportUiState.Idle -> {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = "Pending Insights",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "等待生成智能自律报告...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "点击上方按钮，AI 即可根据您记录的 ${recordsCount} 条高质量原始时间数据进行多维度提炼！",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            is AiReportUiState.Loading -> {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "CampusAI 正在极速归纳中...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "正在驱动 gemini-3.5-flash 大模型进行启发式计算",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            is AiReportUiState.Success -> {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF22C55E), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "智能报告提炼结果",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            IconButton(
                                onClick = onClear,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear Result",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Streaming dynamic text wrapper (mimic OpenAI chat response load)
                        StreamingTypeText(
                            text = state.report,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            is AiReportUiState.Error -> {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "智能服务解析发生故障",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
