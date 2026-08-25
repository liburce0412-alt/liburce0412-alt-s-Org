package com.campusai.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.campusai.core.designsystem.BrandMark
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.SlideConfirm
import com.campusai.core.designsystem.SpectraPrimaryButton
import com.campusai.core.designsystem.SpectraTheme
import com.campusai.core.designsystem.SpectraVisualStyle
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
import com.campusai.core.localai.LocalModelManifest
import com.campusai.core.localai.LocalModelManager
import com.campusai.core.localai.LocalModelMode
import com.campusai.core.profile.CampusProfile
import com.campusai.core.profile.ProfileImageKind
import com.campusai.core.profile.ProfileRepository
import com.campusai.core.security.PersonalDeepSeekKeyStore
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.Dialog
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    preferences: UserPreferences,
    repository: UserPreferencesRepository,
    records: List<TimeRecord>,
    authState: AuthState,
    unreadMessages: Int,
    onLogin: () -> Unit,
    onSignOut: () -> Unit,
    onOpenMessages: () -> Unit,
    localModelManager: LocalModelManager,
    localAiEngine: LocalMnnAiEngine,
    personalDeepSeekKeyStore: PersonalDeepSeekKeyStore,
    profileRepository: ProfileRepository,
    contentPadding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    val layout = SpectraTheme.layout
    val cardRadius = SpectraTheme.tokens.radii.card.value.roundToInt()
    val profileState by profileRepository.state.collectAsState()
    val profile = profileState.profile
    val totalHours = records.sumOf { it.durationMinutes } / 60
    val level = maxOf(profile.level, (totalHours / 10 + 1).coerceAtMost(99).toInt())
    var sheet by remember { mutableStateOf<ProfileSheet?>(null) }
    val fallbackName = authState.email.substringBefore('@').ifBlank { "Caesar 用户" }
    val achievements = remember(records, profile.streakDays) { buildAchievements(records, profile.streakDays) }

    LaunchedEffect(authState.signedIn, authState.userId) {
        profileRepository.load(
            userId = authState.userId.takeIf { authState.signedIn }.orEmpty(),
            fallbackName = fallbackName,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = layout.pageHorizontalPadding,
            end = layout.pageHorizontalPadding,
            top = contentPadding.calculateTopPadding() + layout.pageTopSpacing,
            bottom = maxOf(contentPadding.calculateBottomPadding(), layout.pageBottomSpacing),
        ),
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
    ) {
        item {
            ProfileHero(profile, fallbackName, level, totalHours * 10) {
                if (authState.signedIn) sheet = ProfileSheet.EDIT else onLogin()
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth().height(86.dp), radius = cardRadius, shadowed = !SpectraTheme.isFluid) {
                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatCell("$totalHours", "累计小时", Modifier.weight(1f))
                    VerticalRule()
                    StatCell(records.size.toString(), "时间记录", Modifier.weight(1f))
                    VerticalRule()
                    StatCell(profile.streakDays.coerceAtLeast(currentStreak(records)).toString(), "连续天数", Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("成就", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { sheet = ProfileSheet.ACHIEVEMENTS }) { Text("查看全部 ${achievements.count { it.unlocked }}/${achievements.size}") }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(achievements) { AchievementCard(it) }
            }
        }
        item { Text("快捷入口", style = MaterialTheme.typography.titleLarge) }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = cardRadius, shadowed = !SpectraTheme.isFluid) {
                Column {
                    SettingLink(Icons.Rounded.Edit, "编辑资料", "头像、名称、简介与背景") { if (authState.signedIn) sheet = ProfileSheet.EDIT else onLogin() }
                    DividerInset()
                    SettingLink(Icons.Rounded.Cloud, "AI 运行方式", aiProviderLabel(preferences.aiProvider)) { sheet = ProfileSheet.AI }
                    DividerInset()
                    SettingLink(Icons.Rounded.Palette, "外观与体验", environmentLabel(preferences.environment)) { sheet = ProfileSheet.APPEARANCE }
                    DividerInset()
                    SettingLink(Icons.Rounded.Forum, if (unreadMessages > 0) "消息 · $unreadMessages 条未读" else "消息", onClick = if (authState.signedIn) onOpenMessages else onLogin)
                    DividerInset()
                    if (authState.signedIn) SettingLink(
                        Icons.AutoMirrored.Rounded.Logout,
                        "退出登录",
                        authState.email.ifBlank { "当前账户" },
                        onSignOut,
                    )
                    else SettingLink(Icons.AutoMirrored.Rounded.Login, "登录以同步、聊天和使用 AI", onClick = onLogin)
                }
            }
        }
    }

    sheet?.let { selected ->
        val haptic = LocalHapticFeedback.current
        ModalBottomSheet(
            onDismissRequest = { sheet = null; profileRepository.clearMessage() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(.96f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = layout.pageHorizontalPadding,
                    end = layout.pageHorizontalPadding,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
                item {
                    Text(
                        when (selected) {
                            ProfileSheet.EDIT -> "编辑资料"
                            ProfileSheet.AI -> "AI 运行方式"
                            ProfileSheet.APPEARANCE -> "外观与体验"
                            ProfileSheet.ACHIEVEMENTS -> "全部成就"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                when (selected) {
                    ProfileSheet.EDIT -> item {
                        ProfileEditor(profileState.profile, profileState.saving, profileState.message, profileState.error, authState.userId, profileRepository)
                    }
                    ProfileSheet.AI -> item {
                        LocalAiSettings(preferences, repository, localModelManager, localAiEngine, personalDeepSeekKeyStore)
                    }
                    ProfileSheet.APPEARANCE -> item {
                        AppearanceSettings(preferences, repository) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    }
                    ProfileSheet.ACHIEVEMENTS -> items(achievements.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { AchievementCard(it, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private enum class ProfileSheet { EDIT, AI, APPEARANCE, ACHIEVEMENTS }

@Composable
private fun ProfileHero(profile: CampusProfile, fallbackName: String, level: Int, xp: Long, onEdit: () -> Unit) {
    val hasCover = profile.coverUrl.isNotBlank()
    val primary = if (hasCover) Color.White else MaterialTheme.colorScheme.onSurface
    val tokens = SpectraTheme.tokens
    val fluid = SpectraTheme.isFluid
    GlassPanel(
        Modifier.fillMaxWidth().height(if (fluid) 224.dp else 208.dp),
        radius = tokens.radii.hero.value.roundToInt(),
        emphasized = true,
        shadowed = false,
        onClick = onEdit,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (hasCover) {
                AsyncImage(profile.coverUrl, "个人背景", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFF111827).copy(.18f),
                                Color(0xFF111827).copy(.76f),
                            ),
                        ),
                    ),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.White.copy(.06f)))
                BrandMark(Modifier.align(Alignment.TopCenter).padding(top = 26.dp).size(100.dp))
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (fluid) 108.dp else 100.dp)
                    .background(
                        if (hasCover) {
                            Brush.horizontalGradient(listOf(Color.Black.copy(.06f), Color.Black.copy(.18f)))
                        } else if (fluid) {
                            Brush.horizontalGradient(listOf(Color.White.copy(.10f), Color.White.copy(.055f)))
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(if (hasCover) .30f else .12f),
                                    Color.White.copy(if (hasCover) .20f else .08f),
                                    SpectraColors.Cyan.copy(.08f),
                                    SpectraColors.Violet.copy(.07f),
                                ),
                            )
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(primary.copy(if (fluid) .34f else .20f), style = Stroke(if (fluid) 1.5.dp.toPx() else 1.dp.toPx()))
                        if (!fluid) {
                            drawArc(SpectraColors.Cyan.copy(.82f), 126f, 46f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            drawArc(SpectraColors.Violet.copy(.72f), 284f, 38f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            drawArc(SpectraColors.Warm.copy(.62f), 344f, 24f, false, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                    if (profile.avatarUrl.isNotBlank()) {
                        AsyncImage(profile.avatarUrl, "头像", Modifier.size(66.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else BrandMark(Modifier.size(62.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.displayName.ifBlank { fallbackName }, style = MaterialTheme.typography.titleLarge, color = primary)
                        if (profile.isStaff) { Spacer(Modifier.size(5.dp)); Icon(Icons.Rounded.Shield, "管理员", tint = if (hasCover) Color.White else SpectraColors.Focus, modifier = Modifier.size(18.dp)) }
                    }
                    if (profile.bio.isNotBlank()) Text(profile.bio, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = primary.copy(.70f))
                    Text("LEVEL $level · ${maxOf(profile.experience.toLong(), xp)} XP", fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, color = primary.copy(.72f))
                }
                Icon(Icons.Rounded.Edit, "编辑资料", tint = primary)
            }
        }
    }
}

@Composable
private fun ProfileEditor(profile: CampusProfile, saving: Boolean, message: String?, error: String?, userId: String, repository: ProfileRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(profile.id, profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by remember(profile.id, profile.bio) { mutableStateOf(profile.bio) }
    fun upload(uri: Uri, kind: ProfileImageKind) {
        scope.launch {
            val type = context.contentResolver.getType(uri).orEmpty()
            val maximumBytes = if (kind == ProfileImageKind.AVATAR) 5 * 1024 * 1024 else 10 * 1024 * 1024
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        output.write(buffer, 0, read)
                        if (total > maximumBytes) break
                    }
                    output.toByteArray()
                } ?: ByteArray(0)
            }
            repository.uploadImage(userId, kind, bytes, type)
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { uri -> upload(uri, ProfileImageKind.AVATAR) } }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { uri -> upload(uri, ProfileImageKind.COVER) } }
    GlassPanel(Modifier.fillMaxWidth(), radius = 16, emphasized = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpectraPrimaryButton("更换头像", { avatarPicker.launch("image/*") }, Modifier.weight(1f), enabled = !saving, icon = Icons.Rounded.CameraAlt)
                SpectraPrimaryButton("更换背景", { coverPicker.launch("image/*") }, Modifier.weight(1f), enabled = !saving, icon = Icons.Rounded.Badge)
            }
            OutlinedTextField(name, { name = it.take(32) }, Modifier.fillMaxWidth(), label = { Text("账号名称") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(bio, { bio = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("个人简介（可选）") }, minLines = 2, shape = RoundedCornerShape(12.dp), supportingText = { Text("${bio.length}/160") })
            message?.let { Text(it, color = SpectraColors.Success, style = MaterialTheme.typography.bodyMedium) }
            error?.let { Text(it, color = SpectraColors.Error, style = MaterialTheme.typography.bodyMedium) }
            SpectraPrimaryButton(if (saving) "正在保存…" else "保存资料", { scope.launch { repository.updateText(userId, name, bio) } }, Modifier.fillMaxWidth(), enabled = !saving && name.trim().length in 2..32, icon = Icons.Rounded.Save)
            Text("图片保存在你的 Supabase 私有目录；替换成功后会清理旧文件。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
        }
    }
}

@Composable
private fun AppearanceSettings(preferences: UserPreferences, repository: UserPreferencesRepository, onFeedback: () -> Unit) {
    val scope = rememberCoroutineScope()
    GlassPanel(Modifier.fillMaxWidth(), radius = 16, emphasized = true) {
        Column {
            Column(Modifier.padding(16.dp)) {
                Text("界面体系", style = MaterialTheme.typography.titleMedium)
                Text(
                    "经典保留当前信息架构；Strba Fluid 会同步改变全 App 的留白、卡片、导航、转场与环境场。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(.58f),
                )
                Spacer(Modifier.height(12.dp))
                com.campusai.core.designsystem.CaesarSlidingSelector(
                    options = SpectraVisualStyle.entries.map(::visualStyleLabel),
                    selectedIndex = SpectraVisualStyle.entries.indexOf(preferences.visualStyle),
                    onSelected = { index ->
                        SpectraVisualStyle.entries.getOrNull(index)?.let { style ->
                            onFeedback()
                            scope.launch { repository.setVisualStyle(style) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DividerInset()
            SettingSelector(Icons.Rounded.Palette, "主题", ThemeMode.entries, preferences.themeMode, { themeLabel(it) }) { onFeedback(); scope.launch { repository.setTheme(it) } }
            DividerInset()
            Column(Modifier.padding(16.dp)) {
                Text("SPECTRA 环境", style = MaterialTheme.typography.titleMedium)
                Text("样本会立即改变整页体积色场。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
                Spacer(Modifier.height(12.dp))
                com.campusai.core.designsystem.CaesarSlidingSelector(
                    options = SpectraEnvironment.entries.map(::environmentLabel),
                    selectedIndex = SpectraEnvironment.entries.indexOf(preferences.environment),
                    onSelected = { index ->
                        SpectraEnvironment.entries.getOrNull(index)?.let { environment ->
                            onFeedback()
                            scope.launch { repository.setEnvironment(environment) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                EnvironmentSample(preferences.environment)
            }
            DividerInset()
            SettingSelector(Icons.Rounded.Speed, "渲染质量", RenderQuality.entries, preferences.renderQuality, { qualityLabel(it) }) { onFeedback(); scope.launch { repository.setQuality(it) } }
            DividerInset()
            SettingSwitch(Icons.Rounded.MotionPhotosOff, "动态与折射", preferences.motionMode == MotionMode.ON) { onFeedback(); scope.launch { repository.setMotion(if (it) MotionMode.ON else MotionMode.OFF) } }
            DividerInset()
            SettingSwitch(Icons.Rounded.GraphicEq, "计时完成声音", preferences.soundEnabled) { onFeedback(); scope.launch { repository.setSound(it) } }
        }
    }
}

@Composable
private fun EnvironmentSample(environment: SpectraEnvironment) {
    val colors = environmentColors(environment)
    Box(
        Modifier.fillMaxWidth().height(88.dp).background(Brush.horizontalGradient(colors), RoundedCornerShape(44.dp)).border(1.dp, Color.White.copy(.8f), RoundedCornerShape(44.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(.54f), Color.Transparent)), RoundedCornerShape(44.dp)))
        Text(environmentLabel(environment), style = MaterialTheme.typography.labelMedium, color = SpectraColors.Ink)
    }
}

private data class AchievementUi(val name: String, val description: String, val progress: Int, val target: Int, val colors: List<Color>) { val unlocked get() = progress >= target }

private fun buildAchievements(records: List<TimeRecord>, remoteStreak: Int): List<AchievementUi> {
    val totalMinutes = records.sumOf { it.durationMinutes }.toInt()
    val focusCount = records.count { it.category.contains("专注") || it.durationMinutes >= 25 }
    val categories = records.map { it.category }.filter { it.isNotBlank() }.distinct().size
    val streak = maxOf(remoteStreak, currentStreak(records))
    return listOf(
        AchievementUi("第一束光", "完成第一条时间记录", records.size, 1, listOf(SpectraColors.Cyan, SpectraColors.Focus)),
        AchievementUi("专注起航", "完成一次 25 分钟专注", focusCount, 1, listOf(SpectraColors.Focus, SpectraColors.Violet)),
        AchievementUi("稳定节奏", "连续记录 7 天", streak, 7, listOf(SpectraColors.Warm, SpectraColors.Rose)),
        AchievementUi("深度轨道", "累计投入 10 小时", totalMinutes, 600, listOf(SpectraColors.Violet, SpectraColors.Rose)),
        AchievementUi("时间建筑师", "完成 25 条记录", records.size, 25, listOf(SpectraColors.Cyan, SpectraColors.Warm)),
        AchievementUi("完整光谱", "覆盖 5 个学习分类", categories, 5, listOf(SpectraColors.Cyan, SpectraColors.Violet, SpectraColors.Warm)),
        AchievementUi("百小时节点", "累计投入 100 小时", totalMinutes, 6000, listOf(SpectraColors.Warm, SpectraColors.Violet)),
    )
}

private fun currentStreak(records: List<TimeRecord>): Int {
    val dates = records.map { Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate() }.toSet()
    var day = java.time.LocalDate.now()
    if (day !in dates) day = day.minusDays(1)
    var count = 0
    while (day in dates) { count++; day = day.minusDays(1) }
    return count
}

@Composable
private fun AchievementCard(item: AchievementUi, modifier: Modifier = Modifier.width(150.dp)) {
    GlassPanel(modifier.height(132.dp), radius = 16) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            OpticalBadge(item.colors, item.unlocked, item.progress.toFloat() / item.target.coerceAtLeast(1), Modifier.size(48.dp))
            Column {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(if (item.unlocked) 1f else .55f))
                Text(if (item.unlocked) "已解锁" else "${item.progress.coerceAtMost(item.target)}/${item.target} · ${item.description}", maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.56f))
            }
        }
    }
}

@Composable
private fun OpticalBadge(colors: List<Color>, unlocked: Boolean, progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * .34f
        val alpha = if (unlocked) 1f else .28f
        drawArc(Brush.sweepGradient(colors.map { it.copy(alpha) }), 48f, 264f, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(size.minDimension * .11f, cap = StrokeCap.Round))
        val nodes = listOf(310f, 238f, 166f, 94f, 22f)
        nodes.forEachIndexed { index, degrees ->
            val rad = Math.toRadians(degrees.toDouble())
            val x = c.x + kotlin.math.cos(rad).toFloat() * r
            val y = c.y + kotlin.math.sin(rad).toFloat() * r
            drawCircle(colors[index % colors.size].copy(if (progress * 5f >= index + 1) alpha else .16f), size.minDimension * .07f, androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable private fun StatCell(value: String, label: String, modifier: Modifier) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.58f)) }
@Composable private fun VerticalRule() = Box(Modifier.width(1.dp).height(34.dp).background(SpectraColors.Silver.copy(.62f)))

private fun aiProviderLabel(value: AiProvider) = when (value) { AiProvider.AUTO -> "自动 · 本地优先"; AiProvider.DEEPSEEK -> "DeepSeek · 我的 Key"; AiProvider.LOCAL -> "本地模型" }
private fun visualStyleLabel(value: SpectraVisualStyle) = when (value) { SpectraVisualStyle.CLASSIC -> "经典"; SpectraVisualStyle.FLUID -> "Strba Fluid" }
private fun environmentLabel(value: SpectraEnvironment) = when (value) { SpectraEnvironment.ORIGINAL -> "Original 原生"; SpectraEnvironment.OCEAN -> "Ocean 海流"; SpectraEnvironment.ULTRAVIOLET -> "Ultraviolet 紫外"; SpectraEnvironment.EMBER -> "Ember 余烬" }
private fun themeLabel(value: ThemeMode) = when (value) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" }
private fun qualityLabel(value: RenderQuality) = when (value) { RenderQuality.AUTO -> "自动"; RenderQuality.LOW -> "低"; RenderQuality.HIGH -> "高" }
private fun environmentColors(value: SpectraEnvironment) = when (value) { SpectraEnvironment.ORIGINAL -> listOf(SpectraColors.Cyan, SpectraColors.Violet, SpectraColors.Warm, SpectraColors.Rose); SpectraEnvironment.OCEAN -> listOf(Color(0xFF0C8DBF), SpectraColors.Cyan, SpectraColors.Focus); SpectraEnvironment.ULTRAVIOLET -> listOf(Color(0xFF5138D7), SpectraColors.Violet, SpectraColors.Rose); SpectraEnvironment.EMBER -> listOf(Color(0xFFE04B32), SpectraColors.Warm, Color(0xFFFFC457)) }

@Composable
private fun LocalAiSettings(
    preferences: UserPreferences,
    repository: UserPreferencesRepository,
    manager: LocalModelManager,
    engine: LocalMnnAiEngine,
    personalKeyStore: PersonalDeepSeekKeyStore,
) {
    val scope = rememberCoroutineScope()
    val modelStates by manager.states.collectAsState()
    val selectedModel by manager.selection.collectAsState()
    var confirmMobile by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LocalModelMode?>(null) }
    var confirmRedownload by remember { mutableStateOf<LocalModelMode?>(null) }
    var personalKey by remember { mutableStateOf("") }
    var personalKeyVisible by remember { mutableStateOf(false) }
    var personalKeySaved by remember { mutableStateOf(personalKeyStore.hasKey()) }
    var personalKeyMessage by remember { mutableStateOf<String?>(null) }
    val localModes = listOf(LocalModelMode.FAST, LocalModelMode.QUALITY)
    val largestDownload = localModes.maxOf { manager.manifestFor(it.modelId).totalBytes }
    GlassPanel(Modifier.fillMaxWidth(), radius = 16, emphasized = true) {
        Column {
            SettingSelector(Icons.Rounded.Cloud, "AI 运行方式", AiProvider.entries, preferences.aiProvider, {
                when (it) { AiProvider.AUTO -> "自动"; AiProvider.DEEPSEEK -> "DeepSeek · 我的 Key"; AiProvider.LOCAL -> "本地模型" }
            }) { scope.launch { repository.setAiProvider(it) } }
            Text(
                when (preferences.aiProvider) {
                    AiProvider.AUTO -> "优先使用当前本地档位；本地不可用时仅展示一次性 DeepSeek 升级选项，绝不会自动上传对话。"
                    AiProvider.DEEPSEEK -> "固定使用你自己的 DeepSeek Key，不会自动切换本地，也不会调用任何平台共享额度。"
                    AiProvider.LOCAL -> "提示词、学习统计、课程表与回复仅在本机处理；本地失败不会静默调用云端。"
                },
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.66f),
            )
            DividerInset()
            SettingSelector(
                Icons.Rounded.Speed,
                "本地模型档位",
                localModes,
                selectedModel.mode,
                ::localModelModeLabel,
                manager::selectMode,
            )
            Text(
                "新会话默认 ${localModelModeLabel(selectedModel.mode)} · ${localModelStatusText(selectedModel.state)}",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedModel.state is LocalModelState.Error || selectedModel.state is LocalModelState.Incompatible) SpectraColors.Error else MaterialTheme.colorScheme.onSurface.copy(.70f),
            )
            Text(
                "这里只决定新会话第一次发消息时锁定 FAST 2B 或 DEEP 4B；不会自动下载、切换当前会话，也不会暂停另一模型的下载。",
                Modifier.padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.60f),
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("本机模型", style = MaterialTheme.typography.titleMedium)
                Text(
                    "两套模型独立下载、暂停、校验和删除，可以同时保留。下载完成不会自动改变新会话默认档位。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(.62f),
                )
                localModes.forEach { mode ->
                    val manifest = manager.manifestFor(mode.modelId)
                    val state = modelStates[mode.modelId] ?: LocalModelState.Checking
                    LocalModelDownloadCard(
                        mode = mode,
                        state = state,
                        manifest = manifest,
                        occupiedBytes = manager.runtimeFor(mode.modelId).storage.occupiedBytes(),
                        selected = selectedModel.mode == mode,
                        onPrimaryAction = {
                            when (state) {
                                is LocalModelState.Downloading -> manager.pause(mode)
                                is LocalModelState.Paused -> manager.resume(mode, preferences.localModelWifiOnly)
                                else -> manager.download(mode, preferences.localModelWifiOnly)
                            }
                        },
                        onRedownload = { confirmRedownload = mode },
                        onDelete = { confirmDelete = mode },
                    )
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
                Text("Qwen3.5-2B / 4B · Apache-2.0 · MNN official 4-bit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
            }
        }
    }
    if (confirmMobile) AlertDialog(
        onDismissRequest = { confirmMobile = false },
        title = { Text("允许移动网络下载？") },
        text = { Text("单个模型最大约 ${formatDownloadBytes(largestDownload)}，可能产生较大流量费用。确认后仅改变下载网络限制。") },
        confirmButton = { TextButton(onClick = { confirmMobile = false; scope.launch { repository.setLocalModelWifiOnly(false) } }) { Text("确认允许") } },
        dismissButton = { TextButton(onClick = { confirmMobile = false }) { Text("保持仅 Wi-Fi") } },
    )
    confirmDelete?.let { targetMode -> Dialog(onDismissRequest = { confirmDelete = null }) {
        val targetManifest = manager.manifestFor(targetMode.modelId)
        GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("删除 ${localModelModeLabel(targetMode)}", style = MaterialTheme.typography.titleLarge)
                Text("只删除 ${targetManifest.displayName}，释放约 ${formatBytes(manager.runtimeFor(targetMode.modelId).storage.occupiedBytes())}；不会删除或暂停另一模型的下载。当前本地生成会先停止，聊天报告保留。", style = MaterialTheme.typography.bodyMedium)
                SlideConfirm("滑动删除模型", onConfirm = {
                    confirmDelete = null
                    scope.launch {
                        manager.deleteModel(targetMode) { engine.releaseAndWait() }
                    }
                })
                TextButton(onClick = { confirmDelete = null }, modifier = Modifier.align(Alignment.End)) { Text("取消") }
            }
        }
    } }
    confirmRedownload?.let { targetMode -> Dialog(onDismissRequest = { confirmRedownload = null }) {
        val targetManifest = manager.manifestFor(targetMode.modelId)
        GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("重新下载 ${localModelModeLabel(targetMode)}", style = MaterialTheme.typography.titleLarge)
                Text("只重下 ${targetManifest.displayName}；不会删除或暂停另一模型的下载。当前本地生成会先停止，然后按现有网络限制重新下载和校验。", style = MaterialTheme.typography.bodyMedium)
                SlideConfirm("滑动重新下载", onConfirm = {
                    confirmRedownload = null
                    scope.launch {
                        if (manager.deleteModel(targetMode) { engine.releaseAndWait() }) {
                            manager.download(targetMode, preferences.localModelWifiOnly)
                        }
                    }
                })
                TextButton(onClick = { confirmRedownload = null }, modifier = Modifier.align(Alignment.End)) { Text("取消") }
            }
        }
    } }
}

@Composable
private fun LocalModelDownloadCard(
    mode: LocalModelMode,
    state: LocalModelState,
    manifest: LocalModelManifest,
    occupiedBytes: Long,
    selected: Boolean,
    onPrimaryAction: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        radius = 18,
        emphasized = selected,
        shadowed = false,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhoneAndroid, null)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(localModelModeLabel(mode), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${manifest.displayName} · ${manifest.quantization}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(.62f),
                    )
                }
                if (selected) Text(
                    "新会话默认",
                    style = MaterialTheme.typography.labelMedium,
                    color = SpectraColors.Focus,
                )
            }
            Text(
                if (mode == LocalModelMode.FAST) "更快、更省电，适合日常对话与简单工具。" else "更强推理与多模态，适合复杂任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(.66f),
            )
            Text(
                "下载 ${formatDownloadBytes(manifest.totalBytes)} · 需预留 ${formatDownloadBytes(manifest.totalBytes + manifest.safetyMarginBytes)} · 当前占用 ${formatBytes(occupiedBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(.56f),
            )
            Text(
                localModelStatusText(state),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is LocalModelState.Error || state is LocalModelState.Incompatible) SpectraColors.Error else MaterialTheme.colorScheme.onSurface.copy(.72f),
            )
            (state as? LocalModelState.Downloading)?.let { downloading ->
                LinearProgressIndicator(
                    progress = { downloading.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = SpectraColors.Focus,
                    trackColor = SpectraColors.Silver.copy(.42f),
                )
            }
            localModelActionLabel(state)?.let { label ->
                SpectraPrimaryButton(
                    text = label,
                    onClick = onPrimaryAction,
                    icon = Icons.Rounded.Download,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val canDelete = occupiedBytes > 0L && state != LocalModelState.Checking &&
                state != LocalModelState.Verifying && state != LocalModelState.Loading
            if (state == LocalModelState.Ready || canDelete) {
                Row(Modifier.align(Alignment.End)) {
                    if (state == LocalModelState.Ready) {
                        TextButton(onClick = onRedownload) { Text("重新下载") }
                    }
                    if (canDelete) {
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, null, tint = SpectraColors.Error)
                            Spacer(Modifier.size(6.dp))
                            Text(if (state == LocalModelState.Ready) "删除模型" else "删除下载", color = SpectraColors.Error)
                        }
                    }
                }
            }
        }
    }
}

internal fun localModelModeLabel(mode: LocalModelMode): String = when (mode) {
    LocalModelMode.FAST -> "FAST · 2B"
    LocalModelMode.QUALITY -> "DEEP · 4B"
}

internal fun localModelActionLabel(state: LocalModelState): String? = when (state) {
    LocalModelState.NotDownloaded -> "下载模型"
    is LocalModelState.Downloading -> "暂停下载"
    is LocalModelState.Paused -> "继续下载"
    is LocalModelState.Error -> "重试下载"
    LocalModelState.Ready, LocalModelState.Checking, LocalModelState.Verifying,
    LocalModelState.Loading, is LocalModelState.Incompatible -> null
}

internal fun localModelStatusText(state: LocalModelState): String = when (state) {
    LocalModelState.NotDownloaded -> "未下载 · 不会自动下载"
    LocalModelState.Checking -> "正在检查模型文件"
    is LocalModelState.Downloading -> "下载中 ${(state.progress * 100).toInt()}% · ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
    is LocalModelState.Paused -> "已暂停 · ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
    LocalModelState.Verifying -> "正在逐文件执行 SHA-256 校验"
    LocalModelState.Ready -> "Ready · 可完全离线使用"
    LocalModelState.Loading -> "正在从应用私有目录加载"
    is LocalModelState.Error -> "${state.message}（${state.code}）"
    is LocalModelState.Incompatible -> state.reason
}

private fun formatDownloadBytes(bytes: Long): String = "%.2f GB".format(bytes.toDouble() / 1_000_000_000.0)

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
        com.campusai.core.designsystem.CaesarSlidingSelector(
            options = values.map(text),
            selectedIndex = values.indexOf(selected).coerceAtLeast(0),
            onSelected = { index -> values.getOrNull(index)?.let(onSelect) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingSwitch(icon: ImageVector, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.size(12.dp)); Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Switch(checked, onChecked) }
}

@Composable
private fun SettingLink(icon: ImageVector, label: String, subtitle: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.56f)) }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.45f))
    }
}

@Composable private fun DividerInset() = HorizontalDivider(Modifier.padding(start = 52.dp), color = SpectraColors.Silver.copy(.68f))
