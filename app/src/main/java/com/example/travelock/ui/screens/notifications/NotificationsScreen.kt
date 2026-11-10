package com.example.travelock.ui.screens.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.model.Notification
import com.example.travelock.data.mock.repository.NotificationRepository
import com.example.travelock.data.mock.repository.SocialRepository
import com.example.travelock.ui.components.NotificationItemCard
import kotlinx.coroutines.launch

/**
 * PBI-31 to PBI-35: Notifications Screen (Step 22 of Travelock.pdf).
 * - Displays notifications in chronological order (likes, comments, friend requests, achievements).
 * - Allows marking notifications as read and accepting/declining friend requests.
 * - Clicking a notification navigates to the relevant post/feed or friend profile.
 */
@Composable
fun NotificationsScreen(
    onNavigateToFeed: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    notificationRepository: NotificationRepository = remember { NotificationRepository() },
    socialRepository: SocialRepository = remember { SocialRepository() }
) {
    val scope = rememberCoroutineScope()
    val notifications by notificationRepository.notificationsFlow.collectAsState()
    val friendRequests by notificationRepository.friendRequestsFlow.collectAsState()

    fun handleNotificationClick(notification: Notification) {
        scope.launch {
            notificationRepository.markAsRead(notification.notificationId)
        }
        when (notification.type) {
            "like", "comment", "achievement" -> onNavigateToFeed()
            "friend_request" -> onNavigateToUserProfile(notification.senderId)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (notifications.any { !it.isRead }) {
                    TextButton(
                        onClick = {
                            scope.launch { notificationRepository.markAllAsRead() }
                        }
                    ) {
                        Text("Mark all as read")
                    }
                }
            }
        }

        items(notifications, key = { it.notificationId }) { notif ->
            val matchingFriendRequest = if (notif.type == "friend_request") {
                friendRequests.find {
                    it.senderId == notif.senderId && it.status == "pending"
                }
            } else {
                null
            }

            NotificationItemCard(
                notification = notif,
                pendingFriendRequest = matchingFriendRequest,
                onClick = { handleNotificationClick(notif) },
                onAcceptFriendRequest = { reqId ->
                    scope.launch {
                        socialRepository.acceptFriendRequest(reqId)
                        notificationRepository.markAsRead(notif.notificationId)
                    }
                },
                onDeclineFriendRequest = { reqId ->
                    scope.launch {
                        socialRepository.declineFriendRequest(reqId)
                        notificationRepository.markAsRead(notif.notificationId)
                    }
                }
            )
        }
    }
}

