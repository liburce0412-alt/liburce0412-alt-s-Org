package com.campusai.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
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
import com.campusai.core.designsystem.PageMood
import com.campusai.core.designsystem.SpectraAction
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.SpectraIconAction
import com.campusai.core.designsystem.SpectraPageScaffold
import com.campusai.core.designsystem.SpectraPrimaryButton
import com.campusai.core.designsystem.SpectraStateKind
import com.campusai.core.designsystem.SpectraStatePane
import com.campusai.core.designsystem.SpectraStatus
import com.campusai.core.designsystem.SpectraStatusTone
import com.campusai.core.designsystem.SpectraSurface
import com.campusai.core.designsystem.SpectraTheme
import com.campusai.core.designsystem.SlideConfirm
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
    displayName: String,
    viewModel: CampusViewModel,
    onLogin: () -> Unit,
    contentPadding: PaddingValues,
) {
    val ownerName = displayName.trim().ifBlank { "我" }.take(16)
    val layout = SpectraTheme.layout
    val pageBottom = maxOf(contentPadding.calculateBottomPadding(), layout.pageBottomSpacing)
    var composing by rememberSaveable { mutableStateOf(false) }
    var selectedPost by remember { mutableStateOf<CommunityPost?>(null) }
    var reportingPost by remember { mutableStateOf<CommunityPost?>(null) }
    SpectraPageScaffold(mood = PageMood.SOCIAL) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = layout.pageHorizontalPadding,
                    end = layout.pageHorizontalPadding,
                    top = contentPadding.calculateTopPadding() + layout.pageTopSpacing,
                    bottom = pageBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("${ownerName}的树洞", style = MaterialTheme.typography.headlineLarge); Text("收起喧闹，留下对你真正重要的声音", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f)) }
                    SpectraAction(
                        text = "刷新动态",
                        onClick = { if (signedIn) viewModel.refreshPosts() else onLogin() },
                        emphasized = true,
                        mood = PageMood.SOCIAL,
                        icon = Icons.Rounded.Refresh,
                    )
                }
            }
            state.operationError?.let { message -> item { ErrorBar(message, PageMood.SOCIAL) { viewModel.clearOperationError() } } }
            when (val posts = state.posts) {
                UiState.Loading -> item {
                    SpectraStatePane(
                        kind = SpectraStateKind.LOADING,
                        title = "正在打开树洞",
                        detail = "正在同步最新留言；完成前不会用占位内容替代。",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                UiState.Empty -> item {
                    EmptyRemote(
                        title = "树洞还很安静",
                        detail = "已读取最新结果；你可以先写下第一句。",
                        action = "写进树洞",
                        mood = PageMood.SOCIAL,
                    ) { composing = true }
                }
                is UiState.Error -> item { RemoteError(posts.message, signedIn, onLogin, viewModel::refreshPosts, PageMood.SOCIAL) }
                is UiState.Data -> items(posts.value.size) { index ->
                    val post = posts.value[index]
                    PostCard(post, { viewModel.toggleLike(post.id) }, { viewModel.toggleBookmark(post.id) }, { selectedPost = post; viewModel.openPostComments(post.id) }) { reportingPost = post }
                }
                is UiState.Offline -> {
                    item {
                        SpectraStatePane(
                            kind = SpectraStateKind.OFFLINE,
                            title = "显示上次同步的动态",
                            detail = "当前无法联网；点赞、收藏和发布需要恢复网络后才能确认。",
                            modifier = Modifier.fillMaxWidth(),
                            actionLabel = "重新读取",
                            onAction = viewModel::refreshPosts,
                        )
                    }
                    items(posts.value.size) { index ->
                        val post = posts.value[index]
                        PostCard(post, { viewModel.toggleLike(post.id) }, { viewModel.toggleBookmark(post.id) }, { selectedPost = post; viewModel.openPostComments(post.id) }) { reportingPost = post }
                    }
                }
            }
            }
            FloatingActionButton(
                onClick = { if (signedIn) composing = true else onLogin() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = layout.pageHorizontalPadding, bottom = pageBottom + layout.compactGap),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Rounded.Add, "写进树洞") }
        }
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
    val haptic = LocalHapticFeedback.current
    SpectraSurface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onComments),
        mood = PageMood.SOCIAL,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(Color.White.copy(.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (post.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = post.avatarUrl,
                            contentDescription = "${post.author}的头像",
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        BrandMark(Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) { Text(post.author, style = MaterialTheme.typography.titleMedium); Text(remoteTime(post.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.52f)) }
                SpectraIconAction(
                    icon = Icons.Rounded.MoreHoriz,
                    label = "举报或反馈",
                    onClick = onReport,
                )
            }
            if (post.mediaUrl.isNotBlank()) {
                AsyncImage(model = post.mediaUrl, contentDescription = "帖子图片", modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(2.dp)))
            }
            Column(Modifier.padding(16.dp)) {
                if (post.topic.isNotBlank()) Text("# ${post.topic}", style = MaterialTheme.typography.labelMedium, color = SpectraColors.Focus)
                Spacer(Modifier.height(7.dp))
                Text(post.body, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpectraAction(
                        text = post.likes.toString(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLike()
                        },
                        selected = post.likedByMe,
                        mood = PageMood.SOCIAL,
                        icon = if (post.likedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    )
                    SpectraAction(
                        text = if (post.comments == 0) "评论" else "${post.comments} 条评论",
                        onClick = onComments,
                        modifier = Modifier.weight(1f),
                        mood = PageMood.SOCIAL,
                        icon = Icons.Rounded.ChatBubbleOutline,
                    )
                    SpectraIconAction(
                        icon = Icons.Rounded.BookmarkBorder,
                        label = "收藏",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBookmark()
                        },
                    )
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SpectraPageScaffold(mood = PageMood.SOCIAL) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .imePadding()
                        .padding(bottom = 48.dp),
                ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpectraIconAction(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = "返回树洞",
                        onClick = onDismiss,
                    )
                    Column(Modifier.weight(1f)) { Text("帖子与评论", style = MaterialTheme.typography.titleLarge); Text("${post.author} · ${remoteTime(post.createdAt)}", color = MaterialTheme.colorScheme.onSurface.copy(.55f)) }
                    SpectraIconAction(
                        icon = Icons.Rounded.Refresh,
                        label = "刷新评论",
                        onClick = onRetry,
                    )
                }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SpectraSurface(
                            modifier = Modifier.fillMaxWidth(),
                            mood = PageMood.SOCIAL,
                            emphasized = true,
                        ) {
                            if (post.topic.isNotBlank()) Text("# ${post.topic}", color = SpectraColors.Focus, style = MaterialTheme.typography.labelMedium)
                            Text(post.body, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    error?.let { message -> item { ErrorBar(message, PageMood.SOCIAL, onClearError) } }
                    item { Text("评论", style = MaterialTheme.typography.titleLarge) }
                    when (comments) {
                        UiState.Loading -> item {
                            SpectraStatePane(
                                kind = SpectraStateKind.LOADING,
                                title = "正在读取评论",
                                detail = "会话不会因加载而自动发布草稿。",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        UiState.Empty -> item {
                            EmptyRemote(
                                title = "还没有评论",
                                detail = "这条动态尚无评论；你可以在下方写下具体而友善的回应。",
                                action = "重新检查",
                                mood = PageMood.SOCIAL,
                                onAction = onRetry,
                            )
                        }
                        is UiState.Error -> item { RemoteError(comments.message, true, {}, onRetry, PageMood.SOCIAL) }
                        is UiState.Data -> items(comments.value, key = { it.id }) { comment -> CommentRow(comment) }
                        is UiState.Offline -> {
                            item {
                                SpectraStatePane(
                                    kind = SpectraStateKind.OFFLINE,
                                    title = "显示上次同步的评论",
                                    detail = "恢复网络后再发布新评论。",
                                    modifier = Modifier.fillMaxWidth(),
                                    actionLabel = "重新读取",
                                    onAction = onRetry,
                                )
                            }
                            items(comments.value, key = { it.id }) { comment -> CommentRow(comment) }
                        }
                    }
                }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(.12f))
                            .navigationBarsPadding()
                            .heightIn(min = 76.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        ) { Icon(Icons.AutoMirrored.Rounded.Send, "发布评论", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: CommunityComment) {
    SpectraSurface(Modifier.fillMaxWidth(), mood = PageMood.SOCIAL) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(comment.author, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (comment.moderationStatus == "pending") {
                SpectraStatus("待审核", tone = SpectraStatusTone.WARNING)
            }
        }
        Text(comment.body, style = MaterialTheme.typography.bodyLarge)
        Text(remoteTime(comment.createdAt), color = MaterialTheme.colorScheme.onSurface.copy(.48f), style = MaterialTheme.typography.bodySmall)
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
    contentPadding: PaddingValues,
) {
    val layout = SpectraTheme.layout
    val pageBottom = maxOf(contentPadding.calculateBottomPadding(), layout.pageBottomSpacing)
    var composing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MarketplaceListing?>(null) }
    LaunchedEffect(signedIn, state.listingsRefreshing, state.listingsHasSynced, state.listingsSyncError) {
        if (signedIn && !state.listingsRefreshing && !state.listingsHasSynced && state.listingsSyncError == null) {
            viewModel.refreshListings()
        }
    }
    SpectraPageScaffold(mood = PageMood.COMMERCE) {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                contentPadding = PaddingValues(
                    start = layout.pageHorizontalPadding,
                    end = layout.pageHorizontalPadding,
                    top = contentPadding.calculateTopPadding() + layout.pageTopSpacing,
                    bottom = pageBottom,
                ),
                horizontalArrangement = Arrangement.spacedBy(layout.compactGap),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column { Text("心愿墙", style = MaterialTheme.typography.headlineLarge); Text("把想遇见、想交换的东西，认真留在这里", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f)); Spacer(Modifier.height(layout.compactGap)) }
            }
            state.operationError?.let { message -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { ErrorBar(message, PageMood.COMMERCE) { viewModel.clearOperationError() } } }
            when (val listings = state.listings) {
                UiState.Loading -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    MarketEmptyState(
                        refreshing = true,
                        hasSynced = false,
                        syncError = null,
                        onPublish = { composing = true },
                        onRetry = viewModel::refreshListings,
                    )
                }
                UiState.Empty -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    MarketEmptyState(
                        refreshing = state.listingsRefreshing,
                        hasSynced = state.listingsHasSynced,
                        syncError = state.listingsSyncError,
                        onPublish = { composing = true },
                        onRetry = viewModel::refreshListings,
                    )
                }
                is UiState.Error -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { RemoteError(listings.message, signedIn, onLogin, viewModel::refreshListings, PageMood.COMMERCE) }
                is UiState.Data -> items(listings.value, key = { it.id }) { listing -> ListingCardView(listing) { selected = listing } }
                is UiState.Offline -> {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        SpectraStatePane(
                            kind = SpectraStateKind.OFFLINE,
                            title = "显示上次同步的心愿卡",
                            detail = "内容可能已变化；恢复网络后再联系发布者。",
                            modifier = Modifier.fillMaxWidth(),
                            actionLabel = "重新读取",
                            onAction = viewModel::refreshListings,
                        )
                    }
                    items(listings.value, key = { it.id }) { listing -> ListingCardView(listing) { selected = listing } }
                }
            }
            }
            FloatingActionButton(
                onClick = { if (signedIn) composing = true else onLogin() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = layout.pageHorizontalPadding, bottom = pageBottom + layout.compactGap),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Rounded.Sell, "贴一张心愿") }
        }
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
            onClose = { selected = null },
            onFavorite = { viewModel.toggleFavorite(listing.id) },
            onContact = {
                viewModel.openConversation(listing.sellerId, listing.id) { conversationId ->
                    selected = null
                    onOpenConversation(conversationId)
                }
            },
        )
    }
}

