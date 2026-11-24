package com.example.travelock.ui.screens.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.travelock.data.model.Post
import com.example.travelock.ui.components.PostCard
import com.example.travelock.ui.components.TravelockSearchBar
import com.example.travelock.ui.components.UserAvatarCircle
import com.example.travelock.ui.viewmodel.FeedViewModel

/**
 * Wire FeedScreen to FeedViewModel for live Supabase feed loading,
 * filter switching (Friends vs Public), live user search, and social actions.
 */
@Composable
fun FeedScreen(
    currentUserId: String,
    onOpenUserProfile: (String) -> Unit,
    feedViewModel: FeedViewModel = viewModel(),
    sharedPost: Post? = null,
) {
    val uiState by feedViewModel.uiState.collectAsState()
    var lastLoadedUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUserId, sharedPost?.postId) {
        if (sharedPost != null) {
            feedViewModel.showOwnPost(sharedPost)
            lastLoadedUserId = currentUserId
        } else if (lastLoadedUserId != null && lastLoadedUserId != currentUserId) {
            feedViewModel.loadFeed()
            lastLoadedUserId = currentUserId
        } else {
            lastLoadedUserId = currentUserId
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Filter Chips (Friends vs Public) & Refresh Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.feedFilter == FeedFilter.FRIENDS,
                    onClick = { feedViewModel.setFeedFilter(FeedFilter.FRIENDS) },
                    label = { Text("Friends & you") }
                )
                FilterChip(
                    selected = uiState.feedFilter == FeedFilter.PUBLIC,
                    onClick = { feedViewModel.setFeedFilter(FeedFilter.PUBLIC) },
                    label = { Text("Public Explore") }
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { feedViewModel.loadFeed() },
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh feed"
                    )
                }
            }
        }

        // User Search Bar & Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Connect with Travelers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TravelockSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { feedViewModel.onSearchQueryChange(it) },
                    placeholderText = "Search users by name or @username..."
                )
            }
        }

        // Search Results List
        if (uiState.searchResults.isNotEmpty()) {
            items(uiState.searchResults, key = { "search-${it.profile.userId}" }) { result ->
                val profile = result.profile
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUserProfile(profile.userId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarCircle(name = profile.name, username = profile.username, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${profile.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        when (result.relationshipStatus) {
                            RelationshipStatus.FRIENDS -> {
                                OutlinedButton(onClick = { onOpenUserProfile(profile.userId) }) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text("Friends")
                                }
                            }
                            RelationshipStatus.OUTGOING_PENDING -> {
                                OutlinedButton(onClick = {}, enabled = false) {
                                    Icon(
                                        Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text("Requested")
                                }
                            }
                            RelationshipStatus.INCOMING_PENDING -> {
                                Button(onClick = { result.incomingRequestId?.let { feedViewModel.acceptFriendRequest(it) } }) {
                                    Text("Accept Request")
                                }
                            }
                            RelationshipStatus.NONE -> {
                                Button(onClick = { feedViewModel.sendFriendRequest(profile.userId) }) {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Text("Add Friend")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feed Header & Posts
        item {
            Text(
                text = if (uiState.feedFilter == FeedFilter.FRIENDS) {
                    "Photos from you and your friends"
                } else {
                    "Public Discovery Feed"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        uiState.errorMessage?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = feedViewModel::loadFeed) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.posts.isEmpty()) {
            item {
                Text(
                    text = if (uiState.feedFilter == FeedFilter.FRIENDS) {
                        "No recent photo posts from you or your friends yet."
                    } else {
                        "No public photo posts found."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(uiState.posts, key = { it.post.postId }) { feedPostItem ->
                PostCard(
                    feedPostItem = feedPostItem,
                    currentUserId = currentUserId,
                    onLikeToggle = { feedViewModel.toggleLike(feedPostItem.post.postId) },
                    onAddComment = { content -> feedViewModel.addComment(feedPostItem.post.postId, content) },
                    onAuthorClick = { userId -> onOpenUserProfile(userId) }
                )
            }
        }
    }
}
