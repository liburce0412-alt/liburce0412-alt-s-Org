package com.campusai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.campusai.core.designsystem.BrandMark
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.TelemetryChip
import com.campusai.core.designsystem.SlideConfirm
import com.campusai.core.designsystem.SpectraPrimaryButton
import com.campusai.core.designsystem.Tomorrow
import com.campusai.core.auth.AuthState
import com.campusai.core.model.MotionMode
import com.campusai.core.model.AiProvider
import com.campusai.core.model.LocalModelState
import com.campusai.core.model.RenderQuality
import com.campusai.core.model.SpectraEnvironment
import com.campusai.core.model.ThemeMode
import com.campusai.core.model.TimeRecord
import com.campusai.core.preferences.UserPreferences
import com.campusai.core.preferences.UserPreferencesRepository
import com.campusai.core.localai.LocalMnnAiEngine
import com.campusai.core.localai.LocalModelManager
import com.campusai.core.security.PersonalDeepSeekKeyStore
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog

@Composable
fun ProfileScreen(
    preferences: UserPreferences,
    repository: UserPreferencesRepository,
    records: List<TimeRecord>,
    authState: AuthState,
    unreadMessages: Int,
    activeOrders: Int,
    onLogin: () -> Unit,
    onSignOut: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenOrders: () -> Unit,
    localModelManager: LocalModelManager,
    localAiEngine: LocalMnnAiEngine,
    personalDeepSeekKeyStore: PersonalDeepSeekKeyStore,
    contentPadding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    val totalHours = records.sumOf { it.durationMinutes } / 60
    val level = (totalHours / 10 + 1).coerceAtMost(99)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 24.dp, bottom = contentPadding.calculateBottomPadding() + 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
                Column {
                    Box(Modifier.fillMaxWidth().height(146.dp).background(Brush.horizontalGradient(listOf(SpectraColors.Cyan.copy(.65f), SpectraColors.Violet.copy(.72f), SpectraColors.Warm.copy(.55f)))))
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp).background(Color.White.copy(.72f), CircleShape), contentAlignment = Alignment.Center) { BrandMark(Modifier.size(56.dp)) }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("CampusAI 用户", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.size(5.dp)); Icon(Icons.Rounded.Shield, null, tint = SpectraColors.Focus, modifier = Modifier.size(18.dp)) }
                            Text("LEVEL $level · ${totalHours * 10} XP", fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(.62f))
                        }
                        Icon(Icons.Rounded.Settings, "设置")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatPanel("$totalHours", "累计小时", Modifier.weight(1f))
                StatPanel(records.size.toString(), "时间记录", Modifier.weight(1f))
                StatPanel(level.toString(), "当前等级", Modifier.weight(1f))
            }
        }
        item { Text("成就", style = MaterialTheme.typography.titleLarge) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf("第一束光", "专注起航", "稳定节奏")) { label ->
                    GlassPanel(Modifier.size(132.dp, 116.dp), radius = 16) {
                        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(SpectraColors.Cyan, SpectraColors.Violet)), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.EmojiEvents, null, tint = Color.White) }
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        item { Text("AI 运行方式", style = MaterialTheme.typography.titleLarge) }
        item {
            LocalAiSettings(
                preferences = preferences,
                repository = repository,
                manager = localModelManager,
                engine = localAiEngine,
                personalKeyStore = personalDeepSeekKeyStore,
            )
        }
        item { Text("外观与体验", style = MaterialTheme.typography.titleLarge) }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
                Column {
                    SettingSelector(Icons.Rounded.Palette, "主题", ThemeMode.entries, preferences.themeMode, { it.name }) { scope.launch { repository.setTheme(it) } }
                    DividerInset()
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.size(12.dp)); Column { Text("SPECTRA 环境", style = MaterialTheme.typography.titleMedium); Text("只改变环境色场与折射", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f)) } }
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SpectraEnvironment.entries) { environment -> TelemetryChip(environment.name, environment == preferences.environment, { scope.launch { repository.setEnvironment(environment) } }) }
                        }
                    }
                    DividerInset()
                    SettingSelector(Icons.Rounded.Speed, "渲染质量", RenderQuality.entries, preferences.renderQuality, { it.name }) { scope.launch { repository.setQuality(it) } }
                    DividerInset()
                    SettingSwitch(Icons.Rounded.MotionPhotosOff, "动态与折射", preferences.motionMode == MotionMode.ON) { scope.launch { repository.setMotion(if (it) MotionMode.ON else MotionMode.OFF) } }
                    DividerInset()
                    SettingSwitch(Icons.Rounded.GraphicEq, "计时完成声音", preferences.soundEnabled) { scope.launch { repository.setSound(it) } }
                }
            }
        }
        item { Text("消息与交易", style = MaterialTheme.typography.titleLarge) }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
                Column {
                    SettingLink(Icons.Rounded.Forum, if (unreadMessages > 0) "消息 · $unreadMessages 条未读" else "消息", if (authState.signedIn) onOpenMessages else onLogin)
                    DividerInset()
                    SettingLink(Icons.AutoMirrored.Rounded.ReceiptLong, if (activeOrders > 0) "订单 · $activeOrders 个进行中" else "订单", if (authState.signedIn) onOpenOrders else onLogin)
                }
            }
        }
        item { Text("账户与安全", style = MaterialTheme.typography.titleLarge) }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
                Column {
                    if (authState.signedIn) SettingLink(Icons.AutoMirrored.Rounded.Logout, "退出 ${authState.email.ifBlank { "当前账户" }}", onSignOut)
                    else SettingLink(Icons.AutoMirrored.Rounded.Login, "登录以同步、聊天和使用 AI", onLogin)
                    DividerInset()
                    SettingLink(Icons.Rounded.PrivacyTip, "隐私与匿名身份")
                    DividerInset()
                    SettingLink(Icons.Rounded.Notifications, "通知与提醒")
                }
            }
        }
    }
}

