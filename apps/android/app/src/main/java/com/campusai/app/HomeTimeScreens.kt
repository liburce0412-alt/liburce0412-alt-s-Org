package com.campusai.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusai.core.designsystem.BrandMark
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.SpectraPrimaryButton
import com.campusai.core.designsystem.TelemetryChip
import com.campusai.core.designsystem.Tomorrow
import com.campusai.core.model.TimeRecord
import com.campusai.features.time.TimeViewModel
import com.campusai.features.schedule.CourseDraft
import com.campusai.features.schedule.ScheduleImporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun HomeScreen(
    records: List<TimeRecord>,
    onStartRecord: () -> Unit,
    onOpenAi: () -> Unit,
    contentPadding: PaddingValues,
) {
    val todayStart = remember { startOfToday() }
    val todayRecords = records.filter { it.startTime >= todayStart }
    val totalMinutes = todayRecords.sumOf { it.durationMinutes }
    val goalMinutes = 240L
    val streak = remember(records) { calculateStreak(records) }
    val categories = todayRecords.map { it.category }.distinct().take(3).ifEmpty { listOf("学习", "项目", "运动") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 24.dp, bottom = contentPadding.calculateBottomPadding() + 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                    Text("今天，专注重要的事", style = MaterialTheme.typography.headlineLarge)
                }
                GlassPanel(Modifier.size(48.dp), radius = 24) { BrandMark(Modifier.fillMaxSize().padding(9.dp)) }
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("今日行动", style = MaterialTheme.typography.titleLarge)
                            Text("目标 ${formatDuration(goalMinutes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                        }
                        TelemetryChip("${(totalMinutes * 100 / goalMinutes).coerceAtMost(100)}%", true, {})
                    }
                    Spacer(Modifier.height(8.dp))
                    SpectraProgress(totalMinutes = totalMinutes, goalMinutes = goalMinutes)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { category -> TelemetryChip(category, false, onStartRecord) }
                    }
                    Spacer(Modifier.height(16.dp))
                    SpectraPrimaryButton("开始记录", onStartRecord, Modifier.fillMaxWidth(), icon = Icons.Rounded.Timer)
                }
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = SpectraColors.Warm)
                    Column(Modifier.weight(1f)) {
                        Text("连续 $streak 天", style = MaterialTheme.typography.titleMedium)
                        Text("再完成一次记录，能量条就会继续生长。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.62f))
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(SpectraColors.Silver.copy(.55f))) {
                            Box(Modifier.fillMaxWidth((streak / 7f).coerceIn(.08f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(SpectraColors.Cyan, SpectraColors.Violet, SpectraColors.Warm))))
                        }
                    }
                }
            }
        }
        item { SectionLabel("AI 洞察", "基于今天 ${todayRecords.size} 条记录") }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 16, onClick = onOpenAi) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = SpectraColors.Violet)
                    Spacer(Modifier.height(12.dp))
                    Text(if (todayRecords.isEmpty()) "完成第一条记录后，我会在这里总结你的节奏。" else "你的高投入时段正在形成。建议把下一个 50 分钟留给今天最重要的任务。", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("查看依据与行动计划", style = MaterialTheme.typography.labelLarge, color = SpectraColors.Focus)
                }
            }
        }
        item { SectionLabel("公告", "校园服务") }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Campaign, null, tint = SpectraColors.Warm)
                    Column { Text("新学期数据同步说明", style = MaterialTheme.typography.titleMedium); Text("时间数据始终本地优先，联网后自动同步。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.62f)) }
                }
            }
        }
    }
}

@Composable
private fun SpectraProgress(totalMinutes: Long, goalMinutes: Long) {
    val progress = (totalMinutes / goalMinutes.toFloat()).coerceIn(0f, 1f)
    Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(15.dp)) {
            drawArc(SpectraColors.Silver.copy(.5f), -90f, 360f, false, style = Stroke(15.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(SpectraColors.Cyan, SpectraColors.Violet, SpectraColors.Rose, SpectraColors.Warm, SpectraColors.Cyan)), -90f, 360f * progress, false, style = Stroke(15.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatDuration(totalMinutes), fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, fontSize = 40.sp)
            Text("今日累计", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
        }
    }
}

@Composable
private fun SectionLabel(title: String, meta: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.55f))
    }
}

