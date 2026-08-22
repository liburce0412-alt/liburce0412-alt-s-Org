package com.campusai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.campusai.core.designsystem.BrandMark
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SlideConfirm
import com.campusai.core.designsystem.SpectraColors
import com.campusai.core.designsystem.TelemetryChip
import com.campusai.core.designsystem.Tomorrow
import com.campusai.core.model.UiState
import com.campusai.features.community.CampusMessage
import com.campusai.features.community.CampusRemoteState
import com.campusai.features.community.CampusViewModel
import com.campusai.features.community.ConversationSummary
import com.campusai.features.community.MarketplaceOrder
import com.campusai.features.community.allowedOrderTransitions

@Composable
fun MessageCenterScreen(
    state: CampusRemoteState,
    userId: String,
    viewModel: CampusViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.refreshConversations() }
    val activeId = state.activeConversationId
    if (activeId != null) {
        val summary = conversationValues(state.conversations).firstOrNull { it.id == activeId }
        MessageThreadScreen(state, userId, summary, viewModel)
        return
    }

    OverlayPage {
        PageHeader("消息", "与校园交易对象保持清楚、可追溯的沟通", onBack)
        state.operationError?.let { message -> item { InlineOperationError(message, viewModel::clearOperationError) } }
        when (val conversations = state.conversations) {
            UiState.Loading -> items(3) { CommerceLoadingRow() }
            UiState.Empty -> item { CommerceEmpty("还没有消息", "从商品详情联系卖家后，会话会出现在这里。") }
            is UiState.Error -> item { CommerceError(conversations.message, viewModel::refreshConversations) }
            is UiState.Data -> items(conversations.value, key = { it.id }) { summary -> ConversationRow(summary) { viewModel.openMessageThread(summary.id) } }
            is UiState.Offline -> items(conversations.value, key = { it.id }) { summary -> ConversationRow(summary) { viewModel.openMessageThread(summary.id) } }
        }
    }
}

