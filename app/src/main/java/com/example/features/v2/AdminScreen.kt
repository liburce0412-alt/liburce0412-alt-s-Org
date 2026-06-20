package com.example.features.v2

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import coil.compose.AsyncImage
import com.example.core.model.*
import com.example.core.network.SupabaseManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("stats") } // "stats", "users", "community", "reports", "announcements"

    val userRole = SupabaseManager.currentUser.value?.userRole() ?: UserRole.USER
    if (userRole != UserRole.ADMIN && userRole != UserRole.SUPER_ADMIN) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Security, "secure", modifier = Modifier.size(64.dp), tint = Color.Red)
                Spacer(modifier = Modifier.height(12.dp))
                Text("此项功能仅限学校管理委员会开启！", fontWeight = FontWeight.Bold, color = Color.Red)
                Text("当前账号权限级别不足，请使用管理员邮箱登录。", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) { Text("安全退出") }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School Admin 控制中心", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Elegant scrolling custom Capsule tabs row
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        "stats" to Icons.Default.BarChart,
                        "users" to Icons.Default.People,
                        "community" to Icons.Default.FactCheck,
                        "reports" to Icons.Default.Report,
                        "announcements" to Icons.Default.Announcement
                    )
                    tabs.forEach { (tb, icon) ->
                        val isSelected = selectedTab == tb
                        IconButton(
                            onClick = { selectedTab = tb },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .testTag("admin_tab_$tb")
                        ) {
                            Icon(icon, tb, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                "stats" -> AdminStatsView()
                "users" -> AdminUsersView()
                "community" -> AdminCommunityView()
                "reports" -> AdminReportsView()
                "announcements" -> AdminAnnouncementsView()
            }
        }
    }
}

// ==========================================
// 1. DATA STATISTICS PANEL
// ==========================================
@Composable
fun AdminStatsView() {
    val users by SupabaseManager.allUsers.collectAsStateWithLifecycle()
    val posts by SupabaseManager.posts.collectAsStateWithLifecycle()
    val products by SupabaseManager.products.collectAsStateWithLifecycle()
    val orders by SupabaseManager.orders.collectAsStateWithLifecycle()

    val totalTradeVal = remember(orders) { orders.sumOf { it.price } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("全校区时间社交运行大盘", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("实时核心生产力数据", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell(label = "注册人数", count = "${users.size}人", subtext = "今日 +2人")
                        StatCell(label = "自律 DAU", count = "18人", subtext = "活跃占比 85%")
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell(label = "帖子数量", count = "${posts.size}篇", subtext = "自学经验占比高")
                        StatCell(label = "闲置数量", count = "${products.size}件", subtext = "文具教材流动多")
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell(label = "完成交易 orders", count = "${orders.size}笔", subtext = "待交易 ${(products.filter { it.status == "pending" }).size}件")
                        StatCell(label = "交易交易额 GMV", count = "￥${String.format("%.1f", totalTradeVal)}", subtext = "学霸循环经济")
                    }
                }
            }
        }
    }
}