@Composable
fun TimeScreen(
    records: List<TimeRecord>,
    viewModel: TimeViewModel,
    onStartFocus: (Int) -> Unit,
    onMessage: suspend (String, String?) -> SnackbarResult,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val courses by viewModel.courses.collectAsState()
    var range by rememberSaveable { mutableStateOf("日") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var importDrafts by remember { mutableStateOf<List<CourseDraft>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            importing = true; importError = null
            runCatching { ScheduleImporter.fromImage(context, uri) }
                .onSuccess { if (it.isEmpty()) importError = "没有识别到可靠的课程格。请换一张清晰、完整的课程表截图，或使用日历文件。" else importDrafts = it }
                .onFailure { importError = "截图识别失败：${it.message ?: "图片无法读取"}。你可以改用日历文件或手动添加。" }
            importing = false
        }
    }
    val icsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            importing = true; importError = null
            runCatching { withContext(Dispatchers.IO) { ScheduleImporter.fromIcs(context, uri) } }
                .onSuccess { if (it.isEmpty()) importError = "这个日历文件里没有可导入的课程事件。" else importDrafts = it }
                .onFailure { importError = "日历文件解析失败：${it.message ?: "格式不受支持"}。" }
            importing = false
        }
    }
    val filtered = when (range) {
        "周" -> records.filter { it.startTime >= System.currentTimeMillis() - 7 * 86_400_000L }
        "月" -> records.filter { it.startTime >= System.currentTimeMillis() - 31 * 86_400_000L }
        else -> records.filter { it.startTime >= startOfToday() }
    }
    var deleted by remember { mutableStateOf<TimeRecord?>(null) }
    LaunchedEffect(deleted) {
        val record = deleted ?: return@LaunchedEffect
        if (onMessage("已删除“${record.title}”", "撤销") == SnackbarResult.ActionPerformed) {
            viewModel.addTimeRecord(record.title, record.category, record.startTime, record.endTime, record.remark)
        }
        deleted = null
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 24.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("时间", style = MaterialTheme.typography.headlineLarge); Text("今天的轨迹，清楚而不嘈杂", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f)) }
                    IconButton(onClick = { showImport = true }) { Icon(Icons.Rounded.FileOpen, "导入课程表") }
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
                    Column(Modifier.padding(16.dp)) {
                        Text("专注预设", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25, 50, 90).forEach { minutes -> TelemetryChip("$minutes MIN", minutes == 50, { onStartFocus(minutes) }, Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("进入全屏专注后仍可最小化；完成会自动写入时间轴。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.62f))
                    }
                }
            }
            if (courses.isNotEmpty()) {
                item {
                    val today = ((java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                    val todayCourses = courses.filter { it.weekday == today }
                    GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("今日课程", style = MaterialTheme.typography.titleLarge); Text("${todayCourses.size} 节", style = MaterialTheme.typography.labelMedium) }
                            Spacer(Modifier.height(8.dp))
                            if (todayCourses.isEmpty()) Text("今天没有已导入的课程。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
                            todayCourses.forEach { course ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("%02d:%02d".format(course.startMinute/60,course.startMinute%60), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(58.dp))
                                    Column { Text(course.name, style = MaterialTheme.typography.titleMedium); if(course.location.isNotBlank()) Text(course.location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.56f)) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth(), radius = 22) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("日", "周", "月").forEach { option -> TelemetryChip(option, range == option, { range = option }, Modifier.weight(1f)) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${range}时间轴", style = MaterialTheme.typography.titleLarge)
                    Text(formatDuration(filtered.sumOf { it.durationMinutes }), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 24) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BrandMark(Modifier.size(72.dp))
                            Spacer(Modifier.height(14.dp))
                            Text("时间轴还很安静", style = MaterialTheme.typography.titleLarge)
                            Text("补一条记录，或从 25 分钟专注开始。", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                        }
                    }
                }
            } else {
                item {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
                        Column {
                            filtered.forEachIndexed { index, record ->
                                TimelineRow(record, onDelete = { viewModel.deleteTimeRecord(record.id); deleted = record })
                                if (index < filtered.lastIndex) HorizontalDivider(Modifier.padding(start = 62.dp), color = SpectraColors.Silver.copy(.65f))
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 22.dp),
            shape = CircleShape,
            containerColor = SpectraColors.Ink,
            contentColor = Color.White,
        ) { Icon(Icons.Rounded.Add, "新增记录") }
    }
    if (showAdd) AddTimeRecordDialog(onDismiss = { showAdd = false }, onSave = { title, category, minutes, note ->
        val end = System.currentTimeMillis(); viewModel.addTimeRecord(title, category, end - minutes * 60_000L, end, note); showAdd = false
    })
    if (showImport) ImportScheduleSourceDialog(
        onDismiss = { showImport = false },
        onImage = { showImport = false; imagePicker.launch("image/*") },
        onIcs = { showImport = false; icsPicker.launch(arrayOf("text/calendar", "application/ics", "application/octet-stream")) },
        onManual = { showImport = false; importDrafts = listOf(CourseDraft("新课程", 1, 8*60, 9*60+40)) },
    )
    if (importing) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("正在读取课程表") }, text = { Text("识别在本机完成。完成后会先让你确认，不会直接覆盖现有课程。") })
    importError?.let { message -> AlertDialog(onDismissRequest = { importError = null }, confirmButton = { TextButton(onClick = { importError = null }) { Text("知道了") } }, title = { Text("暂时没能导入") }, text = { Text(message) }) }
    importDrafts?.let { drafts -> SchedulePreviewDialog(
        initial = drafts,
        onDismiss = { importDrafts = null },
        onConfirm = { edited ->
            viewModel.importCourses(edited.map { it.toCourse() }) { inserted, duplicates -> scope.launch { onMessage("已导入 $inserted 门课程${if (duplicates>0) "，跳过 $duplicates 条重复" else ""}", null) } }
            importDrafts = null
        },
    ) }
}

@Composable
private fun ImportScheduleSourceDialog(onDismiss:()->Unit,onImage:()->Unit,onIcs:()->Unit,onManual:()->Unit) {
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("导入课程表")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("最省事的方式是截取一张完整课程表。识别结果会先进入可编辑预览。", style=MaterialTheme.typography.bodyMedium)
            SpectraPrimaryButton("选择课程表截图",onImage,Modifier.fillMaxWidth(),icon=Icons.Rounded.ImageSearch)
            TextButton(onClick=onIcs,Modifier.fillMaxWidth()){Text("从 .ics 日历文件导入")}
            TextButton(onClick=onManual,Modifier.fillMaxWidth()){Text("手动添加课程")}
        }},
        confirmButton={}, dismissButton={TextButton(onClick=onDismiss){Text("取消")}}, shape=RoundedCornerShape(24.dp),
    )
}

