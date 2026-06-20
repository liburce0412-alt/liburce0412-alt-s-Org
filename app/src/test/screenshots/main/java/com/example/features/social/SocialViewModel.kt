package com.example.features.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.CampusDao
import com.example.core.database.ChatMessageEntity
import com.example.core.database.FriendEntity
import com.example.core.database.UserMessageEntity
import com.example.core.model.ChatMessage
import com.example.core.model.Friend
import com.example.core.model.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SocialViewModel(private val dao: CampusDao) : ViewModel() {

    // 1. Reactive Friends List Flow
    val friends: StateFlow<List<Friend>> = dao.getAllFriendsFlow()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 2. Message Board comments / User wall postings
    val wallMessages: StateFlow<List<UserMessage>> = dao.getAllMessagesFlow()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeChatFriendId = MutableStateFlow<String?>(null)
    val activeChatFriendId: StateFlow<String?> = _activeChatFriendId.asStateFlow()

    // 3. Current active conversation chat log
    private val _conversationMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationMessages: StateFlow<List<ChatMessage>> = _conversationMessages.asStateFlow()

    init {
        // Automatically listen to messages if an active chat session buddy changes
        viewModelScope.launch {
            _activeChatFriendId.collect { friendId ->
                if (friendId != null) {
                    dao.getMessagesForFriendFlow(friendId).collect { list ->
                        _conversationMessages.value = list.map { it.toDomain() }
                        // Automatically mark messages from this friend as read
                        dao.markMessagesAsRead(friendId)
                    }
                } else {
                    _conversationMessages.value = emptyList()
                }
            }
        }
    }

    fun selectConversation(friendId: String?) {
        _activeChatFriendId.value = friendId
    }

    // Send chat text message (simulates simple chatbot reply for incredible out-of-the-box experience!)
    fun sendMessage(friendId: String, content: String) {
        viewModelScope.launch {
            val userMsg = ChatMessage(
                friendId = friendId,
                senderId = "local_user",
                content = content,
                timestamp = System.currentTimeMillis()
            )
            dao.insertChatMessage(ChatMessageEntity.fromDomain(userMsg))

            // Trigger simulated smart reply from friend to make chat extremely realistic!
            launch {
                kotlinx.coroutines.delay(1000)
                val responseContent = when {
                    content.contains("你好", true) -> "同学你好呀！最近在自习室复习得怎么样了？"
                    content.contains("学", true) -> "是的呀，我也准备去考研自习区，等放学一起占座啊！"
                    content.contains("二手", true) || content.contains("买", true) -> "哎我正想问你，在交易市场卖的那个 iPad，价格还能再优惠一些吗？"
                    else -> "哈哈，收到啦！等下午写完代码，我们在操场见面聊！🚀"
                }
                val friendMsg = ChatMessage(
                    friendId = friendId,
                    senderId = friendId,
                    content = responseContent,
                    timestamp = System.currentTimeMillis()
                )
                dao.insertChatMessage(ChatMessageEntity.fromDomain(friendMsg))
            }
        }
    }

    // Friend operations
    fun addFriend(id: String, nickname: String, avatarUrl: String, bio: String) {
        viewModelScope.launch {
            val pendingFriend = Friend(id, nickname, avatarUrl, bio, "pending")
            dao.insertFriend(FriendEntity.fromDomain(pendingFriend))
        }
    }

    fun acceptFriendRequest(id: String) {
        viewModelScope.launch {
            val f = friends.value.firstOrNull { it.id == id }
            if (f != null) {
                dao.insertFriend(FriendEntity.fromDomain(f.copy(status = "friend")))
            }
        }
    }

    fun removeFriend(id: String) {
        viewModelScope.launch {
            dao.deleteFriendById(id)
        }
    }

    // User message board operations
    fun postMessageToWall(content: String, authorName: String) {
        viewModelScope.launch {
            val msg = UserMessage(
                profileUserId = "target_wall",
                authorName = authorName,
                authorAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&q=80&w=150",
                content = content,
                timestamp = System.currentTimeMillis(),
                isApproved = true // Default visible
            )
            dao.insertUserMessage(UserMessageEntity.fromDomain(msg))
        }
    }

    fun deleteWallMessage(id: Int) {
        viewModelScope.launch {
            dao.deleteUserMessageById(id)
        }
    }

    fun setWallMessageApproval(id: Int, isApproved: Boolean) {
        viewModelScope.launch {
            dao.updateUserMessageApproval(id, isApproved)
        }
    }

    // Seeding sample friends for high fidelity demo on start
    fun seedSampleFriendsIfEmpty() {
        viewModelScope.launch {
            dao.getAllFriendsFlow().map { it.size }.collect { count ->
                if (count == 0) {
                    val sampleFriends = listOf(
                        FriendEntity(
                            id = "friend_xiaoming",
                            nickname = "小明 同学",
                            avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=150",
                            bio = "努力学习 Kotlin 二刷 Leetcode 的菜鸡，求带！",
                            status = "friend"
                        ),
                        FriendEntity(
                            id = "friend_xiaohong",
                            nickname = "小红 学姐",
                            avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=150",
                            bio = "考研复习中，主攻高等数学。专注、极简、自律者。",
                            status = "pending" // Will show up as friend request in companion UI!
                        ),
                        FriendEntity(
                            id = "friend_laoli",
                            nickname = "老李 班长",
                            avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&q=80&w=150",
                            bio = "今天也是在自习室量化管理时间的一天！有什么活动我通知大家。",
                            status = "friend"
                        )
                    )
                    sampleFriends.forEach { dao.insertFriend(it) }

                    // Add initial sample messages on chat backlog
                    val sampleChats = listOf(
                        ChatMessageEntity(
                            friendId = "friend_xiaoming",
                            senderId = "friend_xiaoming",
                            content = "哈喽队友！今天去哪个自习室自习呀？",
                            timestamp = System.currentTimeMillis() - 7200000,
                            isRead = false
                        ),
                        ChatMessageEntity(
                            friendId = "friend_xiaoming",
                            senderId = "local_user",
                            content = "打算去五教302，听说那里空调比较足，环境很安静。",
                            timestamp = System.currentTimeMillis() - 3600000,
                            isRead = true
                        ),
                        ChatMessageEntity(
                            friendId = "friend_laoli",
                            senderId = "friend_laoli",
                            content = "班级周报AI报告生成啦，注意查看时间分配！",
                            timestamp = System.currentTimeMillis() - 15000000,
                            isRead = true
                        )
                    )
                    sampleChats.forEach { dao.insertChatMessage(it) }

                    // Initial message board seed
                    val sampleWalls = listOf(
                        UserMessageEntity(
                            authorName = "学霸张",
                            authorAvatar = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&q=80&w=150",
                            content = "欢迎加入 CampusAI 校群！互相鼓励，坚持量化学习！🌟",
                            timestamp = System.currentTimeMillis() - 86450000,
                            isApproved = true,
                            profileUserId = "target_wall"
                        ),
                        UserMessageEntity(
                            authorName = "打招呼萌新",
                            authorAvatar = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&q=80&w=150",
                            content = "二手板块有出近代物理教材的学生吗？等一手交易！📚",
                            timestamp = System.currentTimeMillis() - 40000000,
                            isApproved = true,
                            profileUserId = "target_wall"
                        )
                    )
                    sampleWalls.forEach { dao.insertUserMessage(it) }
                }
            }
        }
    }
}

class SocialViewModelFactory(private val dao: CampusDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocialViewModel::class.java)) {
            return SocialViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