@Composable
fun StatCell(label: String, count: String, subtext: String) {
    Column {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text(subtext, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
    }
}

// ==========================================
// 2. USER SECURITY DIRECTORY
// ==========================================
@Composable
fun AdminUsersView() {
    val users by SupabaseManager.allUsers.collectAsStateWithLifecycle()
    var searchKey by remember { mutableStateOf("") }
    
    val filtered = remember(users, searchKey) {
        users.filter { it.nickname.contains(searchKey, ignoreCase = true) || it.email.contains(searchKey, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchKey,
            onValueChange = { searchKey = it },
            placeholder = { Text("搜索用户昵称/邮箱...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, "search") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(filtered) { usr ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = usr.avatar,
                            contentDescription = "av",
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(usr.nickname, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${usr.school} · ${usr.college} · ${usr.role}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(usr.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        if (usr.id != SupabaseManager.currentUser.value?.id) {
                            Button(
                                onClick = { SupabaseManager.adminSetUserBlockStatus(usr.id, !usr.isBlocked) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (usr.isBlocked) Color.Gray else Color.Red
                                )
                            ) {
                                Text(if (usr.isBlocked) "解除封禁" else "封禁账号", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. COMMUNITY AUDIT MODERATOR PANEL
// ==========================================
@Composable
fun AdminCommunityView() {
    val posts by SupabaseManager.posts.collectAsStateWithLifecycle()
    val products by SupabaseManager.products.collectAsStateWithLifecycle()
    var subTab by remember { mutableStateOf("posts") } // "posts", "products"

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { subTab = "posts" },
                colors = ButtonDefaults.buttonColors(containerColor = if (subTab == "posts") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.weight(1f)
            ) {
                Text("社区发帖审核 (${posts.size})", color = if (subTab == "posts") Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { subTab = "products" },
                colors = ButtonDefaults.buttonColors(containerColor = if (subTab == "products") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.weight(1f)
            ) {
                Text("商场商品违规 (${products.size})", color = if (subTab == "products") Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (subTab == "posts") {
                items(posts) { pst ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = pst.authorAvatar, contentDescription = "av", modifier = Modifier.size(32.dp).clip(CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(pst.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(pst.topic, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(pst.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { SupabaseManager.adminDeletePost(pst.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("一键删除", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                items(products) { prod ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                            AsyncImage(model = prod.imageUrl, contentDescription = "p", contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("价格: ￥${prod.price}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("状态: ${prod.status} / 卖家: ${prod.sellerName}", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { SupabaseManager.adminTakeDownProduct(prod.id) },
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) {
                                        Text("违规下架", fontSize = 11.sp, color = Color.White)
                                    }
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
// 4. REPORTS RESOLUTION VIEW
// ==========================================
@Composable
fun AdminReportsView() {
    val reports by SupabaseManager.reports.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (reports.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, "clean", modifier = Modifier.size(64.dp), tint = Color.Green)
                Spacer(modifier = Modifier.height(12.dp))
                Text("全社区一片净土！尚未收到任何违规举报。", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("安全合规待审计清单", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        items(reports) { rep ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (rep.status == "pending") borderStrokeGlow() else null
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(containerColor = when (rep.reason) {
                            "诈骗", "违法内容" -> Color.Red
                            else -> MaterialTheme.colorScheme.primary
                        }) {
                            Text(rep.reason, fontSize = 10.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("举报类型: ${rep.reportedType}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(if (rep.status == "pending") "等待处置 🔔" else "已结束审核", fontSize = 11.sp, color = if (rep.status == "pending") MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                    
                    Text("举报详情: ${rep.details.ifEmpty { "不符合校园文明守纪标准" }}", fontSize = 13.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
                        Text("内容快照:\n${rep.reportedContentDigest}", fontSize = 11.sp, modifier = Modifier.padding(8.dp).fillMaxWidth())
                    }

                    if (rep.status == "pending") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    // Cascade Delete depending on target type
                                    if (rep.reportedType == "post") {
                                        SupabaseManager.adminDeletePost(rep.reportedId.toIntOrNull() ?: 0)
                                    } else if (rep.reportedType == "product") {
                                        SupabaseManager.adminTakeDownProduct(rep.reportedId.toIntOrNull() ?: 0)
                                    } else if (rep.reportedType == "user") {
                                        SupabaseManager.adminSetUserBlockStatus(rep.reportedId, true)
                                    }
                                    SupabaseManager.adminResolveReport(rep.id, "resolved")
                                    Toast.makeText(context, "违规物项已清退，通告成功！", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("下架违规内容", fontSize = 11.sp, color = Color.White)
                            }
                            OutlinedButton(
                                onClick = {
                                    SupabaseManager.adminResolveReport(rep.id, "ignored")
                                    Toast.makeText(context, "已忽略恶意/误举报申请", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("驳回举报/保留", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Border Stroke decorative glow wrapper
@Composable
fun borderStrokeGlow(): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
}

// ==========================================
// 5. CAMPUS ANNOUNCEMENTS CRUDS
// ==========================================
@Composable
fun AdminAnnouncementsView() {
    val anns by SupabaseManager.announcements.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("发布高亮系统公告栏", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("公告主题 (必须简明醒目)", fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("公告内容细则说明...", fontSize = 13.sp) },
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (title.isEmpty() || content.isEmpty()) {
                                Toast.makeText(context, "公告主题及内文不能为空！", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            SupabaseManager.adminPublishAnnouncement(title, content)
                            title = ""
                            content = ""
                            Toast.makeText(context, "公告已向全体师生公开广播发出！", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("全渠道强弹广播发出")
                    }
                }
            }
        }

        item {
            Text("已发布通告明细", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        }

        items(anns) { an ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(an.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Red)
                        Text(an.content, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("起草: ${an.authorName} · ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(an.createdAt))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { SupabaseManager.adminDeleteAnnouncement(an.id) }) {
                        Icon(Icons.Default.Delete, "delete", tint = Color.Gray)
                    }
                }
            }
        }
    }
}
