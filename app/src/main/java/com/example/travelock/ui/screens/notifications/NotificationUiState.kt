package com.example.travelock.ui.screens.notifications

import com.example.travelock.data.model.Notification
import com.example.travelock.data.model.ProfileSummary

data class NotificationListItem(
    val notification: Notification,
    val sender: ProfileSummary? = null,
    val isActionInFlight: Boolean = false,
) {
    val senderDisplayName: String
        get() = sender?.name?.takeIf { it.isNotBlank() }
            ?: sender?.username?.takeIf { it.isNotBlank() }
            ?: "Traveler"

    val senderUsername: String
        get() = sender?.username.orEmpty()

    val senderProfileImage: String
        get() = sender?.profileImage.orEmpty()

    val isFriendRequest: Boolean
        get() = notification.type == "friend_request" && !notification.friendshipId.isNullOrBlank()

    val isPostNotification: Boolean
        get() = notification.type == "like" || notification.type == "comment"
}

data class NotificationsUiState(
    val userId: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMarkingAllRead: Boolean = false,
    val items: List<NotificationListItem> = emptyList(),
    val unreadCount: Int = 0,
    val inFlightIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
) {
    val hasUnread: Boolean
        get() = unreadCount > 0 || items.any { !it.notification.isRead }
}
