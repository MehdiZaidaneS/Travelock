package com.example.travelock.ui.screens.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.data.model.User
import com.example.travelock.data.mock.repository.SocialRepository
import com.example.travelock.ui.components.PostCard
import com.example.travelock.ui.components.TravelockSearchBar
import com.example.travelock.ui.components.UserAvatarCircle
import kotlinx.coroutines.launch

/**
 * PBI-16, PBI-17, PBI-18, PBI-19: Friends Feed & User Search Screen (Steps 2–3 of Travelock.pdf).
 * - Displays chronological feed of friends' destination pictures with likes and comments.
 * - Includes user search bar to find travelers and send friend requests.
 */
@Composable
fun FeedScreen(
    currentUserId: String,
    onOpenUserProfile: (String) -> Unit,
    socialRepository: SocialRepository = remember { SocialRepository() }
) {
    val scope = rememberCoroutineScope()
    val posts by socialRepository.postsFlow.collectAsState()
    val friends by socialRepository.friendsFlow.collectAsState()
    val friendRequests by socialRepository.friendRequestsFlow.collectAsState()

    var userSearchQuery by remember { mutableStateOf("") }
    var searchedUsers by remember { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(userSearchQuery) {
        searchedUsers = if (userSearchQuery.isBlank()) {
            // Show suggested travelers to connect with (Step 3 of Travelock.pdf)
            listOf(MockUsers.sanna, MockUsers.mikko)
        } else {
            socialRepository.searchUsers(userSearchQuery)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Search Bar (PBI-17, Step 3)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Connect with Travelers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TravelockSearchBar(
                    query = userSearchQuery,
                    onQueryChange = { userSearchQuery = it },
                    placeholderText = "Search users by name or @username..."
                )
            }
        }

        // Matching / Suggested Users to Send Friend Requests
        items(searchedUsers, key = { "user-${it.userId}" }) { user ->
            val isAlreadyFriend = friends.any { it.userId == user.userId }
            val hasPendingRequest = friendRequests.any {
                ((it.senderId == currentUserId && it.receiverId == user.userId) ||
                    (it.senderId == user.userId && it.receiverId == currentUserId)) &&
                    it.status == "pending"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUserProfile(user.userId) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatarCircle(name = user.name, username = user.username, size = 40.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${user.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    when {
                        isAlreadyFriend -> {
                            OutlinedButton(onClick = { onOpenUserProfile(user.userId) }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text("Friends")
                            }
                        }
                        hasPendingRequest -> {
                            OutlinedButton(onClick = {}, enabled = false) {
                                Text("Requested")
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    scope.launch {
                                        socialRepository.sendFriendRequest(user.userId)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
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

        // Friends Feed Header (PBI-16, Step 2)
        item {
            Text(
                text = "Recent Friends Activity & Photos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(posts, key = { it.postId }) { post ->
            PostCard(
                post = post,
                currentUserId = currentUserId,
                onLikeToggle = {
                    scope.launch { socialRepository.toggleLike(post.postId) }
                },
                onAddComment = { comment ->
                    scope.launch { socialRepository.addComment(post.postId, comment) }
                },
                onAuthorClick = { userId -> onOpenUserProfile(userId) }
            )
        }
    }
}

