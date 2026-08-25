package com.campusai.features.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusai.core.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampusRemoteState(
    val posts: UiState<List<CommunityPost>> = UiState.Loading,
    val listings: UiState<List<MarketplaceListing>> = UiState.Empty,
    val listingsRefreshing: Boolean = false,
    val listingsHasSynced: Boolean = false,
    val listingsSyncError: String? = null,
    val comments: UiState<List<CommunityComment>> = UiState.Loading,
    val conversations: UiState<List<ConversationSummary>> = UiState.Loading,
    val messages: UiState<List<CampusMessage>> = UiState.Loading,
    val orders: UiState<List<MarketplaceOrder>> = UiState.Loading,
    val activeConversationId: String? = null,
    val activePostId: String? = null,
    val operationBusy: Boolean = false,
    val operationError: String? = null,
)

class CampusViewModel(private val repository: CampusRepository = CampusRepository()) : ViewModel() {
    private val _state = MutableStateFlow(CampusRemoteState())
    val state: StateFlow<CampusRemoteState> = _state.asStateFlow()
    private val _announcements = MutableStateFlow<UiState<List<CampusAnnouncement>>>(UiState.Loading)
    val announcements: StateFlow<UiState<List<CampusAnnouncement>>> = _announcements.asStateFlow()
    private var currentUserId: String = ""
    private val likingPostIds = mutableSetOf<String>()
    private var listingsJob: Job? = null

    fun setSession(signedIn: Boolean, userId: String) {
        listingsJob?.cancel()
        listingsJob = null
        currentUserId = if (signedIn) userId else ""
        if (!signedIn) {
            _announcements.value = UiState.Error("登录后可查看同步公告。", false)
            _state.value = CampusRemoteState(
                posts = UiState.Error("登录后可读取和发布树洞动态。", false),
                listings = UiState.Error("登录后可查看心愿墙。", false),
                listingsRefreshing = false,
                listingsHasSynced = false,
                listingsSyncError = null,
                comments = UiState.Empty,
                conversations = UiState.Error("登录后可查看消息。", false),
                messages = UiState.Empty,
                orders = UiState.Error("登录后可查看订单。", false),
            )
        } else {
            refreshAnnouncements()
            refreshPosts()
            refreshListings()
            refreshConversations()
            refreshOrders()
        }
    }

    fun setSignedIn(signedIn: Boolean) = setSession(signedIn, currentUserId)

    fun refreshAnnouncements() = viewModelScope.launch {
        _announcements.value = UiState.Loading
        _announcements.value = repository.loadAnnouncements().fold(
            onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
            onFailure = { UiState.Error(it.message ?: "同步公告读取失败。") },
        )
    }

    fun refreshPosts() = viewModelScope.launch {
        _state.value = _state.value.copy(posts = UiState.Loading)
        _state.value = _state.value.copy(posts = repository.loadPosts(currentUserId).fold(
            onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
            onFailure = { UiState.Error(it.message ?: "树洞动态读取失败。") },
        ))
    }

