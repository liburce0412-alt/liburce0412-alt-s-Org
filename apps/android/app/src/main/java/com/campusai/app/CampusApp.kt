package com.campusai.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusai.core.database.CampusDao
import com.campusai.core.auth.AuthRepository
import com.campusai.core.designsystem.CampusTheme
import com.campusai.core.designsystem.DefaultSpectraTokens
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.ProvideSpectraExperience
import com.campusai.core.designsystem.ProvideSpectraTokens
import com.campusai.core.designsystem.SpectraBackdrop
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.SpectraPhase
import com.campusai.core.designsystem.SpectraTheme
import com.campusai.core.designsystem.SpectraVisualStyle
import com.campusai.core.designsystem.SpectraVisualStyleController
import com.campusai.core.designsystem.spectraTokensForStyle
import com.campusai.core.model.MotionMode
import com.campusai.core.model.UiState
import com.campusai.core.preferences.UserPreferences
import com.campusai.core.preferences.UserPreferencesRepository
import com.campusai.features.time.TimeViewModel
import com.campusai.features.time.TimeViewModelFactory
import com.campusai.features.ai.AiScreen
import com.campusai.features.ai.AiViewModel
import com.campusai.features.ai.AiViewModelFactory
import com.campusai.features.ai.AiContextSnapshot
import com.campusai.features.ai.AiPostContext
import com.campusai.features.community.CampusViewModel
import com.campusai.features.community.CampusRepository
import com.campusai.core.health.BandBridgeIntents
import com.campusai.core.sync.CampusSyncScheduler
import com.campusai.core.localai.LocalMnnAiEngine
import com.campusai.core.localai.LocalModelManager
import com.campusai.core.agent.MnnAgentEngineFactory
import com.campusai.core.security.PersonalDeepSeekKeyStore
import com.campusai.core.profile.ProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import android.net.Uri

enum class MainDestination(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Rounded.Home),
    TIME("时间", Icons.Rounded.Schedule),
    CAMPUS("树洞", Icons.Rounded.Forum),
    MARKET("心愿墙", Icons.Rounded.FavoriteBorder),
    PROFILE("我的", Icons.Rounded.Person),
}

