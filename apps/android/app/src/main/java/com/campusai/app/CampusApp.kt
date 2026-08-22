package com.campusai.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusai.core.database.CampusDao
import com.campusai.core.auth.AuthRepository
import com.campusai.core.designsystem.CampusTheme
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SpectraBackdrop
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.model.MotionMode
import com.campusai.core.model.UiState
import com.campusai.core.preferences.UserPreferences
import com.campusai.core.preferences.UserPreferencesRepository
import com.campusai.features.time.TimeViewModel
import com.campusai.features.time.TimeViewModelFactory
import com.campusai.features.ai.AiScreen
import com.campusai.features.ai.AiViewModel
import com.campusai.features.ai.AiViewModelFactory
import com.campusai.features.community.CampusViewModel
import com.campusai.core.sync.CampusSyncScheduler
import com.campusai.core.localai.LocalMnnAiEngine
import com.campusai.core.localai.LocalModelManager
import kotlinx.coroutines.delay

enum class MainDestination(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Rounded.Home),
    TIME("时间", Icons.Rounded.Schedule),
    CAMPUS("校园", Icons.Rounded.School),
    MARKET("市场", Icons.Rounded.Storefront),
    PROFILE("我的", Icons.Rounded.Person),
}

@Composable
fun CampusApp(dao: CampusDao) {
    val context = LocalContext.current
    val preferencesRepository = remember { UserPreferencesRepository(context.applicationContext) }
    val localModelManager = remember { LocalModelManager(context.applicationContext) }
    val localAiEngine = remember { LocalMnnAiEngine(context.applicationContext, localModelManager) }
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
    val aiViewModel: AiViewModel = viewModel(factory = AiViewModelFactory(dao, context.applicationContext, preferencesRepository, localModelManager, localAiEngine))
    val campusViewModel: CampusViewModel = viewModel()
    val campusState by campusViewModel.state.collectAsState()
    val records by timeViewModel.timeRecords.collectAsState()
    val courses by timeViewModel.courses.collectAsState()
    var destination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    var focusMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAi by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var showMessages by rememberSaveable { mutableStateOf(false) }
    var showOrders by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val unreadMessages = when (val conversations = campusState.conversations) {
        is UiState.Data -> conversations.value.sumOf { it.unreadCount }
        is UiState.Offline -> conversations.value.sumOf { it.unreadCount }
        else -> 0
    }
    val activeOrders = when (val orders = campusState.orders) {
        is UiState.Data -> orders.value.count { it.status !in listOf("completed", "cancelled") }
        is UiState.Offline -> orders.value.count { it.status !in listOf("completed", "cancelled") }
        else -> 0
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

    LaunchedEffect(authState.signedIn) { campusViewModel.setSignedIn(authState.signedIn) }
    LaunchedEffect(authState.signedIn, authState.userId) {
        timeViewModel.setActiveUser(authState.userId.takeIf { authState.signedIn })
        if (authState.signedIn) CampusSyncScheduler.enqueue(context.applicationContext)
    }

    CampusTheme(preferences.themeMode) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SpectraBackdrop(preferences.environment, preferences.renderQuality, preferences.motionMode)
            if (!showLogin) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        AnimatedVisibility(
                            visible = focusMinutes == null && !showAi && !showMessages && !showOrders,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) { SpectraDock(destination = destination, onDestination = { destination = it }) }
                    },
                ) { padding ->
                    AnimatedContent(destination, label = "main-destination") { selected ->
                        when (selected) {
                            MainDestination.HOME -> HomeScreen(
                                records = records,
                                onStartRecord = { destination = MainDestination.TIME },
                                onOpenAi = { showAi = true },
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
                                    showOrders = false
                                    showMessages = true
                                },
                                onOrderCreated = {
                                    showMessages = false
                                    showOrders = true
                                },
                                contentPadding = padding,
                            )
                            MainDestination.PROFILE -> ProfileScreen(
                                preferences = preferences,
                                repository = preferencesRepository,
                                records = records,
                                authState = authState,
                                unreadMessages = unreadMessages,
                                activeOrders = activeOrders,
                                onLogin = { showLogin = true },
                                onSignOut = authRepository::signOut,
                                onOpenMessages = { showOrders = false; showMessages = true },
                                onOpenOrders = { showMessages = false; showOrders = true },
                                localModelManager = localModelManager,
                                localAiEngine = localAiEngine,
                                contentPadding = padding,
                            )
                        }
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
                    AiScreen(aiViewModel,records,courses,onBack={showAi=false})
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
                if (showOrders && focusMinutes == null && !showAi && !showMessages) {
                    OrdersScreen(
                        state = campusState,
                        userId = authState.userId,
                        viewModel = campusViewModel,
                        onBack = { showOrders = false },
                        onOpenConversation = { conversationId ->
                            campusViewModel.openMessageThread(conversationId)
                            showOrders = false
                            showMessages = true
                        },
                    )
                }
            }
            if (showLogin && focusMinutes == null) {
                AuthScreen(
                    state = authState,
                    onSignIn = authRepository::signIn,
                    onBack = { authRepository.clearError(); showLogin = false },
                )
            }
        }
    }
}

@Composable
private fun SpectraDock(destination: MainDestination, onDestination: (MainDestination) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        GlassPanel(Modifier.fillMaxWidth().height(64.dp), radius = 32, emphasized = true) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainDestination.entries.forEach { item ->
                    val selected = item == destination
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clickable { onDestination(item) }
                            .padding(horizontal = if (selected) 14.dp else 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (selected) Spacer(Modifier.size(34.dp).background(SpectraColors.Ink, CircleShape))
                            Icon(item.icon, item.label, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(.68f), modifier = Modifier.size(21.dp))
                        }
                        if (selected) Text(item.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
