package com.example.travelock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.travelock.data.model.User
import com.example.travelock.ui.screens.notifications.NotificationListItem
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single Notification Row backed by live Supabase state (T-12, T-13, T-14).
 * - Visually distinguishes unread vs read items.
 * - Shows sender avatar (preset, uploaded photo, or initials fallback), display name,
 *   message/comment preview, and timestamp.
 * - Provides guarded inline Accept / Decline actions for pending friend requests.
 */
@Composable
fun NotificationItemCard(
    item: NotificationListItem,
    onClick: () -> Unit,
    onAcceptFriendRequest: () -> Unit,
    onDeclineFriendRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notification = item.notification
    val senderUser = remember(
        notification.senderId,
        item.senderDisplayName,
        item.senderUsername,
        item.senderProfileImage,
    ) {
        User(
            userId = notification.senderId,
            username = item.senderUsername,
            name = item.senderDisplayName,
            email = "",
            profileImage = item.senderProfileImage,
            createdAt = notification.createdAt,
        )
    }
    val timeLabel = remember(notification.createdAt) {
        formatNotificationTime(notification.createdAt)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isActionInFlight, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProfilePhotoAvatar(
                    user = senderUser,
                    size = 42.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.senderDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.senderUsername.isNotBlank() && item.senderUsername != item.senderDisplayName) {
                            Text(
                                text = "@${item.senderUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (!notification.isRead) "$timeLabel • Unread" else "$timeLabel • Read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!notification.isRead) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            if (item.isFriendRequest) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onAcceptFriendRequest,
                        enabled = !item.isActionInFlight,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (item.isActionInFlight) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Accept")
                        }
                    }
                    OutlinedButton(
                        onClick = onDeclineFriendRequest,
                        enabled = !item.isActionInFlight,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}

private fun formatNotificationTime(createdAt: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(createdAt, now)
    val minutes = duration.toMinutes()
    val hours = duration.toHours()
    val days = duration.toDays()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> DateTimeFormatter.ofPattern("MMM d")
            .withZone(ZoneId.systemDefault())
            .format(createdAt)
    }
}
