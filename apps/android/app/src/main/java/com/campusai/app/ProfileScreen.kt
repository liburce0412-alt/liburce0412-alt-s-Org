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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusai.core.designsystem.BrandMark
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.TelemetryChip
import com.campusai.core.designsystem.Tomorrow
import com.campusai.core.auth.AuthState
import com.campusai.core.model.MotionMode
import com.campusai.core.model.RenderQuality
import com.campusai.core.model.SpectraEnvironment
import com.campusai.core.model.ThemeMode
import com.campusai.core.model.TimeRecord
import com.campusai.core.preferences.UserPreferences
import com.campusai.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    preferences: UserPreferences,
    repository: UserPreferencesRepository,
    records: List<TimeRecord>,
    authState: AuthState,
    onLogin: () -> Unit,
    onSignOut: () -> Unit,
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
