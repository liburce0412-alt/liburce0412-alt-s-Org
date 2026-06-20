package com.example.features.v2

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.model.SupabaseConversation
import com.example.core.model.SupabaseMessage
import com.example.core.network.SupabaseManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCenterScreen(
    initialPartnerId: String? = null,
    onBack: () -> Unit
) {
    val conversations = SupabaseManager.getConversationsForUser()
    val initialPartner = conversations.find { 
        val pId = if (it.participantA == SupabaseManager.currentUser.value?.id) it.participantB else it.participantA
        pId == initialPartnerId 
    }
    val initName = initialPartner?.partnerName ?: "自学伙伴"
    val initAvatar = initialPartner?.partnerAvatar ?: ""

    var selectedPartnerId by remember { mutableStateOf<String?>(initialPartnerId) }
    var selectedPartnerName by remember { mutableStateOf(initName) }
    var selectedPartnerAvatar by remember { mutableStateOf(initAvatar) }

    AnimatedContent(
        targetState = selectedPartnerId,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width } + fadeOut()
                )
            } else {
                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width } + fadeOut()
                )
            }
        },
        label = "chat_nav"
    ) { partnerId ->
        if (partnerId == null) {
            // Conversations List Screen
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("消息中心", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, "back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            ) { paddingValues ->
                if (conversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Forum, "empty", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("暂无聊天回执", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                            Text("在二手交易拍下商品或社交页私信，即可开启对话！", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        items(conversations) { conv ->
                            val partnerIdRaw = if (conv.participantA == SupabaseManager.currentUser.value?.id) conv.participantB else conv.participantA
                            ConversationItem(
                                conversation = conv,
                                onClick = {
                                    selectedPartnerId = partnerIdRaw
                                    selectedPartnerName = conv.partnerName
                                    selectedPartnerAvatar = conv.partnerAvatar
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Active Messaging bubble Stack details overlay
            ChatDetailView(
                partnerId = partnerId,
                partnerName = selectedPartnerName,
                partnerAvatar = selectedPartnerAvatar,
                onBack = { selectedPartnerId = null }
            )
        }
    }
}

@Composable
fun ConversationItem(
    conversation: SupabaseConversation,
    onClick: () -> Unit
) {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(conversation.updatedAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = conversation.partnerAvatar,
            contentDescription = "avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.partnerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.lastMessage,
                maxLines = 1,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailView(
    partnerId: String,
    partnerName: String,
    partnerAvatar: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allMessagesState by SupabaseManager.messages.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    
    val threadMessages = remember(allMessagesState, partnerId) {
        val user = SupabaseManager.currentUser.value
        if (user == null) emptyList() else {
            allMessagesState.filter {
                (it.senderId == user.id && it.recipientId == partnerId) ||
                (it.senderId == partnerId && it.recipientId == user.id)
            }.sortedBy { it.createdAt }
        }
    }

    var messageText by remember { mutableStateOf("") }
    
    // Auto scroll to bottom when messages list size changes
    LaunchedEffect(threadMessages.size) {
        if (threadMessages.isNotEmpty()) {
            scrollState.animateScrollToItem(threadMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = partnerAvatar,
                            contentDescription = "partner",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(partnerName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("自习效率伙伴 / 正在线上", fontSize = 10.sp, color = Color.Green)
                        }
                    }
                },
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
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Simulate Image upload (Pre-attached list trigger)
                    IconButton(
                        onClick = {
                            val presetPhotoUrl = "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&q=80&w=300"
                            SupabaseManager.sendMessageToPartner(partnerId, "[分享物品图片]", presetPhotoUrl)
                            Toast.makeText(context, "已发送图书实拍缩略图！", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Photo, "attach", tint = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("说点什么... (校内面交易自当安全第一)", fontSize = 13.sp) },
                        maxLines = 2,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field")
                    )

                    Button(
                        onClick = {
                            if (messageText.trim().isEmpty()) return@Button
                            SupabaseManager.sendMessageToPartner(partnerId, messageText.trim())
                            messageText = ""
                        },
                        shape = RoundedCornerShape(50),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        modifier = Modifier.testTag("send_msg_btn")
                    ) {
                        Icon(Icons.Default.Send, "send", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
        ) {
            items(threadMessages) { msg ->
                MessageBubble(message = msg)
            }
        }
    }
}

@Composable
fun MessageBubble(message: SupabaseMessage) {
    val isFromMe = message.senderId == SupabaseManager.currentUser.value?.id
    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val bubbleColor = if (isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isFromMe) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.widthIn(max = 260.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "chat",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(bottom = 6.dp)
                    )
                }
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
