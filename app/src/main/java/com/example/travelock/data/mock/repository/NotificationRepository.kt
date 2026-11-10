package com.example.travelock.data.mock.repository

import com.example.travelock.data.mock.MockSessionState
import com.example.travelock.data.mock.model.FriendRequest
import com.example.travelock.data.model.Notification
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles Notifications (PBI-31 through PBI-35).
 */
class NotificationRepository {

    val notificationsFlow: StateFlow<List<Notification>> = MockSessionState.notifications
    val friendRequestsFlow: StateFlow<List<FriendRequest>> = MockSessionState.friendRequests

    /**
     * PBI-31: Get notifications ordered chronologically (newest first).
     */
    suspend fun getNotifications(): List<Notification> {
        // TODO: SUPABASE - Replace mock notifications query with:
        // supabase.from("notifications")
        //     .select {
        //         filter { eq("receiver_id", currentUserId) }
        //         order("created_at", Order.DESCENDING)
        //     }
        //     .decodeList<Notification>()

        return MockSessionState.notifications.value.sortedByDescending { it.createdAt }
    }

    /**
     * PBI-31: Mark a single notification as read.
     */
    suspend fun markAsRead(notificationId: String) {
        // TODO: SUPABASE - Replace mock call with:
        // supabase.from("notifications").update(buildJsonObject { put("is_read", true) }) {
        //     filter { eq("notification_id", notificationId) }
        // }

        MockSessionState.markNotificationRead(notificationId)
    }

    /**
     * Mark all notifications as read.
     */
    suspend fun markAllAsRead() {
        // TODO: SUPABASE - Replace mock call with:
        // supabase.from("notifications").update(buildJsonObject { put("is_read", true) }) {
        //     filter { eq("receiver_id", currentUserId) }
        // }

        MockSessionState.markAllNotificationsRead()
    }
}