@Composable
private fun ConversationRow(summary: ConversationSummary, onClick: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 16, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(Brush.linearGradient(listOf(SpectraColors.Warm, SpectraColors.Rose, SpectraColors.Violet)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { BrandMark(Modifier.size(32.dp), Color.White) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(summary.otherName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(commerceTime(summary.lastMessageAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                }
                if (summary.listingTitle.isNotBlank()) Text(summary.listingTitle, style = MaterialTheme.typography.labelMedium, color = SpectraColors.Focus, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary.lastMessage.ifBlank { "开始这段对话" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(.62f))
            }
            if (summary.unreadCount > 0) {
                Spacer(Modifier.size(8.dp))
                Box(Modifier.size(24.dp).background(SpectraColors.Rose, CircleShape), contentAlignment = Alignment.Center) {
                    Text(summary.unreadCount.coerceAtMost(99).toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MessageThreadScreen(
    state: CampusRemoteState,
    userId: String,
    summary: ConversationSummary?,
    viewModel: CampusViewModel,
) {
    val conversationId = state.activeConversationId ?: return
    var draft by rememberSaveable(conversationId) { mutableStateOf("") }
    val messages = messageValues(state.messages)
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(.94f)).navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::closeMessageThread) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回消息列表") }
            Column(Modifier.weight(1f)) {
                Text(summary?.otherName ?: "校园会话", style = MaterialTheme.typography.titleLarge)
                if (!summary?.listingTitle.isNullOrBlank()) Text(summary?.listingTitle.orEmpty(), style = MaterialTheme.typography.bodySmall, color = SpectraColors.Focus)
            }
            IconButton(onClick = { viewModel.openMessageThread(conversationId) }) { Icon(Icons.Rounded.Refresh, "刷新消息") }
        }
        state.operationError?.let { Box(Modifier.padding(horizontal = 20.dp)) { InlineOperationError(it, viewModel::clearOperationError) } }
        when (val remote = state.messages) {
            UiState.Loading -> Box(Modifier.weight(1f).padding(20.dp)) { CommerceLoadingRow() }
            UiState.Empty -> Box(Modifier.weight(1f).padding(20.dp), contentAlignment = Alignment.Center) { CommerceEmpty("还没有消息", "发一句清楚的开场白吧。") }
            is UiState.Error -> Box(Modifier.weight(1f).padding(20.dp)) { CommerceError(remote.message) { viewModel.openMessageThread(conversationId) } }
            is UiState.Data, is UiState.Offline -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { message -> MessageBubble(message, message.senderId == userId) }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(4000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
            )
            IconButton(
                onClick = { viewModel.sendMessage(conversationId, draft) { draft = "" } },
                enabled = draft.isNotBlank() && !state.operationBusy,
                modifier = Modifier.size(52.dp).background(SpectraColors.Ink, CircleShape),
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "发送", tint = Color.White) }
        }
    }
}

@Composable
private fun MessageBubble(message: CampusMessage, own: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
        GlassPanel(Modifier.widthIn(max = 310.dp), radius = 16) {
            Column(
                Modifier
                    .background(
                        if (own) Brush.linearGradient(listOf(SpectraColors.Focus.copy(.16f), SpectraColors.Violet.copy(.14f)))
                        else Brush.linearGradient(listOf(SpectraColors.Warm.copy(.16f), SpectraColors.Rose.copy(.12f))),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(message.body, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(commerceTime(message.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.48f))
            }
        }
    }
}

@Composable
fun OrdersScreen(
    state: CampusRemoteState,
    userId: String,
    viewModel: CampusViewModel,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    var pending by remember { mutableStateOf<Pair<MarketplaceOrder, OrderAction>?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshOrders() }
    OverlayPage {
        PageHeader("订单", "交易状态由双方确认，关键变化会留下审计记录", onBack)
        state.operationError?.let { message -> item { InlineOperationError(message, viewModel::clearOperationError) } }
        when (val orders = state.orders) {
            UiState.Loading -> items(3) { CommerceLoadingRow() }
            UiState.Empty -> item { CommerceEmpty("还没有订单", "确认购买商品后，订单进度会出现在这里。") }
            is UiState.Error -> item { CommerceError(orders.message, viewModel::refreshOrders) }
            is UiState.Data -> items(orders.value, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    userId = userId,
                    onContact = {
                        val other = if (userId == order.buyerId) order.sellerId else order.buyerId
                        viewModel.openConversation(other, order.listingId, onOpenConversation)
                    },
                    onAction = { pending = order to it },
                )
            }
            is UiState.Offline -> items(orders.value, key = { it.id }) { order ->
                OrderCard(order, userId, onContact = {}, onAction = {})
            }
        }
    }

    pending?.let { (order, action) ->
        Dialog(onDismissRequest = { if (!state.operationBusy) pending = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(.24f)).padding(20.dp), contentAlignment = Alignment.BottomCenter) {
                GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(action.label, style = MaterialTheme.typography.titleLarge)
                        Text(action.detail, color = MaterialTheme.colorScheme.onSurface.copy(.64f))
                        SlideConfirm(
                            text = if (state.operationBusy) "正在确认…" else "滑动确认",
                            enabled = !state.operationBusy,
                            onConfirm = {
                                viewModel.transitionOrder(order.id, order.version, action.nextStatus) { pending = null }
                            },
                        )
                        TelemetryChip("暂不操作", false, { pending = null }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: MarketplaceOrder,
    userId: String,
    onContact: () -> Unit,
    onAction: (OrderAction) -> Unit,
) {
    val isBuyer = userId == order.buyerId
    val counterpart = if (isBuyer) order.sellerName else order.buyerName
    val actions = orderActions(order, isBuyer)
    GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(SpectraColors.Cyan.copy(.5f), SpectraColors.Violet.copy(.5f)))), contentAlignment = Alignment.Center) {
                    if (order.listingMediaUrl.isNotBlank()) AsyncImage(order.listingMediaUrl, "商品图片", Modifier.fillMaxSize().aspectRatio(1f))
                    else BrandMark(Modifier.size(42.dp), Color.White)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(order.listingTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("¥${"%.2f".format(order.priceCents / 100.0)}", fontFamily = Tomorrow, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                    Text("${if (isBuyer) "卖家" else "买家"} · $counterpart", color = MaterialTheme.colorScheme.onSurface.copy(.58f))
                }
                TelemetryChip(orderStatusText(order.status), order.status !in listOf("cancelled", "disputed"), {})
            }
            OrderProgress(order.status)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TelemetryChip("联系对方", false, onContact, Modifier.weight(1f))
                actions.firstOrNull()?.let { action -> TelemetryChip(action.label, true, { onAction(action) }, Modifier.weight(1f)) }
            }
            if (actions.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.drop(1).forEach { action -> TelemetryChip(action.label, false, { onAction(action) }, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun OrderProgress(status: String) {
    val steps = listOf("pending_payment", "paid", "meeting", "completed")
    val activeIndex = steps.indexOf(status).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, step ->
            val active = status !in listOf("cancelled", "disputed") && index <= activeIndex
            Box(Modifier.size(16.dp).background(if (active) SpectraColors.Focus else SpectraColors.Silver, CircleShape))
            if (index < steps.lastIndex) Spacer(Modifier.weight(1f).height(3.dp).background(if (active && index < activeIndex) SpectraColors.Violet else SpectraColors.Silver.copy(.7f), CircleShape))
        }
    }
}

private data class OrderAction(val label: String, val nextStatus: String, val detail: String)

private fun orderActions(order: MarketplaceOrder, isBuyer: Boolean): List<OrderAction> =
    allowedOrderTransitions(order.status, isBuyer).map { nextStatus ->
        when (nextStatus) {
            "paid" -> OrderAction("确认付款", nextStatus, "仅在已经完成付款后确认；该操作会推进订单状态。")
            "cancelled" -> OrderAction("取消订单", nextStatus, "取消后商品将重新回到在售状态。")
            "meeting" -> OrderAction("进入面交", nextStatus, "确认已经与买家约定并开始线下交付。")
            "completed" -> OrderAction("确认完成", nextStatus, "确认收到商品后，订单将完成且商品标记为已售。")
            else -> OrderAction("发起争议", "disputed", "订单会暂停流转，等待管理员处理。")
        }
    }

@Composable
private fun OverlayPage(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(.94f)),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineLarge); Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(.58f)) }
        }
    }
}

