package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.core.database.CampusDatabase
import com.example.core.model.Achievement
import com.example.core.model.AppUpdate
import com.example.core.model.Friend
import com.example.core.model.Goods
import com.example.core.model.TimeRecord
import com.example.core.model.UserMessage
import com.example.core.network.AiRepositoryImpl
import com.example.core.network.UpdateRepositoryImpl
import com.example.features.ai.AiReportUiState
import com.example.features.ai.AiViewModel
import com.example.features.ai.AiViewModelFactory
import com.example.features.market.MarketViewModel
import com.example.features.market.MarketViewModelFactory
import com.example.features.social.SocialViewModel
import com.example.features.social.SocialViewModelFactory
import com.example.features.time.TimeViewModel
import com.example.features.time.TimeViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create DB and API Clients singletons manually for Clean Architecture
        val database = CampusDatabase.getDatabase(this)
        val campusDao = database.campusDao()
        val aiRepo = AiRepositoryImpl(applicationContext)
        val updateRepo = UpdateRepositoryImpl()
        com.example.core.network.SupabaseManager.init(applicationContext)

        setContent {
            MyApplicationTheme {
                CampusMainApp(
                    campusDao = campusDao,
                    aiRepo = aiRepo,
                    updateRepo = updateRepo
                )
            }
        }
    }
}

