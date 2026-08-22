package com.campusai.app

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.campusai.core.designsystem.TelemetryChip
import com.campusai.core.designsystem.Tomorrow
import com.campusai.core.model.UiState
import com.campusai.features.community.CampusRemoteState
import com.campusai.features.community.CampusViewModel
import com.campusai.features.community.CommunityPost
import com.campusai.features.community.MarketplaceListing

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
                is UiState.Data -> items(posts.value.size) { index -> PostCard(posts.value[index], { viewModel.toggleLike(posts.value[index].id) }, { viewModel.toggleBookmark(posts.value[index].id) }) }
                is UiState.Offline -> items(posts.value.size) { index -> PostCard(posts.value[index], { viewModel.toggleLike(posts.value[index].id) }, { viewModel.toggleBookmark(posts.value[index].id) }) }
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
        onPublish = { body, topic, anonymous -> viewModel.publishPost(userId, body, topic, anonymous) { composing = false } },
    )
}

@Composable
private fun PostCard(post: CommunityPost, onLike: () -> Unit, onBookmark: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(SpectraColors.Cyan, SpectraColors.Violet)), CircleShape), contentAlignment = Alignment.Center) { BrandMark(Modifier.size(30.dp), Color.White) }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) { Text(post.author, style = MaterialTheme.typography.titleMedium); Text(remoteTime(post.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.52f)) }
                IconButton(onClick = {}) { Icon(Icons.Rounded.MoreHoriz, "更多") }
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
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.ChatBubbleOutline, "评论", Modifier.size(20.dp)); Spacer(Modifier.size(5.dp)); Text(post.comments.toString()) }
                    Icon(Icons.Rounded.BookmarkBorder, "收藏", Modifier.clickable(onClick = onBookmark).padding(vertical = 8.dp).size(20.dp))
                }
            }
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
        onPublish = { title, description, cents, location -> viewModel.publishListing(userId, title, description, cents, location) { composing = false } },
    )
    selected?.let { listing -> ListingDetails(listing, onClose = { selected = null }, onFavorite = { viewModel.toggleFavorite(listing.id) }) }
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
private fun PostComposer(busy: Boolean, onClose: () -> Unit, onPublish: (String, String, Boolean) -> Unit) {
    var body by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var anonymous by rememberSaveable { mutableStateOf(false) }
    FullScreenDialog("发布校园动态", onClose) {
        OutlinedTextField(body, { body = it.take(5000) }, Modifier.fillMaxWidth().height(220.dp), label = { Text("想分享什么？") })
        OutlinedTextField(topic, { topic = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("话题（可选）") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(anonymous, { anonymous = it }); Text("匿名发布；不同帖子不会建立身份关联") }
        Text("发布后会进入审核流程；保存不会被成就动画阻塞。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        SpectraPrimaryButton(if (busy) "正在保存" else "确认发布", { onPublish(body, topic, anonymous) }, Modifier.fillMaxWidth(), enabled = body.isNotBlank() && !busy)
    }
}

@Composable
private fun ListingComposer(busy: Boolean, onClose: () -> Unit, onPublish: (String, String, Int, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    val cents = price.toBigDecimalOrNull()?.movePointRight(2)?.toInt()
    FullScreenDialog("发布商品", onClose) {
        OutlinedTextField(title, { title = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("商品名称") }, singleLine = true)
        OutlinedTextField(description, { description = it.take(2000) }, Modifier.fillMaxWidth().height(160.dp), label = { Text("描述") })
        OutlinedTextField(price, { price = it.filter { char -> char.isDigit() || char == '.' }.take(10) }, Modifier.fillMaxWidth(), label = { Text("价格（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(location, { location = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("交易地点") }, singleLine = true)
        Text("商品将先进入审核；审核通过前只有你和管理员可见。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        SpectraPrimaryButton(if (busy) "正在保存" else "提交商品", { onPublish(title, description, cents ?: 0, location) }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && cents != null && cents >= 0 && !busy)
    }
}

@Composable
private fun ListingDetails(listing: MarketplaceListing, onClose: () -> Unit, onFavorite: () -> Unit) {
    FullScreenDialog(listing.title, onClose) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(SpectraColors.Cyan.copy(.6f), SpectraColors.Violet.copy(.56f)))), contentAlignment = Alignment.Center) {
            if (listing.mediaUrl.isNotBlank()) AsyncImage(listing.mediaUrl, "商品图片", Modifier.fillMaxSize()) else BrandMark(Modifier.size(96.dp), Color.White)
        }
        Text("¥${"%.2f".format(listing.priceCents / 100.0)}", fontFamily = Tomorrow, style = MaterialTheme.typography.headlineLarge)
        Text(listing.description.ifBlank { "卖家暂未填写更多描述。" }, style = MaterialTheme.typography.bodyLarge)
        Text(listOf(listing.seller, listing.location, statusText(listing.status)).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TelemetryChip("收藏", false, onFavorite, Modifier.weight(1f))
            TelemetryChip("联系卖家", false, {}, Modifier.weight(1f))
        }
        SpectraPrimaryButton("购买", {}, Modifier.fillMaxWidth(), enabled = listing.status == "active")
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