@Composable
private fun SchedulePreviewDialog(initial:List<CourseDraft>,onDismiss:()->Unit,onConfirm:(List<CourseDraft>)->Unit) {
    var drafts by remember(initial){mutableStateOf(initial)}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("确认课程（${drafts.size}）")},
        text={LazyColumn(Modifier.fillMaxWidth().height(420.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text("识别可能会把教室当作课程名。请在保存前快速检查；重复课程会自动跳过。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface.copy(.62f))}
            items(drafts.size){index-> val item=drafts[index]; var startText by remember(index,item.startMinute){mutableStateOf(formatClock(item.startMinute))}; var endText by remember(index,item.endMinute){mutableStateOf(formatClock(item.endMinute))}; GlassPanel(Modifier.fillMaxWidth(),radius=16){Column(Modifier.padding(12.dp)){
                OutlinedTextField(item.name,{value->drafts=drafts.toMutableList().also{it[index]=item.copy(name=value)}},label={Text("课程名")},singleLine=true,shape=RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(item.location,{value->drafts=drafts.toMutableList().also{it[index]=item.copy(location=value)}},label={Text("教室（可选）")},singleLine=true,shape=RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items((1..7).toList()){day->TelemetryChip("周${"一二三四五六日"[day-1]}",item.weekday==day,{drafts=drafts.toMutableList().also{it[index]=item.copy(weekday=day)}})}}
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(startText,{value->startText=value;parseClockOrNull(value)?.let{minute->drafts=drafts.toMutableList().also{it[index]=item.copy(startMinute=minute)}}},label={Text("开始 HH:mm")},singleLine=true,shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f),isError=parseClockOrNull(startText)==null)
                    OutlinedTextField(endText,{value->endText=value;parseClockOrNull(value)?.let{minute->drafts=drafts.toMutableList().also{it[index]=item.copy(endMinute=minute)}}},label={Text("结束 HH:mm")},singleLine=true,shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f),isError=parseClockOrNull(endText)==null)
                }
                if(item.endMinute<=item.startMinute) Text("结束时间必须晚于开始时间。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={drafts=drafts.filterIndexed{i,_->i!=index}}){Text("移除这条")}
            }}}
        }},
        confirmButton={TextButton(enabled=drafts.any{it.name.isNotBlank()}&&drafts.all{it.endMinute>it.startMinute},onClick={onConfirm(drafts.filter{it.name.isNotBlank()})}){Text("确认导入")}},
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}}, shape=RoundedCornerShape(24.dp),
    )
}

