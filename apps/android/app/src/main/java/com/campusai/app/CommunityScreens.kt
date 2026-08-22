package com.campusai.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.campusai.core.designsystem.BrandMark
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.SpectraPrimaryButton
import com.campusai.core.designsystem.SlideConfirm
import com.campusai.core.designsystem.TelemetryChip
import com.campusai.core.designsystem.Tomorrow
import com.campusai.core.model.UiState
import com.campusai.features.community.CampusRemoteState
import com.campusai.features.community.CampusViewModel
import com.campusai.features.community.CommunityComment
import com.campusai.features.community.CommunityPost
import com.campusai.features.community.MarketplaceListing
import com.campusai.features.community.UploadImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CampusScreen(
    state: CampusRemoteState,
    signedIn: Boolean,
    userId: String,
    viewModel: CampusViewModel,
    onLogin: () -> Unit,
    contentPadding: PaddingValues,
) {
    var composing by rememberSaveable { mutableStateOf(false) }
    var selectedPost by remember { mutableStateOf<CommunityPost?>(null) }
    var reportingPost by remember { mutableStateOf<CommunityPost?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 24.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("校园", style = MaterialTheme.typography.headlineLarge); Text("真实、克制、可追溯的校园动态", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f)) }
                    TelemetryChip("最新", true, onClick = { if (signedIn) viewModel.refreshPosts() else onLogin() })
                }
            }
            state.operationError?.let { message -> item { ErrorBar(message) { viewModel.clearOperationError() } } }
            when (val posts = state.posts) {
                UiState.Loading -> items(3) { LoadingCard() }
                UiState.Empty -> item { EmptyRemote("还没有校园动态", "成为第一个分享今天的人。", "发布动态") { composing = true } }
                is UiState.Error -> item { RemoteError(posts.message, signedIn, onLogin, viewModel::refreshPosts) }
                is UiState.Data -> items(posts.value.size) { index ->
                    val post = posts.value[index]
                    PostCard(post, { viewModel.toggleLike(post.id) }, { viewModel.toggleBookmark(post.id) }, { selectedPost = post; viewModel.openPostComments(post.id) }) { reportingPost = post }
                }
                is UiState.Offline -> items(posts.value.size) { index ->
                    val post = posts.value[index]
                    PostCard(post, { viewModel.toggleLike(post.id) }, { viewModel.toggleBookmark(post.id) }, { selectedPost = post; viewModel.openPostComments(post.id) }) { reportingPost = post }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (signedIn) composing = true else onLogin() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 22.dp),
            shape = CircleShape,
            containerColor = SpectraColors.Ink,
            contentColor = Color.White,
        ) { Icon(Icons.Rounded.Add, "发布帖子") }
    }
    if (composing) PostComposer(
        busy = state.operationBusy,
        onClose = { composing = false },
        onPublish = { body, topic, anonymous, image -> viewModel.publishPost(userId, body, topic, anonymous, image) { composing = false } },
    )
    selectedPost?.let { post ->
        PostDetails(
            post = post,
            comments = state.comments,
            busy = state.operationBusy,
            error = state.operationError,
            onDismiss = { viewModel.closePostComments(); selectedPost = null },
            onRetry = { viewModel.openPostComments(post.id) },
            onClearError = viewModel::clearOperationError,
            onPublish = { body, onSuccess -> viewModel.publishComment(post.id, body, onSuccess) },
        )
    }
    reportingPost?.let { post -> ReportDialog(
        targetLabel = post.body.take(42),
        busy = state.operationBusy,
        onDismiss = { reportingPost = null },
        onSubmit = { reason, details -> viewModel.submitReport(userId, "post", post.id, reason, details) { reportingPost = null } },
    ) }
}

