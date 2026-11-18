package com.example.travelock.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelock.data.repository.FeedDetailsRepository
import com.example.travelock.data.repository.FriendshipRepository
import com.example.travelock.data.repository.PostInteractionRepository
import com.example.travelock.ui.screens.feed.FeedFilter
import com.example.travelock.ui.screens.feed.FeedUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Implement FeedViewModel for live feed loading, public/friends filter toggle,
 * live user search, and optimistic UI actions (likes, comments, and friend requests).
 */

class FeedViewModel : ViewModel() {
    private val feedDetailsRepo = FeedDetailsRepository()
    private val interactionRepo = PostInteractionRepository()
    private val friendshipRepo = FriendshipRepository()

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val posts = feedDetailsRepo.loadFeed(_uiState.value.feedFilter)
                _uiState.update { it.copy(isLoading = false, posts = posts) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load feed") }
            }
        }
    }

    fun setFeedFilter(filter: FeedFilter) {
        _uiState.update { it.copy(feedFilter = filter) }
        loadFeed()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearchingUsers = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingUsers = true) }
            try {
                val results = feedDetailsRepo.searchUsersWithStatus(query)
                _uiState.update { it.copy(isSearchingUsers = false, searchResults = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingUsers = false) }
            }
        }
    }

    fun toggleLike(postId: String) {
        viewModelScope.launch {
            val currentPosts = _uiState.value.posts
            val targetPost = currentPosts.find { it.post.postId == postId } ?: return@launch
            val isCurrentlyLiked = targetPost.interaction.isLikedByMe

            // Optimistic update
            val updatedPosts = currentPosts.map { item ->
                if (item.post.postId == postId) {
                    item.copy(
                        interaction = item.interaction.copy(
                            isLikedByMe = !isCurrentlyLiked,
                            likeCount = if (isCurrentlyLiked) item.interaction.likeCount - 1 else item.interaction.likeCount + 1
                        )
                    )
                } else item
            }
            _uiState.update { it.copy(posts = updatedPosts) }

            try {
                if (isCurrentlyLiked) {
                    interactionRepo.unlikePost(postId)
                } else {
                    interactionRepo.likePost(postId)
                }
                val freshSummary = interactionRepo.getSummary(postId)
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.map {
                            if (it.post.postId == postId) it.copy(interaction = freshSummary) else it
                        }
                    )
                }
            } catch (e: Exception) {
                loadFeed()
            }
        }
    }

    fun addComment(postId: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try {
                interactionRepo.addComment(postId, content)
                loadFeed()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }
    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                friendshipRepo.acceptFriendRequest(requestId)
                onSearchQueryChange(_uiState.value.searchQuery)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }
    fun sendFriendRequest(targetUserId: String) {
        viewModelScope.launch {
            try {
                friendshipRepo.sendFriendRequest(targetUserId)
                onSearchQueryChange(_uiState.value.searchQuery)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }
}