@Composable
fun CampusApp(dao: CampusDao, initialSharedImage: Uri? = null) {
    val context = LocalContext.current
    val preferencesRepository = remember { UserPreferencesRepository(context.applicationContext) }
    val localModelManager = remember { LocalModelManager(context.applicationContext) }
    val localAiEngine = remember { LocalMnnAiEngine(context.applicationContext, localModelManager) }
    val agentLocalAiEngine = remember {
        MnnAgentEngineFactory.create(localAiEngine, localModelManager::manifestFor)
    }
    val personalDeepSeekKeyStore = remember { PersonalDeepSeekKeyStore(context.applicationContext) }
    val profileRepository = remember { ProfileRepository() }
    val campusRepository = remember { CampusRepository() }
    val profileState by profileRepository.state.collectAsState()
    DisposableEffect(localModelManager, localAiEngine) {
        onDispose {
            localAiEngine.shutdown()
            localModelManager.close()
        }
    }
    val authRepository = remember { AuthRepository(context.applicationContext) }
    val authState by authRepository.state.collectAsState()
    val preferences by preferencesRepository.preferences.collectAsState(initial = UserPreferences())
    val timeViewModel: TimeViewModel = viewModel(factory = TimeViewModelFactory(dao, context.applicationContext, authState.userId.takeIf { authState.signedIn }))
    val aiViewModel: AiViewModel = viewModel(factory = AiViewModelFactory(dao, context.applicationContext, preferencesRepository, localModelManager, agentLocalAiEngine, personalDeepSeekKeyStore, campusRepository, profileRepository))
    val aiRuntimeState by aiViewModel.state.collectAsState()
    val healthState by aiViewModel.healthState.collectAsState()
    val campusViewModel: CampusViewModel = viewModel()
    val campusState by campusViewModel.state.collectAsState()
    val announcementState by campusViewModel.announcements.collectAsState()
    val records by timeViewModel.timeRecords.collectAsState()
    val courses by timeViewModel.courses.collectAsState()
    val dailyGreeting by aiViewModel.dailyGreeting.collectAsState()
    val contextPosts = when (val posts = campusState.posts) {
        is UiState.Data -> posts.value
        is UiState.Offline -> posts.value
        else -> emptyList()
    }
    val aiSnapshot = AiContextSnapshot(
        userId = authState.userId,
        displayName = profileState.profile.displayName,
        records = records,
        courses = courses,
        visiblePosts = contextPosts.map { post ->
            AiPostContext(post.id, post.authorId, post.body, post.topic, post.likes, post.comments, post.createdAt)
        },
    )
    var destination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    var focusMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAi by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var showMessages by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val unreadMessages = when (val conversations = campusState.conversations) {
        is UiState.Data -> conversations.value.sumOf { it.unreadCount }
        is UiState.Offline -> conversations.value.sumOf { it.unreadCount }
        else -> 0
    }
    val fullscreenOverlay = focusMinutes != null || showAi || showMessages

    LaunchedEffect(initialSharedImage) {
        initialSharedImage?.let {
            showAi = true
            aiViewModel.attachImage(it)
        }
    }

    BackHandler(enabled = showLogin || fullscreenOverlay) {
        when {
            showLogin -> { authRepository.clearError(); showLogin = false }
            showAi -> showAi = false
            showMessages -> {
                if (campusState.activeConversationId != null) campusViewModel.closeMessageThread()
                else showMessages = false
            }
            focusMinutes != null -> focusMinutes = null
        }
    }

    LaunchedEffect(authState.signedIn) {
        if (authState.signedIn) {
            authRepository.refresh()
            while (true) {
                delay(45 * 60 * 1_000L)
                authRepository.refresh()
            }
        }
    }

    LaunchedEffect(authState.signedIn, authState.userId) {
        campusViewModel.setSession(authState.signedIn, authState.userId)
    }
    LaunchedEffect(authState.signedIn, authState.userId) {
        timeViewModel.setActiveUser(authState.userId.takeIf { authState.signedIn })
        profileRepository.load(
            userId = authState.userId.takeIf { authState.signedIn }.orEmpty(),
            fallbackName = authState.email.substringBefore('@').ifBlank { "Caesar 用户" },
        )
        if (authState.signedIn) CampusSyncScheduler.enqueue(context.applicationContext)
    }
    LaunchedEffect(aiSnapshot, authState.userId) { aiViewModel.ensureDailyGreeting(aiSnapshot) }
    LaunchedEffect(Unit) { aiViewModel.refreshHealthStatus() }
    SideEffect {
        SpectraVisualStyleController.set(preferences.visualStyle)
    }

    CampusTheme(preferences.themeMode) {
        val styledTokens = spectraTokensForStyle(DefaultSpectraTokens, preferences.visualStyle)
        ProvideSpectraExperience(preferences.visualStyle) {
        ProvideSpectraTokens(
            styledTokens.copy(
                motion = if (preferences.motionMode == MotionMode.ON) styledTokens.motion else styledTokens.motion.disabled(),
            ),
        ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SpectraBackdrop(
                environment = preferences.environment,
                quality = preferences.renderQuality,
                motion = preferences.motionMode,
                active = !showLogin,
                phase = when {
                    focusMinutes != null -> SpectraPhase.FOCUS
                    aiRuntimeState.streaming -> SpectraPhase.THINKING
                    else -> SpectraPhase.AMBIENT
                },
            )
            if (!showLogin) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        AnimatedVisibility(
                            visible = focusMinutes == null && !showAi && !showMessages,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) {
                            SpectraDock(
                                destination = destination,
                                motionEnabled = preferences.motionMode == MotionMode.ON,
                                onDestination = { destination = it },
                            )
                        }
                    },
                ) { padding ->
                    if (!fullscreenOverlay) {
                        AnimatedContent(
                            targetState = destination,
                            transitionSpec = {
                                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                if (preferences.motionMode == MotionMode.ON) {
                                    if (preferences.visualStyle == SpectraVisualStyle.FLUID) {
                                        (fadeIn() + slideInVertically { it / 18 }) togetherWith
                                            (fadeOut() + slideOutVertically { -it / 28 })
                                    } else {
                                        (fadeIn() + slideInHorizontally { it * direction / 10 }) togetherWith
                                            (fadeOut() + slideOutHorizontally { -it * direction / 10 })
                                    }
                                } else {
                                    fadeIn() togetherWith fadeOut()
                                }
                            },
                            contentKey = MainDestination::name,
                            label = "main-destination",
                        ) { selected ->
                            when (selected) {
                            MainDestination.HOME -> HomeScreen(
                                 records = records,
                                 displayName = profileState.profile.displayName,
                                 avatarUrl = profileState.profile.avatarUrl,
                                 dailyText = dailyGreeting?.text.orEmpty(),
                                 announcements = announcementState,
                                 onRefreshAnnouncements = campusViewModel::refreshAnnouncements,
                                 onStartRecord = { destination = MainDestination.TIME },
                                 onOpenAi = { showAi = true },
                                 healthState = healthState,
                                 onRefreshHealth = aiViewModel::refreshHealthStatus,
                                 onStartBand = aiViewModel::startBandSession,
                                 onStopBand = aiViewModel::stopBandSession,
                                 onSyncBandHistory = aiViewModel::triggerBandHistorySync,
                                 onBandDiagnostics = {
                                     runCatching { context.startActivity(BandBridgeIntents.diagnostics()) }
                                         .onFailure {
                                             appScope.launch { snackbar.showSnackbar("CaesarBandBridge 诊断页不可用。") }
                                         }
                                 },
                                contentPadding = padding,
                            )
                            MainDestination.TIME -> TimeScreen(
                                records = records,
                                viewModel = timeViewModel,
                                onStartFocus = { focusMinutes = it },
                                onMessage = { message, action -> snackbar.showSnackbar(message, actionLabel = action) },
                                contentPadding = padding,
                            )
                            MainDestination.CAMPUS -> CampusScreen(
                                state = campusState,
                                signedIn = authState.signedIn,
                                userId = authState.userId,
                                displayName = profileState.profile.displayName
                                    .ifBlank { authState.email.substringBefore('@') }
                                    .ifBlank { "我" },
                                viewModel = campusViewModel,
                                onLogin = { showLogin = true },
                                contentPadding = padding,
                            )
                            MainDestination.MARKET -> MarketScreen(
                                state = campusState,
                                signedIn = authState.signedIn,
                                userId = authState.userId,
                                viewModel = campusViewModel,
                                onLogin = { showLogin = true },
                                onOpenConversation = { conversationId ->
                                    campusViewModel.openMessageThread(conversationId)
                                    showMessages = true
                                },
                                contentPadding = padding,
                            )
                            MainDestination.PROFILE -> ProfileScreen(
                                preferences = preferences,
                                repository = preferencesRepository,
                                records = records,
                                authState = authState,
                                unreadMessages = unreadMessages,
                                onLogin = { showLogin = true },
                                onSignOut = authRepository::signOut,
                                onOpenMessages = { showMessages = true },
                                localModelManager = localModelManager,
                                localAiEngine = localAiEngine,
                                personalDeepSeekKeyStore = personalDeepSeekKeyStore,
                                profileRepository = profileRepository,
                                contentPadding = padding,
                            )
                        }
                    }
                    } else {
                        Spacer(Modifier.fillMaxSize())
                    }
                }
                focusMinutes?.let { preset ->
                    FocusSessionScreen(
                        presetMinutes = preset,
                        motionEnabled = preferences.motionMode == MotionMode.ON,
                        soundEnabled = preferences.soundEnabled,
                        onMinimize = { focusMinutes = null },
                        onFinish = { elapsedMinutes ->
                            val end = System.currentTimeMillis()
                            timeViewModel.addTimeRecord("专注 $elapsedMinutes 分钟", "专注", end - elapsedMinutes * 60_000L, end, "专注计时自动记录")
                            focusMinutes = null
                        },
                    )
                }
                if (showAi && focusMinutes == null) {
                    AiScreen(
                        viewModel = aiViewModel,
                        snapshot = aiSnapshot,
                        motionEnabled = preferences.motionMode == MotionMode.ON,
                        visualStyle = preferences.visualStyle,
                        onVisualStyleChange = { style -> appScope.launch { preferencesRepository.setVisualStyle(style) } },
                        onBack = { showAi = false },
                    )
                }
                if (showMessages && focusMinutes == null && !showAi) {
                    MessageCenterScreen(
                        state = campusState,
                        userId = authState.userId,
                        viewModel = campusViewModel,
                        onBack = {
                            campusViewModel.closeMessageThread()
                            showMessages = false
                        },
                    )
                }
            }
            if (showLogin && focusMinutes == null) {
                AuthScreen(
                    state = authState,
                    onSignIn = authRepository::signIn,
                    onSignUp = authRepository::signUp,
                    onClearMessage = authRepository::clearError,
                    onBack = { authRepository.clearError(); showLogin = false },
                )
            }
        }
        }
        }
    }
}