@Composable
private fun PostCard(post: CommunityPost, onLike: () -> Unit, onBookmark: () -> Unit, onComments: () -> Unit, onReport: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(SpectraColors.Cyan, SpectraColors.Violet)), CircleShape), contentAlignment = Alignment.Center) { BrandMark(Modifier.size(30.dp), Color.White) }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) { Text(post.author, style = MaterialTheme.typography.titleMedium); Text(remoteTime(post.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.52f)) }
                IconButton(onClick = onReport) { Icon(Icons.Rounded.MoreHoriz, "举报或反馈") }
            }
            if (post.mediaUrl.isNotBlank()) {
                AsyncImage(model = post.mediaUrl, contentDescription = "帖子图片", modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(2.dp)))
            }
            Column(Modifier.padding(16.dp)) {
                if (post.topic.isNotBlank()) Text("# ${post.topic}", style = MaterialTheme.typography.labelMedium, color = SpectraColors.Focus)
                Spacer(Modifier.height(7.dp))
                Text(post.body, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.clickable(onClick = onLike).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.FavoriteBorder, "点赞", Modifier.size(20.dp)); Spacer(Modifier.size(5.dp)); Text(post.likes.toString()) }
                    Row(Modifier.clickable(onClick = onComments).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.ChatBubbleOutline, "评论", Modifier.size(20.dp)); Spacer(Modifier.size(5.dp)); Text(post.comments.toString()) }
                    Icon(Icons.Rounded.BookmarkBorder, "收藏", Modifier.clickable(onClick = onBookmark).padding(vertical = 8.dp).size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportDialog(targetLabel: String, busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        title = { Text("举报内容") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(targetLabel, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
            OutlinedTextField(reason, { reason = it.take(120) }, label = { Text("原因") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(details, { details = it.take(1000) }, label = { Text("补充说明") }, minLines = 3, shape = RoundedCornerShape(12.dp))
            Text("举报会进入审核队列，不会直接删除内容。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
        } },
        confirmButton = { TextButton(enabled = reason.isNotBlank() && !busy, onClick = { onSubmit(reason, details) }) { Text(if (busy) "正在提交" else "提交举报") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PostDetails(
    post: CommunityPost,
    comments: UiState<List<CommunityComment>>,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onPublish: (String, () -> Unit) -> Unit,
) {
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(.96f)).navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回校园") }
                Column(Modifier.weight(1f)) { Text("帖子与评论", style = MaterialTheme.typography.titleLarge); Text("${post.author} · ${remoteTime(post.createdAt)}", color = MaterialTheme.colorScheme.onSurface.copy(.55f)) }
                IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "刷新评论") }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 16, emphasized = true) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (post.topic.isNotBlank()) Text("# ${post.topic}", color = SpectraColors.Focus, style = MaterialTheme.typography.labelMedium)
                            Text(post.body, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                error?.let { message -> item { ErrorBar(message, onClearError) } }
                item { Text("评论", style = MaterialTheme.typography.titleLarge) }
                when (comments) {
                    UiState.Loading -> items(2) { LoadingCard() }
                    UiState.Empty -> item { EmptyRemote("还没有评论", "说点具体而友善的话。", "刷新") { onRetry() } }
                    is UiState.Error -> item { RemoteError(comments.message, true, {}, onRetry) }
                    is UiState.Data -> items(comments.value, key = { it.id }) { comment -> CommentRow(comment) }
                    is UiState.Offline -> items(comments.value, key = { it.id }) { comment -> CommentRow(comment) }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(2000) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("写下评论…") },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                )
                IconButton(
                    onClick = { onPublish(draft) { draft = "" } },
                    enabled = draft.isNotBlank() && !busy,
                    modifier = Modifier.size(52.dp).background(SpectraColors.Ink, CircleShape),
                ) { Icon(Icons.AutoMirrored.Rounded.Send, "发布评论", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: CommunityComment) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (comment.moderationStatus == "pending") Text("待审核", color = SpectraColors.Warm, style = MaterialTheme.typography.labelMedium)
            }
            Text(comment.body, style = MaterialTheme.typography.bodyLarge)
            Text(remoteTime(comment.createdAt), color = MaterialTheme.colorScheme.onSurface.copy(.48f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MarketScreen(
    state: CampusRemoteState,
    signedIn: Boolean,
    userId: String,
    viewModel: CampusViewModel,
    onLogin: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOrderCreated: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var composing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MarketplaceListing?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 24.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column { Text("市场", style = MaterialTheme.typography.headlineLarge); Text("校园内可信、清楚的闲置流转", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f)); Spacer(Modifier.height(8.dp)) }
            }
            state.operationError?.let { message -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { ErrorBar(message) { viewModel.clearOperationError() } } }
            when (val listings = state.listings) {
                UiState.Loading -> items(4) { LoadingCard(square = true) }
                UiState.Empty -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { EmptyRemote("还没有在售商品", "发布第一件校园闲置物品。", "发布商品") { composing = true } }
                is UiState.Error -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { RemoteError(listings.message, signedIn, onLogin, viewModel::refreshListings) }
                is UiState.Data -> items(listings.value, key = { it.id }) { listing -> ListingCardView(listing) { selected = listing } }
                is UiState.Offline -> items(listings.value, key = { it.id }) { listing -> ListingCardView(listing) { selected = listing } }
            }
        }
        FloatingActionButton(
            onClick = { if (signedIn) composing = true else onLogin() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 22.dp),
            shape = CircleShape,
            containerColor = SpectraColors.Ink,
            contentColor = Color.White,
        ) { Icon(Icons.Rounded.Sell, "发布商品") }
    }
    if (composing) ListingComposer(
        busy = state.operationBusy,
        onClose = { composing = false },
        onPublish = { title, description, cents, location, image -> viewModel.publishListing(userId, title, description, cents, location, image) { composing = false } },
    )
    selected?.let { listing ->
        ListingDetails(
            listing = listing,
            ownListing = listing.sellerId == userId,
            busy = state.operationBusy,
            onClose = { selected = null },
            onFavorite = { viewModel.toggleFavorite(listing.id) },
            onContact = {
                viewModel.openConversation(listing.sellerId, listing.id) { conversationId ->
                    selected = null
                    onOpenConversation(conversationId)
                }
            },
            onBuy = {
                viewModel.createOrder(listing.id) { orderId ->
                    selected = null
                    onOrderCreated(orderId)
                }
            },
        )
    }
}

@Composable
private fun ListingCardView(listing: MarketplaceListing, onClick: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 16, onClick = onClick) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Brush.linearGradient(listOf(SpectraColors.Cyan.copy(.6f), SpectraColors.Violet.copy(.56f)))), contentAlignment = Alignment.Center) {
                if (listing.mediaUrl.isNotBlank()) AsyncImage(listing.mediaUrl, "商品图片", Modifier.fillMaxSize()) else BrandMark(Modifier.size(64.dp), Color.White.copy(.86f))
                if (listing.status != "active") Text(statusText(listing.status), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.background(SpectraColors.Ink.copy(.86f), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Column(Modifier.padding(12.dp)) {
                Text(listing.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("¥${"%.2f".format(listing.priceCents / 100.0)}", fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                Text(listing.seller, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.55f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PostComposer(busy: Boolean, onClose: () -> Unit, onPublish: (String, String, Boolean, UploadImage?) -> Unit) {
    var body by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var anonymous by rememberSaveable { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> imageUri = uri; mediaError = null }
    FullScreenDialog("发布校园动态", onClose) {
        OutlinedTextField(body, { body = it.take(5000) }, Modifier.fillMaxWidth().height(220.dp), label = { Text("想分享什么？") })
        OutlinedTextField(topic, { topic = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("话题（可选）") }, singleLine = true)
        imageUri?.let { uri -> AsyncImage(uri, "待发布图片", Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))) }
        TelemetryChip(if (imageUri == null) "选择图片" else "更换图片", false, { picker.launch("image/*") }, Modifier.fillMaxWidth())
        mediaError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(anonymous, { anonymous = it }); Text("匿名发布；不同帖子不会建立身份关联") }
        Text("发布后会进入审核流程；保存不会被成就动画阻塞。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        SpectraPrimaryButton(if (busy) "正在保存" else "确认发布", {
            scope.launch {
                val image = imageUri?.let { uri -> runCatching { readUploadImage(context, uri) }.getOrElse { mediaError = it.message ?: "图片无法读取。"; return@launch } }
                onPublish(body, topic, anonymous, image)
            }
        }, Modifier.fillMaxWidth(), enabled = body.isNotBlank() && !busy)
    }
}

@Composable
private fun ListingComposer(busy: Boolean, onClose: () -> Unit, onPublish: (String, String, Int, String, UploadImage?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> imageUri = uri; mediaError = null }
    val cents = price.toBigDecimalOrNull()?.movePointRight(2)?.toInt()
    FullScreenDialog("发布商品", onClose) {
        OutlinedTextField(title, { title = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("商品名称") }, singleLine = true)
        OutlinedTextField(description, { description = it.take(2000) }, Modifier.fillMaxWidth().height(160.dp), label = { Text("描述") })
        OutlinedTextField(price, { price = it.filter { char -> char.isDigit() || char == '.' }.take(10) }, Modifier.fillMaxWidth(), label = { Text("价格（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(location, { location = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("交易地点") }, singleLine = true)
        imageUri?.let { uri -> AsyncImage(uri, "待发布商品图片", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) }
        TelemetryChip(if (imageUri == null) "选择商品图片" else "更换商品图片", false, { picker.launch("image/*") }, Modifier.fillMaxWidth())
        mediaError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("商品将先进入审核；审核通过前只有你和管理员可见。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        SpectraPrimaryButton(if (busy) "正在保存" else "提交商品", {
            scope.launch {
                val image = imageUri?.let { uri -> runCatching { readUploadImage(context, uri) }.getOrElse { mediaError = it.message ?: "图片无法读取。"; return@launch } }
                onPublish(title, description, cents ?: 0, location, image)
            }
        }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && cents != null && cents >= 0 && !busy)
    }
}

@Composable
private fun ListingDetails(
    listing: MarketplaceListing,
    ownListing: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onContact: () -> Unit,
    onBuy: () -> Unit,
) {
    FullScreenDialog(listing.title, onClose) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(SpectraColors.Cyan.copy(.6f), SpectraColors.Violet.copy(.56f)))), contentAlignment = Alignment.Center) {
            if (listing.mediaUrl.isNotBlank()) AsyncImage(listing.mediaUrl, "商品图片", Modifier.fillMaxSize()) else BrandMark(Modifier.size(96.dp), Color.White)
        }
        Text("¥${"%.2f".format(listing.priceCents / 100.0)}", fontFamily = Tomorrow, style = MaterialTheme.typography.headlineLarge)
        Text(listing.description.ifBlank { "卖家暂未填写更多描述。" }, style = MaterialTheme.typography.bodyLarge)
        Text(listOf(listing.seller, listing.location, statusText(listing.status)).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TelemetryChip("收藏", false, onFavorite, Modifier.weight(1f))
            TelemetryChip(if (ownListing) "这是你的商品" else "联系卖家", false, if (ownListing) ({}) else onContact, Modifier.weight(1f))
        }
        SlideConfirm(
            text = when {
                ownListing -> "不能购买自己的商品"
                busy -> "正在创建订单…"
                else -> "滑动确认购买"
            },
            onConfirm = onBuy,
            enabled = listing.status == "active" && !ownListing && !busy,
        )
    }
}

@Composable
private fun FullScreenDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(.96f)).navigationBarsPadding()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }; Text(title, style = MaterialTheme.typography.headlineMedium) } }
                item { GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content) } }
            }
        }
    }
}