    fun refreshListings(): Job {
        listingsJob?.cancel()
        return viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    listings = keepVisibleListDuringRefresh(current.listings),
                    listingsRefreshing = true,
                    listingsSyncError = null,
                )
            }
            val next = loadListUiState("心愿墙读取失败。"){ repository.loadListings(currentUserId) }
            _state.update { current ->
                current.copy(
                    listings = settleVisibleListAfterRefresh(current.listings, next),
                    listingsRefreshing = false,
                    listingsHasSynced = current.listingsHasSynced || next !is UiState.Error,
                    listingsSyncError = (next as? UiState.Error)?.message,
                )
            }
        }.also { listingsJob = it }
    }

    fun publishPost(userId: String, body: String, topic: String, anonymous: Boolean, image: UploadImage?, onSuccess: () -> Unit) = runOperation {
        repository.publishPost(userId, body, topic, anonymous, image).getOrThrow()
        refreshPosts().join()
        onSuccess()
    }

    fun publishListing(userId: String, title: String, description: String, priceCents: Int, location: String, image: UploadImage?, onSuccess: () -> Unit) = runOperation {
        repository.publishListing(userId, title, description, priceCents, location, image).getOrThrow()
        refreshListings().join()
        onSuccess()
    }

    fun toggleLike(postId: String) {
        if (!likingPostIds.add(postId)) return
        val original = currentPosts().firstOrNull { it.id == postId }
        if (original == null) {
            likingPostIds.remove(postId)
            return
        }
        mapPosts { post ->
            if (post.id == postId) post.copy(
                likes = (post.likes + if (post.likedByMe) -1 else 1).coerceAtLeast(0),
                likedByMe = !post.likedByMe,
            ) else post
        }
        viewModelScope.launch {
            repository.togglePostLike(postId).fold(
                onSuccess = { (liked, count) ->
                    mapPosts { post -> if (post.id == postId) post.copy(likes = count, likedByMe = liked) else post }
                },
                onFailure = { error ->
                    mapPosts { post -> if (post.id == postId) original else post }
                    _state.value = _state.value.copy(operationError = error.message ?: "点赞没有完成，请重试。")
                },
            )
            likingPostIds.remove(postId)
        }
    }

    fun toggleBookmark(postId: String) = runOperation { repository.togglePostBookmark(postId).getOrThrow() }

    fun openPostComments(postId: String) = viewModelScope.launch {
        _state.value = _state.value.copy(activePostId = postId, comments = UiState.Loading)
        _state.value = _state.value.copy(comments = repository.loadComments(postId).fold(
            onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
            onFailure = { UiState.Error(it.message ?: "评论读取失败。") },
        ))
    }

    fun closePostComments() {
        _state.value = _state.value.copy(activePostId = null, comments = UiState.Loading)
    }

    fun publishComment(postId: String, body: String, onSuccess: () -> Unit = {}) = runOperation {
        repository.publishComment(postId, body).getOrThrow()
        openPostComments(postId).join()
        refreshPosts().join()
        onSuccess()
    }

    fun submitReport(userId: String, targetType: String, targetId: String, reason: String, details: String, onSuccess: () -> Unit) = runOperation {
        repository.submitReport(userId, targetType, targetId, reason, details).getOrThrow()
        onSuccess()
    }

    fun toggleFavorite(listingId: String) = runOperation { repository.toggleFavorite(listingId).getOrThrow() }

    fun refreshConversations() = viewModelScope.launch {
        _state.value = _state.value.copy(conversations = UiState.Loading)
        _state.value = _state.value.copy(conversations = repository.loadConversations().fold(
            onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
            onFailure = { UiState.Error(it.message ?: "消息读取失败。") },
        ))
    }

    fun openConversation(otherUserId: String, listingId: String?, onSuccess: (String) -> Unit) = runOperation {
        val conversationId = repository.openConversation(otherUserId, listingId).getOrThrow()
        refreshConversations().join()
        onSuccess(conversationId)
    }

    fun openMessageThread(conversationId: String) = viewModelScope.launch {
        _state.value = _state.value.copy(activeConversationId = conversationId, messages = UiState.Loading)
        _state.value = _state.value.copy(messages = repository.loadMessages(conversationId).fold(
            onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
            onFailure = { UiState.Error(it.message ?: "消息读取失败。") },
        ))
        repository.markConversationRead(conversationId)
        refreshConversations().join()
    }

    fun closeMessageThread() {
        _state.value = _state.value.copy(activeConversationId = null, messages = UiState.Loading)
    }

    fun sendMessage(conversationId: String, body: String, onSuccess: () -> Unit = {}) = runOperation {
        val sent = repository.sendMessage(conversationId, body).getOrThrow()
        val current = when (val messages = _state.value.messages) {
            is UiState.Data -> messages.value
            is UiState.Offline -> messages.value
            else -> emptyList()
        }
        _state.value = _state.value.copy(messages = UiState.Data((current + sent).distinctBy { it.id }))
        repository.markConversationRead(conversationId)
        refreshConversations().join()
        onSuccess()
    }

    fun refreshOrders() = viewModelScope.launch {
        _state.value = _state.value.copy(orders = UiState.Loading)
        _state.value = _state.value.copy(orders = repository.loadOrders().fold(
            onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
            onFailure = { UiState.Error(it.message ?: "订单读取失败。") },
        ))
    }

    fun createOrder(listingId: String, onSuccess: (String) -> Unit) = runOperation {
        val orderId = repository.createOrder(listingId).getOrThrow()
        refreshOrders().join()
        refreshListings().join()
        onSuccess(orderId)
    }

    fun transitionOrder(orderId: String, version: Int, nextStatus: String, onSuccess: () -> Unit = {}) = runOperation {
        repository.transitionOrder(orderId, version, nextStatus).getOrThrow()
        refreshOrders().join()
        refreshListings().join()
        onSuccess()
    }

    fun clearOperationError() { _state.value = _state.value.copy(operationError = null) }

    private fun currentPosts(): List<CommunityPost> = when (val posts = _state.value.posts) {
        is UiState.Data -> posts.value
        is UiState.Offline -> posts.value
        else -> emptyList()
    }

    private fun mapPosts(transform: (CommunityPost) -> CommunityPost) {
        _state.value = _state.value.copy(posts = when (val posts = _state.value.posts) {
            is UiState.Data -> UiState.Data(posts.value.map(transform))
            is UiState.Offline -> UiState.Offline(posts.value.map(transform))
            else -> posts
        })
    }

    private fun runOperation(block: suspend () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(operationBusy = true, operationError = null)
        runCatching { block() }
            .onFailure { _state.value = _state.value.copy(operationError = it.message ?: "操作没有完成，请重试。") }
        _state.value = _state.value.copy(operationBusy = false)
    }
}

internal suspend fun <T> loadListUiState(
    fallbackMessage: String,
    loader: suspend () -> Result<List<T>>,
): UiState<List<T>> = try {
    loader().fold(
        onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
        onFailure = { UiState.Error(it.message ?: fallbackMessage) },
    )
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    UiState.Error(error.message ?: fallbackMessage)
}

internal fun <T> keepVisibleListDuringRefresh(current: UiState<List<T>>): UiState<List<T>> = when (current) {
    UiState.Loading, is UiState.Error -> UiState.Empty
    else -> current
}

internal fun <T> settleVisibleListAfterRefresh(
    current: UiState<List<T>>,
    loaded: UiState<List<T>>,
): UiState<List<T>> = when {
    loaded !is UiState.Error -> loaded
    current is UiState.Data -> UiState.Offline(current.value)
    current is UiState.Offline -> current
    else -> UiState.Empty
}