// ==========================================
// Main Application Scaffold and Router Flow
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusMainApp(
    campusDao: com.example.core.database.CampusDao,
    aiRepo: AiRepositoryImpl,
    updateRepo: UpdateRepositoryImpl
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen State Tracker: "splash", "dashboard", "time", "ai", "market", "social", "wall", "settings", "achievements", "admin"
    var currentScreen by remember { mutableStateOf("splash") }
    var showAppUpdateDialog by remember { mutableStateOf<AppUpdate?>(null) }
    val currentUser by com.example.core.network.SupabaseManager.currentUser.collectAsStateWithLifecycle()
    val isUserAdmin = currentUser?.userRole() == com.example.core.model.UserRole.ADMIN || currentUser?.userRole() == com.example.core.model.UserRole.SUPER_ADMIN
    var initialChatPartnerId by remember { mutableStateOf<String?>(null) }

    // Instantiate ViewModels with static factory injection
    val timeViewModel: TimeViewModel = viewModel(factory = TimeViewModelFactory(campusDao))
    val aiViewModel: AiViewModel = viewModel(factory = AiViewModelFactory(aiRepo))
    val marketViewModel: MarketViewModel = viewModel(factory = MarketViewModelFactory(campusDao))
    val socialViewModel: SocialViewModel = viewModel(factory = SocialViewModelFactory(campusDao))

    // Observe State Flows safely
    val timeRecords by timeViewModel.timeRecords.collectAsStateWithLifecycle()
    val filteredGoods by marketViewModel.filteredGoods.collectAsStateWithLifecycle()
    val friendsList by socialViewModel.friends.collectAsStateWithLifecycle()
    val wallMessages by socialViewModel.wallMessages.collectAsStateWithLifecycle()

    // Trigger dummy/prepopulated SQLite values on entry for smooth prototyping
    LaunchedEffect(Unit) {
        marketViewModel.seedSampleGoodsIfEmpty()
        socialViewModel.seedSampleFriendsIfEmpty()

        // Fast Async version update check
        val updateInfo = updateRepo.checkUpdate(currentVersionCode = com.example.BuildConfig.VERSION_CODE)
        if (updateInfo != null) {
            showAppUpdateDialog = updateInfo
        }
    }

    // Dynamic Achievements Engine Calculation
    val achievementsList = remember(timeRecords, filteredGoods, friendsList) {
        val hasTime = timeRecords.isNotEmpty()
        val readingsCount = timeRecords.count { it.category == "阅读" || it.title.contains("看书") }
        val listingsCount = filteredGoods.size
        val friendsCount = friendsList.count { it.status == "friend" }
        val hoursSum = timeRecords.sumOf { it.durationMinutes } / 60.0

        listOf(
            Achievement(
                id = "ach_pioneer",
                name = "量化先锋",
                description = "迈出柳比歇夫时间统计第一步，成功记录一次活动",
                iconName = "Timer",
                isUnlocked = hasTime,
                unlockedAt = if (hasTime) System.currentTimeMillis() else null,
                criteriaDescription = "已记录第一条时间"
            ),
            Achievement(
                id = "ach_reading",
                name = "书香门第",
                description = "深入阅读，激发灵感，记录2次或以上的阅读/看书类时间",
                iconName = "Book",
                isUnlocked = readingsCount >= 2,
                unlockedAt = if (readingsCount >= 2) System.currentTimeMillis() else null,
                criteriaDescription = "记录2次阅读 (当前: $readingsCount)"
            ),
            Achievement(
                id = "ach_market",
                name = "初露锋芒",
                description = "绿色循环环保校园！在二手市场发布你第一件教材或闲置商品",
                iconName = "Sell",
                isUnlocked = listingsCount > 3, // Since sample has 3, >3 implies they created one
                unlockedAt = if (listingsCount > 3) System.currentTimeMillis() else null,
                criteriaDescription = "在二手市场新增1件商品"
            ),
            Achievement(
                id = "ach_friendship",
                name = "高山流水",
                description = "结识了志同道合的好友，形成校园学术时间社交伙伴",
                iconName = "People",
                isUnlocked = friendsCount >= 2,
                unlockedAt = if (friendsCount >= 2) System.currentTimeMillis() else null,
                criteriaDescription = "好友栏拥有超过2个知己"
            ),
            Achievement(
                id = "ach_master",
                name = "自律宗师",
                description = "时间量化累积了惊人的100个小时，卓越管理，走向巅峰！",
                iconName = "Star",
                isUnlocked = hoursSum >= 100.0,
                unlockedAt = if (hoursSum >= 100.0) System.currentTimeMillis() else null,
                criteriaDescription = "总自习时间达100小时 (当前: %.1f)".format(hoursSum)
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentScreen != "splash" && currentScreen != "auth" && currentUser != null) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                "dashboard" -> "CampusAI 首页"
                                "time" -> "柳比歇夫时间统计"
                                "ai" -> "AI 效率分析"
                                "market" -> "校园二手交易"
                                "social" -> "校园社交互动"
                                "wall" -> "主页留言板"
                                "settings" -> "设置中心"
                                "achievements" -> "成就系统"
                                "admin" -> "学校管理员后台"
                                "messages" -> "消息中心"
                                else -> "CampusAI"
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        if (currentScreen != "dashboard") {
                            IconButton(
                                onClick = { currentScreen = "dashboard" },
                                modifier = Modifier.testTag("back_to_dash")
                            ) {
                                Icon(Icons.Filled.ArrowBack, "返回主页")
                            }
                        }
                    },
                    actions = {
                        // Admin Panel Toggle Shortcut (Only shown if isUserAdmin)
                        if (isUserAdmin) {
                            IconButton(
                                onClick = { currentScreen = "admin" },
                                modifier = Modifier.testTag("admin_panel_button")
                            ) {
                                Icon(Icons.Filled.AdminPanelSettings, "管理员后台")
                            }
                        }
                        
                        // Message Center Shortcut
                        IconButton(
                            onClick = { currentScreen = "messages" },
                            modifier = Modifier.testTag("conversations_button")
                        ) {
                            Icon(Icons.Filled.Forum, "聊天消息")
                        }

                        IconButton(
                            onClick = { currentScreen = "achievements" },
                            modifier = Modifier.testTag("achievements_button")
                        ) {
                            Icon(Icons.Outlined.MilitaryTech, "成就列表")
                        }
                        IconButton(
                            onClick = { currentScreen = "settings" },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(Icons.Default.Settings, "设置")
                        }
                        
                        // Dynamic Selected Avatar (renders selected 3D avatar)
                        AsyncImage(
                            model = currentUser?.avatar ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=150",
                            contentDescription = "avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(start = 4.dp, end = 12.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen != "splash" && currentScreen != "auth" && currentUser != null) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentScreen == "dashboard" || currentScreen == "settings" || currentScreen == "achievements",
                        onClick = { currentScreen = "dashboard" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("首页") },
                        modifier = Modifier.testTag("nav_dash")
                    )
                    NavigationBarItem(
                        selected = currentScreen == "time",
                        onClick = { currentScreen = "time" },
                        icon = { Icon(Icons.Default.Timer, contentDescription = "Time Tracking") },
                        label = { Text("时间统计") },
                        modifier = Modifier.testTag("nav_time")
                    )
                    NavigationBarItem(
                        selected = currentScreen == "ai",
                        onClick = { currentScreen = "ai" },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Report") },
                        label = { Text("AI报告") },
                        modifier = Modifier.testTag("nav_ai")
                    )
                    NavigationBarItem(
                        selected = currentScreen == "market",
                        onClick = { currentScreen = "market" },
                        icon = { Icon(Icons.Default.Storefront, contentDescription = "Market") },
                        label = { Text("二手市场") },
                        modifier = Modifier.testTag("nav_market")
                    )
                    NavigationBarItem(
                        selected = currentScreen == "social" || currentScreen == "wall",
                        onClick = { currentScreen = "social" },
                        icon = { Icon(Icons.Filled.DynamicFeed, contentDescription = "Social") },
                        label = { Text("校园社交") },
                        modifier = Modifier.testTag("nav_social")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentUser == null && currentScreen != "splash") {
                com.example.features.v2.AuthScreen(onAuthSuccess = {
                    currentScreen = "dashboard"
                })
            } else {
                when (currentScreen) {
                    "splash" -> SplashScreen(onStarted = {
                        currentScreen = if (com.example.core.network.SupabaseManager.currentUser.value == null) "auth" else "dashboard"
                    })
                    "auth" -> com.example.features.v2.AuthScreen(onAuthSuccess = { currentScreen = "dashboard" })
                    "dashboard" -> DashboardScreen(
                        timeViewModel = timeViewModel,
                        timeRecords = timeRecords,
                        achievements = achievementsList,
                        goodsCount = filteredGoods.size,
                        onNavigate = { screen -> currentScreen = screen }
                    )
                    "time" -> TimeTrackingScreen(
                        timeViewModel = timeViewModel,
                        timeRecords = timeRecords
                    )
                    "ai" -> AiAnalysisScreen(
                        aiViewModel = aiViewModel,
                        timeRecords = timeRecords
                    )
                    "market" -> com.example.features.v2.RealMarketScreen(
                        onNavigateToChat = { partnerId ->
                            initialChatPartnerId = partnerId
                            currentScreen = "messages"
                        }
                    )
                    "social" -> com.example.features.v2.RealSocialFeedScreen(
                        onNavigateToChat = { partnerId ->
                            initialChatPartnerId = partnerId
                            currentScreen = "messages"
                        }
                    )
                    "messages" -> com.example.features.v2.MessageCenterScreen(
                        initialPartnerId = initialChatPartnerId,
                        onBack = {
                            initialChatPartnerId = null
                            currentScreen = "dashboard"
                        }
                    )
                    "achievements" -> AchievementsScreen(
                        achievements = achievementsList
                    )
                    "admin" -> com.example.features.v2.AdminConsoleScreen(
                        onBack = { currentScreen = "dashboard" }
                    )
                    "settings" -> SettingsScreen()
                }
            }
        }
    }

    // Modern OkHttp updater notification dialog (supports Force/Gray upgrade simulation)
    showAppUpdateDialog?.let { update ->
        Dialog(onDismissRequest = { if (!update.isForceUpdate) { showAppUpdateDialog = null } }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Upgrade,
                            contentDescription = "有更新可用",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "自动更新版本 ${update.versionName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = update.updateLog,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (!update.isForceUpdate) {
                            TextButton(
                                onClick = { showAppUpdateDialog = null },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("稍后再说", color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Button(
                            onClick = {
                                Toast.makeText(context, "开始下载新版本 APK...", Toast.LENGTH_LONG).show()
                                showAppUpdateDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("立即安装最新版", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. Splash Welcome Screen Composable
// ==========================================

@Composable
fun SplashScreen(onStarted: () -> Unit) {
    var step by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width } + fadeOut()
                )
            },
            label = "splashSlide"
        ) { activeStep ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                // Large styled Hero vectors
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (activeStep) {
                        1 -> Icon(
                            Icons.Default.HourglassEmpty,
                            contentDescription = "Time",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        )
                        2 -> Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(80.dp)
                        )
                        3 -> Icon(
                            Icons.Default.Storefront,
                            contentDescription = "Market",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = when (activeStep) {
                        1 -> "量化时间：柳比歇夫法"
                        2 -> "智能分析：DeepSeek 效率动力"
                        else -> "绿色循环：校园闲置交易"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when (activeStep) {
                        1 -> "科学分类并量化记录您的自习、阅读、锻炼情况，形成规律的每日学术时间清单。"
                        2 -> "依托 AI 引擎快速总结，提炼学习效率高低峰，深度改善拖延状况，让每一次坚持都有迹可循。"
                        else -> "足不出户一键淘书、购买闲置数码产品，支持校内面交、安全私密，校园低碳环保新风尚。"
                    },
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Navigation Stepper Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pills Stepper Indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 1..3) {
                    Box(
                        modifier = Modifier
                            .size(width = if (step == i) 24.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (step == i) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Button(
                onClick = {
                    if (step < 3) {
                        step++
                    } else {
                        onStarted()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .width(110.dp)
                    .height(48.dp)
                    .testTag("splash_next"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (step == 3) "立即体验" else "踏上旅程",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ==========================================
// 2. Dashboard Home Screen Composable
// ==========================================

@Composable
fun LegacyDashboardScreen(
    timeViewModel: TimeViewModel,
    timeRecords: List<TimeRecord>,
    achievements: List<Achievement>,
    goodsCount: Int,
    onNavigate: (String) -> Unit
) {
    val todayStudy = timeViewModel.getStatsToday(timeRecords)
    val weekStudy = timeViewModel.getStatsThisWeek(timeRecords)
    val streakDays = timeViewModel.getStreakDays(timeRecords)

    val todayHours = todayStudy / 60
    val todayMins = todayStudy % 60
    val progressFraction = (todayStudy / 480f).coerceIn(0f, 1f) // Target: 8 hours (480 mins)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header & Streak Status -> Polished "Primary Focus Card" (Today's Study)
        item {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "TODAY'S STUDY / 今日学时量化",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = "Today's Time Studies",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "%02d".format(todayHours),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "h",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp, end = 8.dp)
                        )
                        Text(
                            text = "%02d".format(todayMins),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "m",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress slider line (height 6.dp, h-1.5)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (todayStudy > 0) "+12% 比昨日提升，自我效能动力攀升中！" else "开启今天的第一条时间卡，记录深度思考的高光时刻！",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Stats Grid -> Polished Row with Box 1 (Blue layout) & Box 2 (White layout)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Blue Card (Weekly Streak)
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "WEEKLY STREAK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${streakDays} Days",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(modifier = Modifier.width(4.dp).height(12.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                            Box(modifier = Modifier.width(4.dp).height(24.dp).clip(CircleShape).background(Color.White))
                            Box(modifier = Modifier.width(4.dp).height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                            Box(modifier = Modifier.width(4.dp).height(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
                            Box(modifier = Modifier.width(4.dp).height(14.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                        }
                    }
                }

                // White Card (Marketplace list display)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate("market") },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MARKETPLACE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${goodsCount}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "件新架商品",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Storefront,
                                contentDescription = "Market",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Gradient AI Insight Banner (previously Swipeable AI Card Suggestion)
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseOnSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("ai") },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F172A), // slate-900
                                    Color(0xFF1E293B)  // slate-800
                                )
                            )
                        )
                ) {
                    // Deco background glowing circle
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 16.dp, y = (-16).dp)
                            .size(100.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                    )

                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "AI Insight",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Insight / 智能分析洞察",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (timeRecords.isEmpty()) "您最近还没有添加时间统计明细。添加学习、看书、做开发日志，AI 即可为您定制高效率日报建议！"
                                else "根据您这周的柳比歇夫日志，您的深度学习黄金时段主要在上午。推荐在这个高效时间处理最难的课题功课！",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1), // slate-300
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // Achievements highlight banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的核心成就",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = { onNavigate("achievements") },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("查看全部", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Staggered horizontal locked achievements badge tray
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(achievements) { ach ->
                    Card(
                        modifier = Modifier
                            .width(140.dp)
                            .clickable { onNavigate("achievements") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ach.isUnlocked) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (ach.isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (ach.iconName) {
                                        "Timer" -> Icons.Default.Timer
                                        "Book" -> Icons.Default.MenuBook
                                        "Sell" -> Icons.Default.Sell
                                        "People" -> Icons.Default.People
                                        else -> Icons.Default.Star
                                    },
                                    contentDescription = ach.name,
                                    tint = if (ach.isUnlocked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = ach.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (ach.isUnlocked) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )

                            Text(
                                text = if (ach.isUnlocked) "已解锁 ✨" else "未达成",
                                fontSize = 10.sp,
                                color = if (ach.isUnlocked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        // Recent Activity history
        item {
            Text(
                text = "最近活动流水",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (timeRecords.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.TrendingDown,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "还没有自律明细，去时间统计页添加一条吧！",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(timeRecords.take(3)) { r ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    when (r.category) {
                                        "学习", "课程" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        "阅读" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                ImageVector.fromCategory(r.category),
                                contentDescription = r.category,
                                tint = when (r.category) {
                                    "学习", "课程" -> MaterialTheme.colorScheme.primary
                                    "阅读" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "时长: ${r.durationMinutes}分钟 | ${r.remark}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = r.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. Time Logging Screen Composable
// ==========================================

@Composable
fun TimeTrackingScreen(
    timeViewModel: TimeViewModel,
    timeRecords: List<TimeRecord>
) {
    var isShowLogDialog by remember { mutableStateOf(false) }

    // Log detail entries fields
    var inputTitle by remember { mutableStateOf("") }
    var inputCategory by remember { mutableStateOf("学习") }
    var inputHours by remember { mutableStateOf("1") }
    var inputMinutes by remember { mutableStateOf("0") }
    var inputRemark by remember { mutableStateOf("") }

    val categories = listOf("学习", "阅读", "运动", "项目开发", "课程", "其他")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                titleRangeDescOfToday(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (timeRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.HourglassDisabled,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "柳比歇夫时间流水空空如也",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "坚持记录今天在课业、项目和锻炼上花费的每分钟，点击右下角按钮添加！",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(timeRecords) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            when (record.category) {
                                                "学习" -> MaterialTheme.colorScheme.primaryContainer
                                                "阅读" -> MaterialTheme.colorScheme.secondaryContainer
                                                "项目开发" -> MaterialTheme.colorScheme.tertiaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        ImageVector.fromCategory(record.category),
                                        contentDescription = record.category,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "时长: ${record.durationMinutes}分钟 | ${record.remark}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { timeViewModel.deleteTimeRecord(record.id) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("delete_time_record")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除记录",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating log adder button
        FloatingActionButton(
            onClick = { isShowLogDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_time_record_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "新增量化流", tint = Color.White)
        }
    }

    if (isShowLogDialog) {
        Dialog(onDismissRequest = { isShowLogDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "记录柳比歇夫时间",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("自习、阅读或项目的标题 (例如: 备考英语六级)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("title_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Categories pills scroll row
                    Text(
                        "活动分类：",
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Start),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val selected = inputCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { inputCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    cat,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextField(
                            value = inputHours,
                            onValueChange = { inputHours = it },
                            label = { Text("小时") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("hours_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        TextField(
                            value = inputMinutes,
                            onValueChange = { inputMinutes = it },
                            label = { Text("分钟") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("minutes_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = inputRemark,
                        onValueChange = { inputRemark = it },
                        label = { Text("补充备注说明 (选填)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remark_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { isShowLogDialog = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("取消", color = MaterialTheme.colorScheme.outline)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (inputTitle.isNotEmpty()) {
                                    val hours = inputHours.toIntOrNull() ?: 0
                                    val minutes = inputMinutes.toIntOrNull() ?: 0
                                    val totalMinutes = (hours * 60) + minutes
                                    if (totalMinutes > 0) {
                                        val now = System.currentTimeMillis()
                                        val start = now - (totalMinutes * 60 * 1000L)
                                        timeViewModel.addTimeRecord(
                                            title = inputTitle,
                                            category = inputCategory,
                                            startTime = start,
                                            endTime = now,
                                            remark = inputRemark
                                        )
                                        isShowLogDialog = false
                                        inputTitle = ""
                                        inputRemark = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("保存记录", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. AI Report Screen Composable
// ==========================================

@Composable
fun LegacyAiAnalysisScreen(
    aiViewModel: AiViewModel,
    timeRecords: List<TimeRecord>
) {
    val reportState by aiViewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI Head",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "DeepSeek & Gemini 智能顾问",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "对你的柳比歇夫时间日志进行挖掘，推荐自律成长和学习建议！",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Trigger action report launchers buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { aiViewModel.generateDailyReport(timeRecords) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_daily_report")
                        .minimumInteractiveComponentSize(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("AI 日报", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = { aiViewModel.generateWeeklyReport(timeRecords) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_weekly_report")
                        .minimumInteractiveComponentSize(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("AI 周报", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = { aiViewModel.generateMonthlyReport(timeRecords) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_monthly_report")
                        .minimumInteractiveComponentSize(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("AI 月报", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Detailed loading state and markdown output
        item {
            AnimatedContent(targetState = reportState, label = "report_anim") { state ->
                when (state) {
                    is AiReportUiState.Idle -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Insights,
                                    contentDescription = "Pending",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "等待生成分析报表...",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "点击上方按钮，AI 即可根据您记录的 ${timeRecords.size} 条历史量化时间，为您快速提炼生成精美报表！",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    is AiReportUiState.Loading -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "AI 正在极速总结分析中...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "模型：gemini-3.5-flash",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    is AiReportUiState.Success -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "智能报告生成结果：",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { aiViewModel.clearReport() },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Clear, "清除", modifier = Modifier.size(16.dp))
                                    }
                                }

                                Divider(modifier = Modifier.padding(vertical = 12.dp))

                                Text(
                                    text = state.report,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    is AiReportUiState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "生成失败",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    state.message,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. Second-hand Market Screen Composable
// ==========================================

@Composable
fun SecondHandMarketScreen(
    marketViewModel: MarketViewModel,
    filteredGoods: List<Goods>
) {
    val searchQuery by marketViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by marketViewModel.selectedTabFilter.collectAsStateWithLifecycle()

    var showPublishDialog by remember { mutableStateOf(false) }

    // Publish item inputs
    var inputGoodsTitle by remember { mutableStateOf("") }
    var inputGoodsDesc by remember { mutableStateOf("") }
    var inputGoodsPrice by remember { mutableStateOf("") }
    var inputGoodsImage by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search Bar Input
            TextField(
                value = searchQuery,
                onValueChange = { marketViewModel.setSearchQuery(it) },
                leadingIcon = { Icon(Icons.Default.Search, "搜索") },
                placeholder = { Text("搜索教材、数码或生活杂货...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("goods_search_input"),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filtering Tab selection (All VS Bookmarked)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = { marketViewModel.setFilterTab("all") },
                    label = { Text("全部在售宝贝") },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
                FilterChip(
                    selected = selectedFilter == "favorite",
                    onClick = { marketViewModel.setFilterTab("favorite") },
                    label = { Text("我最喜欢的商品") },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredGoods.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = "Empty Shelf",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "没有找到售卖的商品",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredGoods) { goods ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                // Image Loader Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(goods.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = goods.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Bookmark/Favorite Button overlay
                                    IconButton(
                                        onClick = { marketViewModel.toggleFavorite(goods) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(32.dp)
                                            .background(Color.White.copy(alpha = 0.82f), CircleShape)
                                            .testTag("fav_button_${goods.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (goods.isFavorite) Icons.Filled.Favorite
                                            else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (goods.isFavorite) Color.Red else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = goods.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = goods.description,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "¥${goods.price}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = goods.sellerName,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // New Item publication triggers FAB
        FloatingActionButton(
            onClick = { showPublishDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("publish_goods_fab")
        ) {
            Icon(Icons.Default.Publish, contentDescription = "发布商品", tint = Color.White)
        }
    }

    if (showPublishDialog) {
        Dialog(onDismissRequest = { showPublishDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "发布商品在售",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = inputGoodsTitle,
                        onValueChange = { inputGoodsTitle = it },
                        label = { Text("商品标题") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goods_title_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = inputGoodsDesc,
                        onValueChange = { inputGoodsDesc = it },
                        label = { Text("详细描述 (成色、购买出处、交接自习室)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goods_desc_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = inputGoodsPrice,
                        onValueChange = { inputGoodsPrice = it },
                        label = { Text("一口价 (元)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goods_price_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = inputGoodsImage,
                        onValueChange = { inputGoodsImage = it },
                        label = { Text("示例图片 URL (选填)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goods_image_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showPublishDialog = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("取消", color = MaterialTheme.colorScheme.outline)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (inputGoodsTitle.isNotEmpty() && inputGoodsPrice.isNotEmpty()) {
                                    val priceVal = inputGoodsPrice.toDoubleOrNull() ?: 0.0
                                    marketViewModel.publishItem(
                                        title = inputGoodsTitle,
                                        description = inputGoodsDesc,
                                        price = priceVal,
                                        imageUrl = inputGoodsImage,
                                        sellerName = "我"
                                    )
                                    showPublishDialog = false
                                    inputGoodsTitle = ""
                                    inputGoodsDesc = ""
                                    inputGoodsPrice = ""
                                    inputGoodsImage = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("上架商品", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. Social Screen Composable
// ==========================================

@Composable
fun SocialScreen(
    socialViewModel: SocialViewModel,
    friends: List<Friend>,
    onNavigateToWall: () -> Unit
) {
    val activeChatId by socialViewModel.activeChatFriendId.collectAsStateWithLifecycle()
    val chatMessages by socialViewModel.conversationMessages.collectAsStateWithLifecycle()

    var chatInputText by remember { mutableStateOf("") }
    var showAddFriendDialog by remember { mutableStateOf(false) }

    // Add friend fields
    var inputFriendId by remember { mutableStateOf("") }
    var inputFriendName by remember { mutableStateOf("") }
    var inputFriendBio by remember { mutableStateOf("") }

    if (activeChatId != null) {
        val activeFriend = friends.firstOrNull { it.id == activeChatId }
        // Render Active Chat Window Dialog style
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { socialViewModel.selectConversation(null) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.ArrowBack, "关闭")
                }

                Spacer(modifier = Modifier.width(8.dp))

                AsyncImage(
                    model = activeFriend?.avatarUrl,
                    contentDescription = activeFriend?.nickname,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        activeFriend?.nickname ?: "聊天伙伴",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text("自习室伙伴 · 线上联系中", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Chat lists logger
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatMessages) { msg ->
                    val isMyMessage = msg.senderId == "local_user"
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMyMessage) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMyMessage) 16.dp else 4.dp,
                                bottomEnd = if (isMyMessage) 4.dp else 16.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMyMessage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.widthIn(max = 260.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    msg.content,
                                    fontSize = 13.sp,
                                    color = if (isMyMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Input and send toolbar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = chatInputText,
                    onValueChange = { chatInputText = it },
                    placeholder = { Text("打个招呼，说点什么...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_text_input"),
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(10.dp))

                FloatingActionButton(
                    onClick = {
                        if (chatInputText.isNotEmpty()) {
                            socialViewModel.sendMessage(activeChatId!!, chatInputText)
                            chatInputText = ""
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Send, "发送", tint = Color.White)
                }
            }
        }
    } else {
        // Friends Dashboard lists Composable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "学术自律伙伴",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row {
                    TextButton(
                        onClick = onNavigateToWall,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("主页留言板", color = MaterialTheme.colorScheme.secondary)
                    }

                    IconButton(
                        onClick = { showAddFriendDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.PersonAdd, "添加好友")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (friends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("点击右上角添加您的第一名校园自律盟友！", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Friend items
                    items(friends) { friend ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (friend.status == "friend") {
                                        socialViewModel.selectConversation(friend.id)
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar Unsplash Placeholders
                                AsyncImage(
                                    model = friend.avatarUrl,
                                    contentDescription = friend.nickname,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        friend.nickname,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        friend.bio,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (friend.status == "pending") {
                                    Button(
                                        onClick = { socialViewModel.acceptFriendRequest(friend.id) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("同意申请", fontSize = 10.sp, color = Color.White)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { socialViewModel.removeFriend(friend.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Default.PersonRemove,
                                            contentDescription = "删除好友",
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddFriendDialog) {
        Dialog(onDismissRequest = { showAddFriendDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "搜寻添加好友",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = inputFriendId,
                        onValueChange = { inputFriendId = it },
                        label = { Text("学号/学期编号 (ID)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("friend_id_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = inputFriendName,
                        onValueChange = { inputFriendName = it },
                        label = { Text("姓名/学术花名") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("friend_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = inputFriendBio,
                        onValueChange = { inputFriendBio = it },
                        label = { Text("个性签名/自习目标") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("friend_bio_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showAddFriendDialog = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("取消", color = MaterialTheme.colorScheme.outline)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (inputFriendId.isNotEmpty() && inputFriendName.isNotEmpty()) {
                                    socialViewModel.addFriend(
                                        id = inputFriendId,
                                        nickname = inputFriendName,
                                        avatarUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&q=80&w=150",
                                        bio = inputFriendBio
                                    )
                                    showAddFriendDialog = false
                                    inputFriendId = ""
                                    inputFriendName = ""
                                    inputFriendBio = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("发送请求", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. Wall Screen Composable
// ==========================================

@Composable
fun WallScreen(
    socialViewModel: SocialViewModel,
    wallMessages: List<UserMessage>
) {
    var WallInputText by remember { mutableStateOf("") }
    var inputAuthorName by remember { mutableStateOf("") }

    val approvedComments = remember(wallMessages) {
        wallMessages.filter { it.isApproved }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "校园微论坛留言墙",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Create comment forms box
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = inputAuthorName,
                    onValueChange = { inputAuthorName = it },
                    placeholder = { Text("你的昵称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wall_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = WallInputText,
                    onValueChange = { WallInputText = it },
                    placeholder = { Text("写下你的校园生活、求书或找室友寄语...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wall_content_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (WallInputText.isNotEmpty()) {
                            socialViewModel.postMessageToWall(
                                content = WallInputText,
                                authorName = inputAuthorName.ifEmpty { "热心同学" }
                            )
                            WallInputText = ""
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("btn_wall_publish")
                        .minimumInteractiveComponentSize(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("发布便签墙", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (approvedComments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("留言被管理员过滤或为空，写第一条吧！", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(approvedComments) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = msg.authorAvatar,
                                    contentDescription = msg.authorName,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        msg.authorName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                    Text(dateStr, fontSize = 9.sp, color = Color.Gray)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                msg.content,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. Admin Moderation Screen Composable
// ==========================================

@Composable
fun AdminPanelScreen(
    socialViewModel: SocialViewModel,
    wallMessages: List<UserMessage>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "学校绿色安全内容管理后台",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "管理员权限：即时过滤有害、谩骂留言板标签，保护网络健康",
            fontSize = 11.sp,
            color = Color.Red
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (wallMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("当前无任何公开留言可供审核", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(wallMessages) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "发布者: ${msg.authorName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = if (msg.isApproved) "已批准审核" else "违规已被封禁",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (msg.isApproved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                "内容: ${msg.content}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { socialViewModel.deleteWallMessage(msg.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("清退彻底删除", color = MaterialTheme.colorScheme.error)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { socialViewModel.setWallMessageApproval(msg.id, !msg.isApproved) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (msg.isApproved) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(
                                        text = if (msg.isApproved) "惩罚性隐藏" else "重新公开上架",
                                        fontSize = 10.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. Achievements Board Screen Composable
// ==========================================

@Composable
fun AchievementsScreen(
    achievements: List<Achievement>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    val unlockedCount = achievements.count { it.isUnlocked }
                    Text(
                        text = "成长荣誉殿堂 ✨",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "校内记录达成进度: $unlockedCount / ${achievements.size}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { unlockedCount.toFloat() / achievements.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
            }
        }

        items(achievements) { ach ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (ach.isUnlocked) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (ach.isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (ach.iconName) {
                                "Timer" -> Icons.Default.Timer
                                "Book" -> Icons.Default.MenuBook
                                "Sell" -> Icons.Default.Sell
                                "People" -> Icons.Default.People
                                else -> Icons.Default.Star
                            },
                            contentDescription = ach.name,
                            tint = if (ach.isUnlocked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ach.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (ach.isUnlocked) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = ach.description,
                            fontSize = 11.sp,
                            color = if (ach.isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "达成条件：${ach.criteriaDescription}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ach.isUnlocked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }

                    if (ach.isUnlocked) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已达成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. Configurations & Settings Screen
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentUser by com.example.core.network.SupabaseManager.currentUser.collectAsStateWithLifecycle()

    var config by remember { mutableStateOf(com.example.core.network.DynamicAiClient.loadConfig(context)) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var model by remember { mutableStateOf(config.model) }
    var temperature by remember { mutableStateOf(config.temperature) }
    var maxTokens by remember { mutableStateOf(config.maxTokens) }
    var selectedProvider by remember { mutableStateOf(config.provider) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var isSavingConfig by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val user = currentUser
                if (user == null) {
                    Toast.makeText(context, "请先登录后再修改头像！", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                isUploadingAvatar = true
                Toast.makeText(context, "正在读取并保存临时文件...", Toast.LENGTH_SHORT).show()
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        Toast.makeText(context, "读取图片失败，请重试！", Toast.LENGTH_SHORT).show()
                        isUploadingAvatar = false
                        return@launch
                    }
                    val tempFile = java.io.File(context.cacheDir, "temp_avatar_${System.currentTimeMillis()}.jpg")
                    tempFile.outputStream().use { outStream ->
                        inputStream.copyTo(outStream)
                    }

                    Toast.makeText(context, "正在连接云存储并上传头像...", Toast.LENGTH_SHORT).show()
                    val result = com.example.core.network.SupabaseClient.uploadAvatar(tempFile.absolutePath, user.id)
                    if (result.isSuccess) {
                        val publicUrl = result.getOrThrow()
                        Toast.makeText(context, "头像上传成功！正在更新配置...", Toast.LENGTH_SHORT).show()
                        com.example.core.network.SupabaseManager.updateUserAvatar(context, publicUrl)
                        Toast.makeText(context, "更新成功并已同步！", Toast.LENGTH_SHORT).show()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "未知错误"
                        Toast.makeText(context, "头像上传失败: $error", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "出错: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Logged in Profile Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = currentUser?.avatar ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=150",
                        contentDescription = "avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable {
                                if (currentUser != null && !isUploadingAvatar) {
                                    imagePickerLauncher.launch("image/*")
                                }
                            }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isUploadingAvatar) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        TextButton(
                            onClick = {
                                if (currentUser != null) {
                                    imagePickerLauncher.launch("image/*")
                                } else {
                                    Toast.makeText(context, "请登录后使用头像上传功能", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("upload_avatar_btn")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "上传头像", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("上传头像 (M3 UI)", fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = currentUser?.nickname ?: "自学自律伙伴",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${currentUser?.school} · ${currentUser?.college} · ${currentUser?.grade}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (currentUser?.bio?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "“${currentUser?.bio}”",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            com.example.core.network.SupabaseManager.logout(context)
                            Toast.makeText(context, "已成功安全撤离空间！", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(100),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth().testTag("logout_btn")
                    ) {
                        Icon(Icons.Filled.Logout, "logout", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("安全退出登录/注销", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 2. AI Configuration Center Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🤖 AI 生产力模型配置中心", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text("安全要求：您的 API 凭证均采用 Android Keystore 本地加密技术存储，绝不下传任何服务器！", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)

                    // Provider Dropdowns Selection (Segmented-like pills selector)
                    Text("选择 AI 服务商 :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val providers = listOf("DeepSeek", "OpenAI", "SiliconFlow")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        providers.forEach { pr ->
                            val isSel = selectedProvider == pr
                            Button(
                                onClick = {
                                    selectedProvider = pr
                                    baseUrl = when (pr) {
                                        "DeepSeek" -> "https://api.deepseek.com"
                                        "OpenAI" -> "https://api.openai.com"
                                        "SiliconFlow" -> "https://api.siliconflow.cn"
                                        else -> baseUrl
                                    }
                                    model = when (pr) {
                                        "DeepSeek" -> "deepseek-chat"
                                        "OpenAI" -> "gpt-4o-mini"
                                        "SiliconFlow" -> "deepseek-ai/DeepSeek-V3"
                                        else -> model
                                    }
                                },
                                shape = RoundedCornerShape(100),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text(pr, fontSize = 9.sp, color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // API Key input field (masked string security)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text("API Key (以 sk- 开头)", color = Color.Gray, fontSize = 13.sp) },
                        label = { Text("授权秘钥 API Key") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ai_apiKey_input")
                    )

                    // Base URL input field
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        placeholder = { Text("接口端点 Base URL", color = Color.Gray, fontSize = 13.sp) },
                        label = { Text("Base URL 端点") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Model Name input field
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = { Text("模型名称 Model Name", color = Color.Gray, fontSize = 13.sp) },
                        label = { Text("模型 Model (chat 如 deepseek-chat)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Temperature slider
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("多样温度 Temperature:", fontSize = 12.sp)
                            Text("${String.format("%.2f", temperature)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..1.5f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Max Tokens field
                    OutlinedTextField(
                        value = "$maxTokens",
                        onValueChange = { maxTokens = it.toIntOrNull() ?: 2048 },
                        label = { Text("最大限制令牌数 Max Tokens") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // SAVE Button
                        Button(
                            onClick = {
                                isSavingConfig = true
                                val newCfg = com.example.core.network.AiConfig(
                                    provider = selectedProvider,
                                    apiKey = apiKey,
                                    baseUrl = baseUrl,
                                    model = model,
                                    temperature = temperature,
                                    maxTokens = maxTokens
                                )
                                com.example.core.network.DynamicAiClient.saveConfig(context, newCfg)
                                config = newCfg
                                isSavingConfig = false
                                Toast.makeText(context, "AI 服务端点配置已成功保密写入！", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(100),
                            modifier = Modifier.weight(1f).testTag("save_ai_config_btn")
                        ) {
                            Text("本地安全保存", fontSize = 12.sp)
                        }

                        // TEST Button
                        Button(
                            onClick = {
                                isTestingConnection = true
                                val testCfg = com.example.core.network.AiConfig(
                                    provider = selectedProvider,
                                    apiKey = apiKey,
                                    baseUrl = baseUrl,
                                    model = model,
                                    temperature = temperature,
                                    maxTokens = maxTokens
                                )
                                scope.launch {
                                    val result = com.example.core.network.DynamicAiClient.testConnection(context, testCfg)
                                    isTestingConnection = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "🎉 ${result.getOrNull()}", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "❌ 连接失败：\n${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(100),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f).testTag("test_ai_conn_btn")
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text("点击测试连接", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. Cloud Synchronization Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Supabase 云同步状态", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (com.example.core.network.SupabaseClient.isConfigured()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color.Green, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("外部 Supabase 线上数据库环路：已就绪接轨", fontSize = 12.sp, color = Color.Green)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color.Yellow, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("本地高保真离线多端持久储存 (未加载 API 云凭证)", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Extencials Helpers & ImageVector Converters
// ==========================================

fun ImageVector.Companion.fromCategory(cat: String): ImageVector {
    return when (cat) {
        "学习" -> Icons.Default.School
        "阅读" -> Icons.Default.MenuBook
        "运动" -> Icons.Default.FitnessCenter
        "项目开发" -> Icons.Default.Code
        "课程" -> Icons.Default.Class
        else -> Icons.Default.Timer
    }
}

fun titleRangeDescOfToday(): String {
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
    return "今天是 ${sdf.format(cal.time)} 时光清单"
}
