package com.example.features.v2

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.model.*
import com.example.core.network.SupabaseManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RealSocialFeedScreen(
    onNavigateToChat: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val postsState by SupabaseManager.posts.collectAsStateWithLifecycle()
    val likesState by SupabaseManager.likes.collectAsStateWithLifecycle()

    var activeSort by remember { mutableStateOf("latest") } // "latest", "hot", "following"
    var showReportDialogForPostId by remember { mutableStateOf<Int?>(null) }
    var activeCommentingPostId by remember { mutableStateOf<Int?>(null) }
    var showCreatePostDialog by remember { mutableStateOf(false) }

    // Dynamic Filter & Sort algorithms
    val sortedPosts = remember(postsState, likesState, activeSort) {
        val approved = postsState.filter { it.isApproved }
        when (activeSort) {
            "hot" -> approved.sortedByDescending { pst ->
                SupabaseManager.getPostLikeCount(pst.id) + SupabaseManager.getPostComments(pst.id).size
            }
            "following" -> approved.filter { pst ->
                pst.tags.contains("自律") || pst.topic.contains("打卡") // Simulate customized topics tracking lists
            }.sortedByDescending { it.createdAt }
            else -> approved.sortedByDescending { it.createdAt }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Sorting row headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sorts = listOf(
                        "latest" to "最新发布",
                        "hot" to "热门高热",
                        "following" to "特别关注"
                    )
                    sorts.forEach { (sr, label) ->
                        val isSelected = activeSort == sr
                        Button(
                            onClick = { activeSort = sr },
                            shape = RoundedCornerShape(100),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("social_sort_$sr")
                        ) {
                            Text(
                                label, 
                                fontSize = 12.sp, 
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Elegant Capsule Floating Action Button (FAB)
            FloatingActionButton(
                onClick = { showCreatePostDialog = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("publish_post_fab")
            ) {
                Icon(Icons.Default.Add, "write")
            }
        }
    ) { paddingValues ->
        if (sortedPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Feed, "empty", modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("暂无符合条件的状态动态", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(sortedPosts, key = { it.id }) { pst ->
                    PostCardItem(
                        post = pst,
                        onLikeClick = { SupabaseManager.likePost(pst.id) },
                        onCommentClick = { activeCommentingPostId = pst.id },
                        onReportClick = { showReportDialogForPostId = pst.id },
                        onChatClick = { onNavigateToChat(pst.authorId) }
                    )
                }
            }
        }
    }

    // Expanding Comments Expanded Modal Sheet Custom Implementation
    activeCommentingPostId?.let { postId ->
        val selectedPost = postsState.find { it.id == postId }
        if (selectedPost != null) {
            CommentsModalOverlay(
                post = selectedPost,
                onDismiss = { activeCommentingPostId = null }
            )
        }
    }

    // Submit Report dialog compliance workflow
    showReportDialogForPostId?.let { postId ->
        val post = postsState.find { it.id == postId }
        if (post != null) {
            ReportSubmissionDialog(
                reportedType = "post",
                reportedId = "$postId",
                reportedContentDigest = "[发帖] ${post.authorName}: ${post.content.take(60)}",
                onDismiss = { showReportDialogForPostId = null }
            )
        }
    }

    // Create New Post dialogue
    if (showCreatePostDialog) {
        CreatePostDialog(onDismiss = { showCreatePostDialog = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCardItem(
    post: SupabasePost,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onReportClick: () -> Unit,
    onChatClick: () -> Unit
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val formattedDate = sdf.format(Date(post.createdAt))
    val isLiked = SupabaseManager.isPostLiked(post.id)
    val likeCount = SupabaseManager.getPostLikeCount(post.id)
    val commentsSize = SupabaseManager.getPostComments(post.id).size

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Author Avatar & info
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.authorAvatar,
                    contentDescription = "avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        text = "$formattedDate · 来自 ${if (post.isAnonymous) "自律树洞" else "云校区"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.weight(1f))

                // Direct chat to seller if not anonymous & not self
                val me = SupabaseManager.currentUser.value
                if (!post.isAnonymous && post.authorId != me?.id) {
                    IconButton(onClick = onChatClick) {
                        Icon(Icons.Default.Forum, "chat", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }

                // Report button
                IconButton(onClick = onReportClick) {
                    Icon(Icons.Outlined.OutlinedFlag, "report", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body content Text
            Text(
                text = post.content,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Body image
            if (post.imageUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "illustration",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tags row (flowing tag layout)
            if (post.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    post.tags.split(",").filter { it.isNotEmpty() }.forEach { tg ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(100))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("# $tg", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            // Action row buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                Row(
                    modifier = Modifier
                        .clickable { onLikeClick() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "like",
                        tint = if (isLiked) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("$likeCount", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }

                // Comment Button
                Row(
                    modifier = Modifier
                        .clickable { onCommentClick() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Comment,
                        contentDescription = "comments",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("$commentsSize", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }

                // Topic Tag label
                if (post.topic.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(post.topic, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Comments expansions Overlay Drawer Dialog
@Composable
fun CommentsModalOverlay(
    post: SupabasePost,
    onDismiss: () -> Unit
) {
    val comments = SupabaseManager.getPostComments(post.id)
    val context = LocalContext.current
    var inputCmtText by remember { mutableStateOf("") }
    
    // Track nesting replies selection
    var selectedParentId by remember { mutableStateOf<Int?>(null) }
    var selectedParentAuthor by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "评论区 (${comments.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "close")
                    }
                }
                
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // List comments
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    items(comments) { cmt ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedParentId = cmt.id
                                    selectedParentAuthor = cmt.authorName
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = cmt.authorAvatar,
                                    contentDescription = "av",
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cmt.authorName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(cmt.createdAt)),
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            // Check nested indentation replies (二级回复)
                            val parentName = if (cmt.parentId != null) {
                                val original = comments.find { it.id == cmt.parentId }
                                original?.authorName ?: "原条目"
                            } else null

                            Row {
                                if (cmt.parentId != null) {
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.LightGray))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Column {
                                    if (parentName != null) {
                                        Text("回复 @$parentName ：", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(cmt.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Add comment input
                Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (selectedParentId != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("回复 @$selectedParentAuthor", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Text("取消回复", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.clickable {
                                    selectedParentId = null
                                    selectedParentAuthor = ""
                                })
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputCmtText,
                                onValueChange = { inputCmtText = it },
                                placeholder = { Text("良言一句三冬暖...", fontSize = 13.sp) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("comment_input_box")
                            )
                            Button(
                                onClick = {
                                    if (inputCmtText.trim().isEmpty()) return@Button
                                    SupabaseManager.addComment(
                                        post.id,
                                        inputCmtText.trim(),
                                        selectedParentId
                                    )
                                    inputCmtText = ""
                                    selectedParentId = null
                                    selectedParentAuthor = ""
                                    Toast.makeText(context, "评论发布成功！", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.Send, "send", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Submission of reports overlay Dialog screen
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportSubmissionDialog(
    reportedType: String,
    reportedId: String,
    reportedContentDigest: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedReason by remember { mutableStateOf("垃圾广告") }
    var details by remember { mutableStateOf("") }

    val reasons = listOf("垃圾广告", "违法内容", "诈骗", "辱骂", "色情", "其他")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提交合规举报审查", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text("我们重视校园文明。请在下方指出违规理由项：", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { rs ->
                        val isSel = selectedReason == rs
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedReason = rs },
                            label = { Text(rs, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    placeholder = { Text("描述更多补充证据情况 (选填)...", fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SupabaseManager.submitReport(
                        reportedType = reportedType,
                        reportedId = reportedId,
                        reason = selectedReason,
                        details = details,
                        contentDigest = reportedContentDigest
                    )
                    Toast.makeText(context, "感谢举报贡献！我们将于 24 小时内完成审核下线处罚。", Toast.LENGTH_LONG).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("确认提交审计", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("放弃")
            }
        }
    )
}

// Create New Post Dialogue
@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }
    var selectedTopic by remember { mutableStateOf("自律打卡") }
    var isAnonymous by remember { mutableStateOf(false) }

    val topics = listOf("自律打卡", "自习疑问", "学术科研", "交友面基", "深度学习圈")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建立新的自律动态", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("记录今天的时间统计心得...", fontSize = 13.sp) },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("new_post_content")
                )

                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    placeholder = { Text("标签 (逗号隔离, 如: 顺产,考研)", fontSize = 13.sp) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("选择发帖关联话题分类：", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(topics) { tp ->
                        val isSel = selectedTopic == tp
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedTopic = tp },
                            label = { Text(tp, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("匿名发表（不显示昵称头像）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isAnonymous,
                        onCheckedChange = { isAnonymous = it },
                        modifier = Modifier.testTag("anonymous_toggle")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isEmpty()) {
                        Toast.makeText(context, "写点内容才能发帖哦！", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    SupabaseManager.publishPost(
                        content = content,
                        tags = tagsInput,
                        topic = selectedTopic,
                        isAnonymous = isAnonymous,
                        imageUrl = "" // can default empty, can easily load from dynamic if required
                    )
                    Toast.makeText(context, "动态同步发布成功！", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("发布动态")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