@Composable
private fun ListingCardView(listing: MarketplaceListing, onClick: () -> Unit) {
    SpectraSurface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        mood = PageMood.COMMERCE,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Brush.linearGradient(listOf(SpectraColors.Warm.copy(.2f), SpectraColors.Rose.copy(.1f)))),
                contentAlignment = Alignment.Center,
            ) {
                if (listing.mediaUrl.isNotBlank()) {
                    AsyncImage(
                        model = listing.mediaUrl,
                        contentDescription = "心愿图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    BrandMark(Modifier.size(64.dp))
                }
                if (listing.moderationStatus.isNotBlank() && listing.moderationStatus != "approved") {
                    SpectraStatus(
                        text = moderationText(listing.moderationStatus),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        tone = SpectraStatusTone.WARNING,
                    )
                } else if (listing.status != "active") {
                    SpectraStatus(
                        text = statusText(listing.status),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        tone = SpectraStatusTone.WARNING,
                    )
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(listing.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("¥${"%.2f".format(listing.priceCents / 100.0)}", fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                Text("由 ${listing.seller} 留下", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.55f), maxLines = 1)
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
    ComposerDialog(
        title = "写进树洞",
        subtitle = "一句真话就够了。当前编辑期间，内容只作为本机草稿存在。",
        progress = "${body.length} / 5000",
        primaryText = if (busy) "正在写入" else "放进树洞",
        primaryEnabled = body.isNotBlank() && !busy,
        onClose = onClose,
        mood = PageMood.SOCIAL,
        onPrimary = {
            scope.launch {
                val image = imageUri?.let { uri -> runCatching { readUploadImage(context, uri) }.getOrElse { mediaError = it.message ?: "图片无法读取。"; return@launch } }
                onPublish(body, topic, anonymous, image)
            }
        },
    ) {
        ComposerSection("01", "此刻想说的", "先把句子写完，排版和话题可以稍后再想。", PageMood.SOCIAL) {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(5000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
                placeholder = { Text("今天有什么想被记住？") },
                shape = RoundedCornerShape(18.dp),
            )
        }
        ComposerSection("02", "给它一个线索", "话题可选，日后回看时更容易找到。", PageMood.SOCIAL) {
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("话题") },
                placeholder = { Text("例如：今夜、灵感、想说") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                supportingText = { Text("${topic.length} / 40") },
            )
        }
        ComposerSection("03", "谁在说话", "这是一个可滑动的选择；匿名内容不会向其他人展示你的名字。", PageMood.SOCIAL) {
            com.campusai.core.designsystem.CaesarSlidingSelector(
                options = listOf("以本人发布", "匿名发布"),
                selectedIndex = if (anonymous) 1 else 0,
                onSelected = { anonymous = it == 1 },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ComposerSection("04", "加一张画面", "不加图也可以发布。", PageMood.SOCIAL) {
            MediaPickerSlot(
                uri = imageUri,
                emptyLabel = "从相册选一张图",
                contentDescription = "待发布的树洞图片",
                mood = PageMood.SOCIAL,
                onClick = { picker.launch("image/*") },
            )
            mediaError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Text("发布后会进入审核流程；重复点击不会创建多份内容。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
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
    ComposerDialog(
        title = "贴一张心愿",
        subtitle = "把东西、价格和碰面方式说清楚，一张心愿卡就完整了。",
        progress = if (title.isBlank()) "草稿" else "已填 ${listOf(title, price, location).count { it.isNotBlank() }} / 3",
        primaryText = if (busy) "正在写入" else "贴上心愿墙",
        primaryEnabled = title.isNotBlank() && cents != null && cents >= 0 && !busy,
        onClose = onClose,
        mood = PageMood.COMMERCE,
        onPrimary = {
            scope.launch {
                val image = imageUri?.let { uri -> runCatching { readUploadImage(context, uri) }.getOrElse { mediaError = it.message ?: "图片无法读取。"; return@launch } }
                onPublish(title, description, cents ?: 0, location, image)
            }
        },
    ) {
        ComposerSection("01", "这是什么", "先给心愿一个一眼能懂的名字。", PageMood.COMMERCE) {
            OutlinedTextField(title, { title = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("心愿标题") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            OutlinedTextField(description, { description = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 132.dp), label = { Text("细节（可选）") }, placeholder = { Text("状态、成色、期待的交换方式…") }, shape = RoundedCornerShape(18.dp))
        }
        ComposerSection("02", "期待的条件", "价格是必填项，碰面地点可以稍后再补充。", PageMood.COMMERCE) {
            OutlinedTextField(price, { price = it.filter { char -> char.isDigit() || char == '.' }.take(10) }, Modifier.fillMaxWidth(), label = { Text("期待价格（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(18.dp), isError = price.isNotBlank() && cents == null)
            OutlinedTextField(location, { location = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("碰面地点（可选）") }, singleLine = true, shape = RoundedCornerShape(18.dp))
        }
        ComposerSection("03", "让它被看见", "清楚的图片会让回应更准确。", PageMood.COMMERCE) {
            MediaPickerSlot(
                uri = imageUri,
                emptyLabel = "选一张代表它的图",
                contentDescription = "待发布的心愿图片",
                mood = PageMood.COMMERCE,
                onClick = { picker.launch("image/*") },
            )
            mediaError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Text("心愿卡会先进入审核；审核通过前只有你和管理员可见。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
    }
}

@Composable
private fun ComposerDialog(
    title: String,
    subtitle: String,
    progress: String,
    primaryText: String,
    primaryEnabled: Boolean,
    onClose: () -> Unit,
    mood: PageMood,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            SpectraPageScaffold(mood = mood) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SpectraIconAction(icon = Icons.AutoMirrored.Rounded.ArrowBack, label = "收起草稿", onClick = onClose)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        Text("未发布 · 本机草稿", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.52f))
                    }
                    SpectraStatus(progress, tone = SpectraStatusTone.INFO)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(.66f))
                    content()
                    Spacer(Modifier.height(8.dp))
                }
                    SpectraSurface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        mood = mood,
                        emphasized = true,
                        contentPadding = PaddingValues(10.dp),
                    ) {
                        SpectraPrimaryButton(primaryText, onPrimary, Modifier.fillMaxWidth(), enabled = primaryEnabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerSection(
    number: String,
    title: String,
    detail: String,
    mood: PageMood,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(3.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.54f))
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(.09f))
    }
}

@Composable
private fun MediaPickerSlot(
    uri: Uri?,
    emptyLabel: String,
    contentDescription: String,
    mood: PageMood,
    onClick: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        radius = 22,
        emphasized = uri != null,
        shadowed = false,
    ) {
        Box(
            Modifier.fillMaxWidth().height(176.dp).background(MaterialTheme.colorScheme.onSurface.copy(.035f)),
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null) {
                AsyncImage(uri, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                SpectraStatus("点击更换", modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), tone = SpectraStatusTone.INFO)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.onSurface.copy(.08f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurface.copy(.68f))
                    }
                    Text(emptyLabel, style = MaterialTheme.typography.labelLarge)
                    Text("JPG · PNG · WEBP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.46f))
                }
            }
        }
    }
}

@Composable
private fun ListingDetails(
    listing: MarketplaceListing,
    ownListing: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onContact: () -> Unit,
) {
    FullScreenDialog(listing.title, onClose, PageMood.COMMERCE) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(SpectraColors.Warm.copy(.22f), SpectraColors.Rose.copy(.1f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (listing.mediaUrl.isNotBlank()) {
                AsyncImage(
                    model = listing.mediaUrl,
                    contentDescription = "心愿图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BrandMark(Modifier.size(96.dp))
            }
        }
        Text("¥${"%.2f".format(listing.priceCents / 100.0)}", fontFamily = Tomorrow, style = MaterialTheme.typography.headlineLarge)
        Text(listing.description.ifBlank { "留下这张心愿卡的人暂未填写更多细节。" }, style = MaterialTheme.typography.bodyLarge)
        SpectraStatus(
            text = listOf(
                listing.seller,
                listing.location,
                statusText(listing.status),
                listing.moderationStatus.takeIf { it.isNotBlank() && it != "approved" }?.let(::moderationText).orEmpty(),
            ).filter { it.isNotBlank() }.joinToString(" · "),
            tone = if (listing.status == "active") SpectraStatusTone.SUCCESS else SpectraStatusTone.WARNING,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpectraAction(
                text = "收藏",
                onClick = onFavorite,
                modifier = Modifier.weight(1f),
                mood = PageMood.COMMERCE,
                icon = Icons.Rounded.BookmarkBorder,
            )
            SpectraAction(
                text = if (ownListing) "这是你的心愿" else "联系发布者",
                onClick = if (ownListing) ({}) else onContact,
                modifier = Modifier.weight(1f),
                enabled = !ownListing,
                mood = PageMood.COMMERCE,
                icon = Icons.Rounded.ChatBubbleOutline,
            )
        }
    }
}

@Composable
private fun FullScreenDialog(
    title: String,
    onClose: () -> Unit,
    mood: PageMood,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SpectraPageScaffold(mood = mood) {
                Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SpectraIconAction(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                label = "返回",
                                onClick = onClose,
                            )
                            Text(title, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                        item {
                            SpectraSurface(
                                modifier = Modifier.fillMaxWidth(),
                                mood = mood,
                                emphasized = true,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketEmptyState(
    refreshing: Boolean,
    hasSynced: Boolean,
    syncError: String?,
    onPublish: () -> Unit,
    onRetry: () -> Unit,
) {
    val presentation = marketEmptyPresentation(
        refreshing = refreshing,
        hasSynced = hasSynced,
        hasError = syncError != null,
    )
    val tone = when {
        refreshing -> SpectraStatusTone.INFO
        syncError != null -> SpectraStatusTone.STALE
        else -> SpectraStatusTone.NEUTRAL
    }
    SpectraSurface(
        modifier = Modifier.fillMaxWidth(),
        mood = PageMood.COMMERCE,
        emphasized = true,
    ) {
        SpectraStatus(presentation.status, tone = tone)
        Text(presentation.title, style = MaterialTheme.typography.titleLarge)
        Text(presentation.detail, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        SpectraAction(
            text = "贴一张心愿",
            onClick = onPublish,
            modifier = Modifier.fillMaxWidth(),
            emphasized = true,
            mood = PageMood.COMMERCE,
        )
        if (syncError != null || (!refreshing && !hasSynced)) {
            SpectraAction(
                text = if (syncError != null) "重新读取" else "读取目录",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                mood = PageMood.COMMERCE,
                icon = Icons.Rounded.Refresh,
            )
        }
    }
}

internal data class MarketEmptyPresentation(
    val status: String,
    val title: String,
    val detail: String,
)

internal fun marketEmptyPresentation(
    refreshing: Boolean,
    hasSynced: Boolean,
    hasError: Boolean,
): MarketEmptyPresentation = when {
    refreshing && !hasSynced -> MarketEmptyPresentation(
        status = "正在确认",
        title = "正在确认心愿墙",
        detail = "本地暂时没有可显示的心愿卡；正在后台获取最新结果。",
    )
    refreshing -> MarketEmptyPresentation(
        status = "正在更新",
        title = "上次同步时心愿墙是空的",
        detail = "正在后台确认最新内容；你仍可直接贴上一张心愿。",
    )
    hasError && !hasSynced -> MarketEmptyPresentation(
        status = "同步未完成",
        title = "暂时无法确认心愿墙",
        detail = "尚未取得远端结果；当前空白不代表没有心愿卡。",
    )
    hasError -> MarketEmptyPresentation(
        status = "刷新未完成",
        title = "上次同步时心愿墙是空的",
        detail = "本次刷新失败，目录可能已经变化；网络恢复后可重试。",
    )
    hasSynced -> MarketEmptyPresentation(
        status = "暂无内容",
        title = "心愿墙还很安静",
        detail = "本次同步结果为空；新心愿提交后会先进入审核。",
    )
    else -> MarketEmptyPresentation(
        status = "暂无内容",
        title = "暂时没有显示心愿",
        detail = "正在等待首次同步；你也可以直接贴上一张心愿。",
    )
}

@Composable
private fun EmptyRemote(
    title: String,
    detail: String,
    action: String,
    mood: PageMood,
    onAction: () -> Unit,
) {
    SpectraSurface(
        modifier = Modifier.fillMaxWidth(),
        mood = mood,
        emphasized = true,
    ) {
        SpectraStatus("暂无内容", tone = SpectraStatusTone.NEUTRAL)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        SpectraAction(
            text = action,
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            emphasized = true,
            mood = mood,
        )
    }
}

@Composable
private fun RemoteError(
    message: String,
    signedIn: Boolean,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    mood: PageMood,
) {
    SpectraSurface(Modifier.fillMaxWidth(), mood = mood) {
        SpectraStatus("需要处理", tone = SpectraStatusTone.ERROR)
        Text(message, style = MaterialTheme.typography.titleMedium)
        Text(
            if (signedIn) "请检查网络与权限后重试。" else "当前未登录；本地时间和课程表仍可使用。",
            color = MaterialTheme.colorScheme.onSurface.copy(.6f),
        )
        SpectraAction(
            text = if (signedIn) "重新读取" else "安全登录",
            onClick = if (signedIn) onRetry else onLogin,
            mood = mood,
        )
    }
}

@Composable
private fun ErrorBar(message: String, mood: PageMood, onDismiss: () -> Unit) {
    SpectraSurface(
        modifier = Modifier.fillMaxWidth(),
        mood = mood,
        shadowed = false,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            SpectraIconAction(
                icon = Icons.Rounded.Close,
                label = "关闭错误",
                onClick = onDismiss,
            )
        }
    }
}

private fun remoteTime(value: String): String = value.take(16).replace('T', ' ').ifBlank { "刚刚" }
private fun statusText(value: String): String = when (value) { "active" -> "在售"; "reserved" -> "已预订"; "sold" -> "已售"; "withdrawn" -> "已下架"; "removed" -> "已移除"; else -> value }
private fun moderationText(value: String): String = when (value) { "pending" -> "待审核"; "approved" -> "已通过"; "rejected" -> "未通过"; else -> value }

private suspend fun readUploadImage(context: Context, uri: Uri): UploadImage = withContext(Dispatchers.IO) {
    val contentType = context.contentResolver.getType(uri).orEmpty().lowercase()
    require(contentType in setOf("image/jpeg", "image/png", "image/webp")) { "只支持 JPEG、PNG 或 WebP 图片。" }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("图片无法读取。")
    require(bytes.isNotEmpty()) { "图片内容为空。" }
    require(bytes.size <= 15 * 1024 * 1024) { "图片不能超过 15MB。" }
    UploadImage(bytes, contentType)
}