@Composable
private fun TimelineRow(record: TimeRecord, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Brush.linearGradient(listOf(SpectraColors.Cyan.copy(.8f), SpectraColors.Violet.copy(.7f))), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${record.category} · ${SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(record.startTime))}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
        }
        Text(formatDuration(record.durationMinutes), style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.onSurface.copy(.55f)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimeRecordDialog(onDismiss: () -> Unit, onSave: (String, String, Long, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("学习") }
    var note by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableIntStateOf(50) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("补录时间", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("做了什么") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(category, { category = it }, label = { Text("分类") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                Text("$minutes 分钟", style = MaterialTheme.typography.labelMedium)
                Slider(minutes.toFloat(), { minutes = it.toInt() }, valueRange = 5f..240f, steps = 46)
                OutlinedTextField(note, { note = it }, label = { Text("描述（可选）") }, shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), category.trim().ifEmpty { "其他" }, minutes.toLong(), note.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun FocusSessionScreen(
    presetMinutes: Int,
    motionEnabled: Boolean,
    soundEnabled: Boolean,
    onMinimize: () -> Unit,
    onFinish: (Int) -> Unit,
) {
    val context = LocalContext.current
    var remaining by rememberSaveable(presetMinutes) { mutableLongStateOf(presetMinutes * 60L) }
    var running by rememberSaveable { mutableStateOf(true) }
    var completed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(running, completed) {
        while (running && remaining > 0) { delay(1_000); remaining-- }
        if (remaining == 0L && !completed) {
            completed = true; running = false
            if (soundEnabled) ToneGenerator(AudioManager.STREAM_NOTIFICATION, 32).apply { startTone(ToneGenerator.TONE_PROP_ACK, 900); delay(950); release() }
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    Box(Modifier.fillMaxSize().background(SpectraColors.Night.copy(.84f))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TelemetryChip("DEEP FOCUS", true, {})
                IconButton(onClick = onMinimize) { Icon(Icons.Rounded.KeyboardArrowDown, "最小化", tint = Color.White) }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val progress = remaining / (presetMinutes * 60f)
                    drawCircle(Color.White.copy(.1f), style = Stroke(10.dp.toPx()))
                    drawArc(Brush.sweepGradient(listOf(SpectraColors.Cyan, SpectraColors.Violet, SpectraColors.Rose, SpectraColors.Warm, SpectraColors.Cyan)), -90f, progress * 360f, false, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(remaining / 60, remaining % 60), fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, fontSize = 58.sp, color = Color.White)
                    Text(if (completed) "已完成" else "保持当前节奏", color = Color.White.copy(.65f))
                }
            }
            Spacer(Modifier.weight(1f))
            if (completed) SpectraPrimaryButton("完成并保存", { onFinish(presetMinutes) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.CheckCircle)
            else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(onClick = { running = !running }, shape = CircleShape, containerColor = Color.White.copy(.14f), contentColor = Color.White) { Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (running) "暂停" else "继续") }
                FloatingActionButton(onClick = { onFinish(max(1, presetMinutes - (remaining / 60).toInt())) }, shape = CircleShape, containerColor = SpectraColors.Ink, contentColor = Color.White) { Icon(Icons.Rounded.Stop, "结束") }
            }
            Spacer(Modifier.height(20.dp))
            Text(if (motionEnabled) "SPECTRA 环境响应已开启" else "纯色玻璃模式", color = Color.White.copy(.52f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatDuration(minutes: Long): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

private fun formatClock(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun parseClockOrNull(value: String): Int? {
    val match = Regex("^(\\d{1,2}):([0-5]\\d)$").matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    return hour * 60 + match.groupValues[2].toInt()
}

private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

private fun calculateStreak(records: List<TimeRecord>): Int {
    if (records.isEmpty()) return 0
    val days = records.map { java.util.Calendar.getInstance().apply { timeInMillis = it.startTime; set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0) }.timeInMillis }.toSet()
    var cursor = startOfToday(); if (cursor !in days) cursor -= 86_400_000L
    var streak = 0; while (cursor in days) { streak++; cursor -= 86_400_000L }
    return streak
}
