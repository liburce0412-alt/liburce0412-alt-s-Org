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
            )
        } else {
            refreshPosts()
            refreshListings()
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

    fun publishPost(userId: String, body: String, topic: String, anonymous: Boolean, onSuccess: () -> Unit) = runOperation {
        repository.publishPost(userId, body, topic, anonymous).getOrThrow()
        refreshPosts().join()
        onSuccess()
    }

    fun publishListing(userId: String, title: String, description: String, priceCents: Int, location: String, onSuccess: () -> Unit) = runOperation {
        repository.publishListing(userId, title, description, priceCents, location).getOrThrow()
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

    fun toggleFavorite(listingId: String) = runOperation { repository.toggleFavorite(listingId).getOrThrow() }

    fun clearOperationError() { _state.value = _state.value.copy(operationError = null) }

    private fun runOperation(block: suspend () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(operationBusy = true, operationError = null)
        runCatching { block() }
            .onFailure { _state.value = _state.value.copy(operationError = it.message ?: "操作没有完成，请重试。") }
        _state.value = _state.value.copy(operationBusy = false)
    }
}
