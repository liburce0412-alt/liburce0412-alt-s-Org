package com.campusai.features.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.TelemetryChip
import com.campusai.core.model.AiMode

@Composable
fun AiScreen(viewModel: AiViewModel, todayMinutes: Long, recordCount: Int, onBack:()->Unit) {
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    var prompt by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(.92f)).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Rounded.ArrowBack,"返回")}
                Column(Modifier.weight(1f)){Text("AI 洞察",style=MaterialTheme.typography.headlineMedium);Text(if(state.model.isBlank())"安全服务端模式" else state.model,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface.copy(.56f))}
                IconButton(onClick={showHistory=!showHistory}){Icon(Icons.Rounded.History,"报告历史")}
                IconButton(onClick=viewModel::newConversation){Icon(Icons.Rounded.AddComment,"新对话")}
            }
            Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TelemetryChip("FAST",state.mode==AiMode.FAST,{viewModel.setMode(AiMode.FAST)},Modifier.weight(1f))
                TelemetryChip("DEEP",state.mode==AiMode.DEEP,{viewModel.setMode(AiMode.DEEP)},Modifier.weight(1f))
            }
            if(showHistory){
                LazyColumn(Modifier.weight(1f).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    if(history.isEmpty()) item{EmptyAiState("还没有保存的 AI 报告","完成一次回复后会自动保存在本机。")}
                    items(history){report->GlassPanel(Modifier.fillMaxWidth(),radius=16){Column(Modifier.padding(16.dp)){Text(report.title,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(report.summary,style=MaterialTheme.typography.bodyMedium,maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurface.copy(.65f));Text(report.mode.name,style=MaterialTheme.typography.labelMedium,color=SpectraColors.Focus)}}}
                }
            } else {
                LazyColumn(Modifier.weight(1f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    item{GlassPanel(Modifier.fillMaxWidth(),radius=16){Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Rounded.Info,null,tint=SpectraColors.Focus);Text("基于今天 $recordCount 条记录、${todayMinutes} 分钟。建议遵循“先结论、后行动”；DEEP 只显示公开阶段，不展示内部推理。",style=MaterialTheme.typography.bodyMedium)}}}
                    if(state.messages.isEmpty()) item{EmptyAiState("从一个具体问题开始","例如：根据今天的记录，我下一段 50 分钟最该做什么？")}
                    items(state.messages){message->
                        if(message.content.isNotBlank() || message.role=="assistant") Box(Modifier.fillMaxWidth(),contentAlignment=if(message.role=="user")Alignment.CenterEnd else Alignment.CenterStart){
                            GlassPanel(Modifier.fillMaxWidth(if(message.role=="user") .82f else 1f),radius=16){Text(if(message.content.isBlank())"正在连接…" else message.content,Modifier.padding(15.dp),style=MaterialTheme.typography.bodyLarge)}
                        }
                    }
                    if(state.streaming) item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text(state.stage.ifBlank{"接收真实数据块"},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface.copy(.6f))}}
                    state.error?.let { error->item{GlassPanel(Modifier.fillMaxWidth(),radius=16){Column(Modifier.padding(14.dp)){Text("这次没有完成",style=MaterialTheme.typography.titleMedium,color=SpectraColors.Error);Text(error,style=MaterialTheme.typography.bodyMedium);Text("检查网络或登录状态后可以重试。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface.copy(.6f))}}} }
                }
            }
            if(!showHistory) Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(prompt,{prompt=it},modifier=Modifier.weight(1f),placeholder={Text("问问今天的节奏…")},shape=RoundedCornerShape(20.dp),maxLines=4)
                IconButton(enabled=prompt.isNotBlank()&&!state.streaming,onClick={val value=prompt;prompt="";viewModel.send(value,todayMinutes,recordCount)},modifier=Modifier.background(SpectraColors.Ink,CircleShape)){Icon(Icons.AutoMirrored.Rounded.Send,"发送",tint=Color.White)}
            }
        }
    }
}

@Composable private fun EmptyAiState(title:String,body:String){GlassPanel(Modifier.fillMaxWidth(),radius=24){Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Rounded.AutoAwesome,null,tint=SpectraColors.Violet,modifier=Modifier.size(42.dp));Spacer(Modifier.size(12.dp));Text(title,style=MaterialTheme.typography.titleLarge);Text(body,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface.copy(.6f))}}}
