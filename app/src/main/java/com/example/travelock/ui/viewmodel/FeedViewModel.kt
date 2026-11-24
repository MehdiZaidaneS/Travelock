package com.example.travelock.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import com.example.travelock.data.model.ProfileSummary
import com.example.travelock.data.repository.FeedDetailsRepository
import com.example.travelock.data.repository.FriendshipRepository
import com.example.travelock.data.repository.PostInteractionRepository
import com.example.travelock.data.repository.ProfileRepository
import com.example.travelock.ui.screens.feed.FeedCommentItem
import com.example.travelock.ui.screens.feed.FeedFilter
import com.example.travelock.ui.screens.feed.FeedUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val profileRepo = ProfileRepository()

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
    private var feedJob: Job? = null
    private var ownPostToView: String? = null
    private val authorCache = mutableMapOf<String, ProfileSummary?>()

    init {
        loadFeed()
    }

    fun loadFeed() {
        feedJob?.cancel()
        val filter = _uiState.value.feedFilter
        val focused = ownPostToView
        feedJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val posts = feedDetailsRepo.loadFeed(filter, focused)
                _uiState.update { it.copy(isLoading = false, posts = posts) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not load the feed. Check your connection and retry."
                    )
                }
            }
        }
    }

    fun setFeedFilter(filter: FeedFilter) {
        ownPostToView = null
        _uiState.update { it.copy(feedFilter = filter, posts = emptyList()) }
        loadFeed()
    }

    fun showOwnPost(post: Post) {
        ownPostToView = post.postId
        _uiState.update {
            it.copy(
                feedFilter = if (post.visibility == PostVisibility.PUBLIC) FeedFilter.PUBLIC else FeedFilter.FRIENDS,
                posts = emptyList()
            )
        }
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
            } catch (_: CancellationException) {
                // Ignore cancelled search request
            } catch (_: Exception) {
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
                    val nextCount = if (isCurrentlyLiked) {
                        (item.interaction.likeCount - 1).coerceAtLeast(0)
                    } else {
                        item.interaction.likeCount + 1
                    }
                    item.copy(
                        interaction = item.interaction.copy(
                            isLikedByMe = !isCurrentlyLiked,
                            likeCount = nextCount
                        )
                    )
                } else {
                    item
                }
            }
            _uiState.update { it.copy(posts = updatedPosts, errorMessage = null) }

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
            } catch (_: CancellationException) {

            } catch (_: Exception) {
                loadFeed()
            }
        }
    }

    fun addComment(postId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                interactionRepo.addComment(postId, trimmed)
                val freshSummary = interactionRepo.getSummary(postId)
                val freshComments = interactionRepo.getComments(postId).map { comment ->
                    val author = if (authorCache.containsKey(comment.userId)) {
                        authorCache[comment.userId]
                    } else {
                        val fetched = try {
                            profileRepo.getProfileSummary(comment.userId)
                        } catch (_: Exception) {
                            null
                        }
                        authorCache[comment.userId] = fetched
                        fetched
                    }
                    FeedCommentItem(comment = comment, author = author)
                }

                _uiState.update { state ->
                    state.copy(
                        errorMessage = null,
                        posts = state.posts.map { item ->
                            if (item.post.postId == postId) {
                                item.copy(
                                    interaction = freshSummary,
                                    comments = freshComments
                                )
                            } else {
                                item
                            }
                        }
                    )
                }
            } catch (_: CancellationException) {

            } catch (_: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Could not post comment. Check your connection and try again.")
                }
            }
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        viewModelScope.launch {
            try {
                friendshipRepo.sendFriendRequest(targetUserId)
                onSearchQueryChange(_uiState.value.searchQuery)
            } catch (_: CancellationException) {
                // Ignore cancelled request
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Could not save this action. Check your connection and try again.")
                }
            }
        }
    }

    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                friendshipRepo.acceptFriendRequest(requestId)
                onSearchQueryChange(_uiState.value.searchQuery)
            } catch (_: CancellationException) {
                // Ignore cancelled request
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Could not save this action. Check your connection and try again.")
                }
            }
        }
    }
}