@Composable
private fun CommerceLoadingRow() {
    GlassPanel(Modifier.fillMaxWidth().height(100.dp), radius = 16) {
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp).background(SpectraColors.Silver.copy(.45f), CircleShape)); Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Spacer(Modifier.fillMaxWidth(.5f).height(14.dp).background(SpectraColors.Silver.copy(.5f), CircleShape))
                Spacer(Modifier.fillMaxWidth(.78f).height(11.dp).background(SpectraColors.Silver.copy(.32f), CircleShape))
            }
        }
    }
}

@Composable
private fun CommerceEmpty(title: String, detail: String) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
        Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = SpectraColors.Focus, modifier = Modifier.size(42.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
        }
    }
}

@Composable
private fun CommerceError(message: String, onRetry: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 16) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            TelemetryChip("重新读取", true, onRetry)
        }
    }
}

@Composable
private fun InlineOperationError(message: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error.copy(.1f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Refresh, "关闭错误") }
    }
}

private fun conversationValues(state: UiState<List<ConversationSummary>>): List<ConversationSummary> = when (state) {
    is UiState.Data -> state.value
    is UiState.Offline -> state.value
    else -> emptyList()
}

private fun messageValues(state: UiState<List<CampusMessage>>): List<CampusMessage> = when (state) {
    is UiState.Data -> state.value
    is UiState.Offline -> state.value
    else -> emptyList()
}

private fun commerceTime(value: String): String = value.take(16).replace('T', ' ').ifBlank { "刚刚" }
private fun orderStatusText(value: String): String = when (value) {
    "pending_payment" -> "待付款"
    "paid" -> "已付款"
    "meeting" -> "面交中"
    "completed" -> "已完成"
    "cancelled" -> "已取消"
    "disputed" -> "争议中"
    else -> value
}
