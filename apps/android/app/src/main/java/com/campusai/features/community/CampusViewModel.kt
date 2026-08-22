package com.campusai.features.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusai.core.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CampusRemoteState(
    val posts: UiState<List<CommunityPost>> = UiState.Loading,
    val listings: UiState<List<MarketplaceListing>> = UiState.Loading,
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

    fun setSignedIn(signedIn: Boolean) {
        if (!signedIn) {
            _state.value = CampusRemoteState(
                posts = UiState.Error("登录后可读取和发布校园动态。", false),
                listings = UiState.Error("登录后可查看校园市场。", false),
                comments = UiState.Empty,
                conversations = UiState.Error("登录后可查看消息。", false),
                messages = UiState.Empty,
                orders = UiState.Error("登录后可查看订单。", false),
            )
        } else {
            refreshPosts()
            refreshListings()
            refreshConversations()
            refreshOrders()
        }
    }

    fun refreshPosts() = viewModelScope.launch {
        _state.value = _state.value.copy(posts = UiState.Loading)
        _state.value = _state.value.copy(posts = repository.loadPosts().fold(
            onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
            onFailure = { UiState.Error(it.message ?: "校园动态读取失败。") },
        ))
    }

    fun refreshListings() = viewModelScope.launch {
        _state.value = _state.value.copy(listings = UiState.Loading)
        _state.value = _state.value.copy(listings = repository.loadListings().fold(
            onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Data(it) },
            onFailure = { UiState.Error(it.message ?: "校园市场读取失败。") },
        ))
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

    fun toggleLike(postId: String) = runOperation {
        val (liked, count) = repository.togglePostLike(postId).getOrThrow()
        val current = (_state.value.posts as? UiState.Data)?.value ?: return@runOperation
        _state.value = _state.value.copy(posts = UiState.Data(current.map { if (it.id == postId) it.copy(likes = count) else it }))
        @Suppress("UNUSED_VARIABLE") val interactionState = liked
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

    private fun runOperation(block: suspend () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(operationBusy = true, operationError = null)
        runCatching { block() }
            .onFailure { _state.value = _state.value.copy(operationError = it.message ?: "操作没有完成，请重试。") }
        _state.value = _state.value.copy(operationBusy = false)
    }
}
