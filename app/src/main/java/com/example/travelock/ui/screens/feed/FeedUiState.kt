package com.example.travelock.ui.screens.feed

import com.example.travelock.data.model.Comment
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostInteractionSummary
import com.example.travelock.data.model.ProfileSummary

/**
 * Defines FeedFilter, RelationshipStatus, UserSearchResultItem,
 * FeedCommentItem, FeedPostItem, and FeedUiState for PBI-16, PBI-17, and PBI-19.
 */

enum class FeedFilter { FRIENDS, PUBLIC }

enum class RelationshipStatus { FRIENDS, OUTGOING_PENDING, INCOMING_PENDING, NONE }

data class UserSearchResultItem(
    val profile: ProfileSummary,
    val relationshipStatus: RelationshipStatus,
    val incomingRequestId: String? = null
)

data class FeedCommentItem(
    val comment: Comment,
    val author: ProfileSummary?
)

data class FeedPostItem(
    val post: Post,
    val author: ProfileSummary?,
    val destination: Destination?,
    val interaction: PostInteractionSummary,
    val comments: List<FeedCommentItem>,
    val achievementBadge: String? = null
)

data class FeedUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val feedFilter: FeedFilter = FeedFilter.FRIENDS,
    val posts: List<FeedPostItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchingUsers: Boolean = false,
    val searchResults: List<UserSearchResultItem> = emptyList(),
    val errorMessage: String? = null
)