@Composable
private fun LocalAiSettings(
    preferences: UserPreferences,
    repository: UserPreferencesRepository,
    manager: LocalModelManager,
    engine: LocalMnnAiEngine,
    personalKeyStore: PersonalDeepSeekKeyStore,
) {
    val scope = rememberCoroutineScope()
    val state by manager.state.collectAsState()
    var confirmMobile by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRedownload by remember { mutableStateOf(false) }
    var personalKey by remember { mutableStateOf("") }
    var personalKeyVisible by remember { mutableStateOf(false) }
    var personalKeySaved by remember { mutableStateOf(personalKeyStore.hasKey()) }
    var personalKeyMessage by remember { mutableStateOf<String?>(null) }
    val manifest = manager.manifest
    val actionLabel = when (state) {
        LocalModelState.NotDownloaded -> "下载离线模型"
        is LocalModelState.Downloading -> "暂停下载"
        is LocalModelState.Paused -> "继续下载"
        is LocalModelState.Error -> "重新下载"
        LocalModelState.Ready -> null
        LocalModelState.Checking, LocalModelState.Verifying, LocalModelState.Loading, is LocalModelState.Incompatible -> null
    }
    val statusText = when (val value = state) {
        LocalModelState.NotDownloaded -> "未下载 · 首次安装不会自动下载"
        LocalModelState.Checking -> "正在检查模型文件"
        is LocalModelState.Downloading -> "${(value.progress * 100).toInt()}% · ${formatBytes(value.downloadedBytes)} / ${formatBytes(value.totalBytes)}"
        is LocalModelState.Paused -> "已暂停 · ${formatBytes(value.downloadedBytes)} / ${formatBytes(value.totalBytes)}"
        LocalModelState.Verifying -> "正在逐文件执行 SHA-256 校验"
        LocalModelState.Ready -> "Ready · 可完全离线使用"
        LocalModelState.Loading -> "正在从应用私有目录加载"
        is LocalModelState.Error -> "${value.message}（${value.code}）"
        is LocalModelState.Incompatible -> value.reason
    }
    GlassPanel(Modifier.fillMaxWidth(), radius = 16, emphasized = true) {
        Column {
            SettingSelector(Icons.Rounded.Cloud, "AI 运行方式", AiProvider.entries, preferences.aiProvider, {
                when (it) { AiProvider.AUTO -> "自动"; AiProvider.DEEPSEEK -> "DeepSeek · 我的 Key"; AiProvider.LOCAL -> "本地离线" }
            }) { scope.launch { repository.setAiProvider(it) } }
            Text(
                when (preferences.aiProvider) {
                    AiProvider.AUTO -> "在线时使用你自己的 DeepSeek Key；离线且模型 Ready 时使用本地快速模式。没有 Key 时不会借用平台额度。"
                    AiProvider.DEEPSEEK -> "固定使用你自己的 DeepSeek Key，不会自动切换本地，也不会调用 CampusAI 平台额度。"
                    AiProvider.LOCAL -> "提示词、学习统计、课程表与回复仅在本机处理；本地失败不会静默调用云端。"
                },
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.66f),
            )
            DividerInset()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, null)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DeepSeek 个人 Key", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (personalKeySaved) personalKeyStore.maskedLabel() else "尚未保存",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(.58f),
                        )
                    }
                }
                Text(
                    "Key 使用 Android Keystore 加密保存，不参与备份；生成时只发送到 api.deepseek.com，不经过 Supabase。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(.66f),
                )
                OutlinedTextField(
                    value = personalKey,
                    onValueChange = { personalKey = it; personalKeyMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (personalKeySaved) "粘贴新 Key 以替换" else "DeepSeek API Key") },
                    singleLine = true,
                    visualTransformation = if (personalKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { personalKeyVisible = !personalKeyVisible }) {
                            Icon(if (personalKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (personalKeyVisible) "隐藏 Key" else "显示 Key")
                        }
                    },
                )
                personalKeyMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (personalKeySaved) SpectraColors.Success else SpectraColors.Error,
                    )
                }
                SpectraPrimaryButton(
                    text = if (personalKeySaved) "安全替换 Key" else "安全保存 Key",
                    onClick = {
                        personalKeyStore.save(personalKey).fold(
                            onSuccess = {
                                personalKey = ""
                                personalKeySaved = true
                                personalKeyMessage = "已加密保存。下次云端生成将使用这个 Key。"
                            },
                            onFailure = { personalKeyMessage = it.message ?: "Key 保存失败，请重试。" },
                        )
                    },
                    icon = Icons.Rounded.Save,
                    enabled = personalKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (personalKeySaved) TextButton(
                    onClick = {
                        if (personalKeyStore.delete()) {
                            personalKey = ""
                            personalKeySaved = false
                            personalKeyMessage = "个人 Key 已从本机删除。"
                        } else personalKeyMessage = "删除失败，请重试。"
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("删除个人 Key", color = SpectraColors.Error) }
            }
            DividerInset()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PhoneAndroid, null)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${manifest.displayName} · ${manifest.quantization}", style = MaterialTheme.typography.titleMedium)
                        Text("版本 ${manifest.version} · 下载 ${formatBytes(manifest.totalBytes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
                    }
                }
                Text(statusText, style = MaterialTheme.typography.bodyMedium, color = if (state is LocalModelState.Error || state is LocalModelState.Incompatible) SpectraColors.Error else MaterialTheme.colorScheme.onSurface.copy(.7f))
                val downloading = state as? LocalModelState.Downloading
                if (downloading != null) LinearProgressIndicator(
                    progress = { downloading.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = SpectraColors.Focus,
                    trackColor = SpectraColors.Silver.copy(.42f),
                )
                Text("当前占用 ${formatBytes(manager.storage.occupiedBytes())} · 需要 arm64-v8a、Android ${manifest.minimumApi}+、至少 6 GB 内存", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
                actionLabel?.let { label ->
                    SpectraPrimaryButton(
                        text = label,
                        onClick = {
                            when (state) {
                                is LocalModelState.Downloading -> manager.pause()
                                is LocalModelState.Paused -> manager.resume(preferences.localModelWifiOnly)
                                else -> manager.download(preferences.localModelWifiOnly)
                            }
                        },
                        icon = if (state is LocalModelState.Downloading) Icons.Rounded.Download else Icons.Rounded.Download,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state == LocalModelState.Ready) Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = { confirmRedownload = true }) { Text("重新下载") }
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, null, tint = SpectraColors.Error)
                        Spacer(Modifier.size(6.dp))
                        Text("删除模型", color = SpectraColors.Error)
                    }
                }
            }
            DividerInset()
            SettingSwitch(Icons.Rounded.Download, "仅 Wi-Fi 下载", preferences.localModelWifiOnly) { enabled ->
                if (enabled) scope.launch { repository.setLocalModelWifiOnly(true) } else confirmMobile = true
            }
            DividerInset()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("隐私、许可证与来源", style = MaterialTheme.typography.titleMedium)
                Text("本地推理不发送提示词或回复；下载器只接受内置固定清单，模型文件只作为数据读取。", style = MaterialTheme.typography.bodyMedium)
                Text("模型 ${manifest.license} · MNN ${manifest.runtime.version} · ${manifest.sourceUrl}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
            }
        }
    }
    if (confirmMobile) AlertDialog(
        onDismissRequest = { confirmMobile = false },
        title = { Text("允许移动网络下载？") },
        text = { Text("模型约 ${formatBytes(manifest.totalBytes)}，可能产生较大流量费用。确认后仅改变下载网络限制。") },
        confirmButton = { TextButton(onClick = { confirmMobile = false; scope.launch { repository.setLocalModelWifiOnly(false) } }) { Text("确认允许") } },
        dismissButton = { TextButton(onClick = { confirmMobile = false }) { Text("保持仅 Wi-Fi") } },
    )
    if (confirmDelete) Dialog(onDismissRequest = { confirmDelete = false }) {
        GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("删除离线模型", style = MaterialTheme.typography.titleLarge)
                Text("将停止本地推理并释放约 ${formatBytes(manager.storage.occupiedBytes())}。聊天报告不会删除。", style = MaterialTheme.typography.bodyMedium)
                SlideConfirm("滑动删除模型", onConfirm = {
                    confirmDelete = false
                    scope.launch {
                        manager.deleteModel { engine.releaseAndWait() }
                    }
                })
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.align(Alignment.End)) { Text("取消") }
            }
        }
    }
    if (confirmRedownload) Dialog(onDismissRequest = { confirmRedownload = false }) {
        GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("重新下载离线模型", style = MaterialTheme.typography.titleLarge)
                Text("将先停止推理并删除当前模型，再按现有网络限制重新下载和校验。", style = MaterialTheme.typography.bodyMedium)
                SlideConfirm("滑动重新下载", onConfirm = {
                    confirmRedownload = false
                    scope.launch {
                        if (manager.deleteModel { engine.releaseAndWait() }) {
                            manager.download(preferences.localModelWifiOnly)
                        }
                    }
                })
                TextButton(onClick = { confirmRedownload = false }, modifier = Modifier.align(Alignment.End)) { Text("取消") }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes.toDouble() / (1024L * 1024L * 1024L))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / (1024L * 1024L))
    else -> "%.1f KB".format(bytes.toDouble() / 1024L)
}

@Composable
private fun StatPanel(value: String, label: String, modifier: Modifier) {
    GlassPanel(modifier.height(92.dp), radius = 16) { Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) { Text(value, fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f)) } }
}

@Composable
private fun <T> SettingSelector(icon: ImageVector, label: String, values: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.size(12.dp)); Text(label, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(values) { value -> TelemetryChip(text(value), value == selected, { onSelect(value) }) } }
    }
}

@Composable
private fun SettingSwitch(icon: ImageVector, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.size(12.dp)); Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Switch(checked, onChecked) }
}

@Composable
private fun SettingLink(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.size(12.dp)); Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.45f)) }
}

@Composable private fun DividerInset() = HorizontalDivider(Modifier.padding(start = 52.dp), color = SpectraColors.Silver.copy(.68f))