@Composable
private fun SpectraDock(
    destination: MainDestination,
    motionEnabled: Boolean,
    onDestination: (MainDestination) -> Unit,
) {
    val entries = MainDestination.entries
    val layout = SpectraTheme.layout
    val fluid = SpectraTheme.isFluid
    val dark = MaterialTheme.colorScheme.background.luminance() < .35f
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animatedCenter = remember { Animatable(0f) }
    var dockWidth by remember { mutableFloatStateOf(0f) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var visualIndex by remember { mutableIntStateOf(destination.ordinal) }

    fun centerFor(index: Int): Float {
        val slot = dockWidth / entries.size.coerceAtLeast(1)
        return slot * (index + .5f)
    }

    LaunchedEffect(destination, dockWidth, dragging, motionEnabled) {
        if (dockWidth <= 0f || dragging) return@LaunchedEffect
        visualIndex = destination.ordinal
        val target = centerFor(destination.ordinal)
        if (!motionEnabled || animatedCenter.value == 0f) animatedCenter.snapTo(target)
        else animatedCenter.animateTo(
            target,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = layout.dockHorizontalPadding, vertical = layout.dockVerticalPadding),
    ) {
        GlassPanel(
            Modifier.fillMaxWidth().height(layout.dockHeight),
            radius = (layout.dockHeight.value / 2f).roundToInt(),
            emphasized = true,
            shadowed = false,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (fluid) 5.dp else 7.dp)
                    .onSizeChanged {
                        dockWidth = it.width.toFloat()
                        if (animatedCenter.value == 0f) {
                            dragCenter = centerFor(destination.ordinal)
                        }
                    }
                    .pointerInput(dockWidth, motionEnabled) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                if (dockWidth <= 0f) return@detectHorizontalDragGestures
                                dragging = true
                                dragCenter = animatedCenter.value.takeIf { it > 0f } ?: centerFor(destination.ordinal)
                                dragDelta = 0f
                                visualIndex = destination.ordinal
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                val slot = dockWidth / entries.size
                                dragCenter = (dragCenter + amount).coerceIn(slot * .5f, dockWidth - slot * .5f)
                                dragDelta = if (motionEnabled) amount else 0f
                                val next = ((dragCenter / slot) - .5f).roundToInt().coerceIn(entries.indices)
                                if (next != visualIndex) {
                                    visualIndex = next
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                val targetIndex = visualIndex.coerceIn(entries.indices)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    animatedCenter.snapTo(dragCenter)
                                    dragging = false
                                    dragDelta = 0f
                                    onDestination(entries[targetIndex])
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    animatedCenter.snapTo(dragCenter)
                                    dragging = false
                                    dragDelta = 0f
                                    visualIndex = destination.ordinal
                                }
                            },
                        )
                    },
            ) {
                val center = if (dragging) dragCenter else animatedCenter.value
                LiquidDockSelection(
                    centerX = center,
                    dragDelta = dragDelta,
                    motionEnabled = motionEnabled,
                    fluid = fluid,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    Modifier.fillMaxSize().semantics { selectableGroup() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    entries.forEachIndexed { index, item ->
                        val selected = index == if (dragging) visualIndex else destination.ordinal
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("main-nav-${item.name.lowercase()}")
                                .selectable(
                                    selected = selected,
                                    interactionSource = remember(item) { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        visualIndex = index
                                        if (item != destination) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDestination(item)
                                    },
                                )
                                .semantics(mergeDescendants = true) {
                                    contentDescription = item.label
                                    this.selected = selected
                                    stateDescription = if (selected) "当前页" else "未选中"
                                },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                item.icon,
                                null,
                                tint = if (selected) {
                                    if (dark) Color.White else SpectraColors.Ink
                                } else MaterialTheme.colorScheme.onSurface.copy(.62f),
                                modifier = Modifier.size(if (selected) 20.dp else 22.dp),
                            )
                            if (selected) {
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    item.label,
                                    color = if (dark) Color.White else SpectraColors.Ink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidDockSelection(
    centerX: Float,
    dragDelta: Float,
    motionEnabled: Boolean,
    fluid: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .35f
    Canvas(modifier) {
        if (centerX <= 0f) return@Canvas
        val baseHalfWidth = with(density) { (if (fluid) 34.dp else 38.dp).toPx() }
        val halfHeight = with(density) { (if (fluid) 19.dp else 23.dp).toPx() }
        val maxStretch = with(density) { (if (fluid) 16.dp else 12.dp).toPx() }
        val stretch = if (motionEnabled) (abs(dragDelta) * .68f).coerceAtMost(maxStretch) else 0f
        val direction = when {
            dragDelta > .4f -> 1f
            dragDelta < -.4f -> -1f
            else -> 0f
        }
        val left = centerX - baseHalfWidth - if (direction < 0f) stretch else 0f
        val right = centerX + baseHalfWidth + if (direction > 0f) stretch else 0f
        val top = center.y - halfHeight
        val bottom = center.y + halfHeight
        val radius = halfHeight
        val liquid = Path().apply {
            moveTo(left + radius, top)
            lineTo(right - radius, top)
            cubicTo(right - radius * .28f, top, right, top + radius * .34f, right, center.y)
            cubicTo(right, bottom - radius * .34f, right - radius * .28f, bottom, right - radius, bottom)
            lineTo(left + radius, bottom)
            cubicTo(left + radius * .28f, bottom, left, bottom - radius * .34f, left, center.y)
            cubicTo(left, top + radius * .34f, left + radius * .28f, top, left + radius, top)
            close()
        }
        val bodyBrush = Brush.horizontalGradient(
            colors = if (dark) listOf(
                Color(0xFF15171D).copy(if (fluid) .84f else .94f),
                Color(0xFF373A42).copy(if (fluid) .72f else .84f),
                Color(0xFF15171D).copy(if (fluid) .84f else .94f),
            ) else listOf(
                Color.White.copy(if (fluid) .70f else .86f),
                Color(0xFFE6E7E9).copy(if (fluid) .58f else .78f),
                Color.White.copy(if (fluid) .66f else .82f),
            ),
            startX = left,
            endX = right,
        )
        drawPath(liquid, bodyBrush)
        drawPath(
            liquid,
            Color.White.copy(if (dark) .42f else .72f),
            style = Stroke(width = with(density) { 1.dp.toPx() }),
        )
        drawLine(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(.82f), Color.Transparent),
                startX = left,
                endX = right,
            ),
            start = Offset(left + radius * .7f, top + with(density) { 3.dp.toPx() }),
            end = Offset(right - radius * .7f, top + with(density) { 3.dp.toPx() }),
            strokeWidth = with(density) { 1.dp.toPx() },
        )
    }
}