@Composable
private fun LoadingCard(square: Boolean = false) {
    GlassPanel(Modifier.fillMaxWidth().then(if (square) Modifier.aspectRatio(1f) else Modifier.height(150.dp)), radius = 16) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.fillMaxWidth(.45f).height(18.dp).background(SpectraColors.Silver.copy(.55f), CircleShape))
            Spacer(Modifier.fillMaxWidth().height(12.dp).background(SpectraColors.Silver.copy(.35f), CircleShape))
            Spacer(Modifier.fillMaxWidth(.72f).height(12.dp).background(SpectraColors.Silver.copy(.35f), CircleShape))
        }
    }
}

@Composable
private fun EmptyRemote(title: String, detail: String, action: String, onAction: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BrandMark(Modifier.size(72.dp)); Text(title, style = MaterialTheme.typography.titleLarge); Text(detail, color = MaterialTheme.colorScheme.onSurface.copy(.6f)); TelemetryChip(action, true, onAction)
        }
    }
}

@Composable
private fun RemoteError(message: String, signedIn: Boolean, onLogin: () -> Unit, onRetry: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, style = MaterialTheme.typography.titleMedium)
            Text(if (signedIn) "请检查网络与权限后重试。" else "本地时间和课程表仍可继续使用。", color = MaterialTheme.colorScheme.onSurface.copy(.6f))
            TelemetryChip(if (signedIn) "重新读取" else "安全登录", true, if (signedIn) onRetry else onLogin)
        }
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error.copy(.1f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)); IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Refresh, "关闭错误") }
    }
}

private fun remoteTime(value: String): String = value.take(16).replace('T', ' ').ifBlank { "刚刚" }
private fun statusText(value: String): String = when (value) { "active" -> "在售"; "reserved" -> "已预订"; "sold" -> "已售"; "withdrawn" -> "已下架"; "removed" -> "已移除"; else -> value }

private suspend fun readUploadImage(context: Context, uri: Uri): UploadImage = withContext(Dispatchers.IO) {
    val contentType = context.contentResolver.getType(uri).orEmpty().lowercase()
    require(contentType in setOf("image/jpeg", "image/png", "image/webp")) { "只支持 JPEG、PNG 或 WebP 图片。" }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("图片无法读取。")
    require(bytes.isNotEmpty()) { "图片内容为空。" }
    require(bytes.size <= 15 * 1024 * 1024) { "图片不能超过 15MB。" }
    UploadImage(bytes, contentType)
}
