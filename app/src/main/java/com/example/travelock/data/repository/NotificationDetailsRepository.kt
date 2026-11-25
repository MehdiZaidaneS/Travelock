package com.example.travelock.data.repository

import com.example.travelock.data.model.Notification
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.ProfileSummary
import kotlinx.coroutines.CancellationException

data class NotificationInboxBundle(
    val notifications: List<Notification>,
    val senders: Map<String, ProfileSummary?>,
    val unreadCount: Int,
)

/**
 * Composes live NotificationRepository, ProfileRepository, FriendshipRepository, and PostRepository
 * for the Notifications inbox without modifying any existing backend repositories.
 */
class NotificationDetailsRepository(
    private val notificationRepository: NotificationRepository = NotificationRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val friendshipRepository: FriendshipRepository = FriendshipRepository(),
    private val postRepository: PostRepository = PostRepository(),
) {
    private val senderCache = mutableMapOf<String, ProfileSummary?>()

    fun clearCache() {
        senderCache.clear()
    }

    suspend fun loadInbox(limit: Int = 50): NotificationInboxBundle {
        val notifications = notificationRepository.getNotifications(limit)
        val unreadCount = try {
            notificationRepository.getUnreadCount().toInt().coerceAtLeast(0)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            notifications.count { !it.isRead }
        }

        val senders = mutableMapOf<String, ProfileSummary?>()
        val distinctSenderIds = notifications.map { it.senderId }.distinct()
        for (senderId in distinctSenderIds) {
            senders[senderId] = resolveSenderSummary(senderId)
        }

        return NotificationInboxBundle(
            notifications = notifications,
            senders = senders,
            unreadCount = unreadCount,
        )
    }

    suspend fun getUnreadCount(): Int =
        notificationRepository.getUnreadCount().toInt().coerceAtLeast(0)

    suspend fun markAsRead(notificationId: String): Notification? =
        notificationRepository.markAsRead(notificationId)

    suspend fun markAllAsRead() {
        notificationRepository.markAllAsRead()
    }

    suspend fun acceptFriendRequest(friendshipId: String) {
        friendshipRepository.acceptFriendRequest(friendshipId)
    }

    suspend fun declineFriendRequest(friendshipId: String) {
        friendshipRepository.rejectFriendRequest(friendshipId)
    }

    suspend fun loadPostForNotification(postId: String): Post? {
        if (postId.isBlank()) return null
        return try {
            postRepository.getMyPost(postId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveSenderSummary(senderId: String): ProfileSummary? {
        if (senderId.isBlank()) return null
        if (senderCache.containsKey(senderId)) {
            return senderCache[senderId]
        }
        val summary = try {
            profileRepository.getProfileSummary(senderId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (summary != null) {
            senderCache[senderId] = summary
        }
        return summary
    }
}
