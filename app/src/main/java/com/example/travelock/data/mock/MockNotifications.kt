package com.example.travelock.data.mock

import com.example.travelock.data.model.FriendRequest
import com.example.travelock.data.model.Notification
import java.time.Instant

/**
 * Sample notifications (PBI-31 to PBI-35) covering likes, comments, friend requests, and achievements.
 */
object MockNotifications {

    val initialFriendRequests: List<FriendRequest> = listOf(
        FriendRequest(
            requestId = "freq-001",
            senderId = MockUsers.osama.userId,
            receiverId = MockUsers.currentUser.userId,
            status = "pending",
            createdAt = Instant.parse("2026-11-08T15:00:00Z")
        )
    )

    val initialNotifications: List<Notification> = listOf(
        Notification(
            notificationId = "notif-001",
            senderId = MockUsers.amir.userId,
            receiverId = MockUsers.currentUser.userId,
            type = "like",
            message = "Amir Noori (@amir_n) liked your photo at Oodi Central Library.",
            isRead = false,
            createdAt = Instant.parse("2026-11-08T16:30:00Z"),
            postId = "post-004"
        ),
        Notification(
            notificationId = "notif-002",
            senderId = MockUsers.jesper.userId,
            receiverId = MockUsers.currentUser.userId,
            type = "comment",
            message = "Jesper Holmström (@jesper_h) commented: \"One of the best study spots in Helsinki!\"",
            isRead = false,
            createdAt = Instant.parse("2026-11-08T15:45:00Z"),
            postId = "post-004"
        ),
        Notification(
            notificationId = "notif-003",
            senderId = MockUsers.osama.userId,
            receiverId = MockUsers.currentUser.userId,
            type = "friend_request",
            message = "Osama Aamer (@osama_a) sent you a friend request.",
            isRead = false,
            createdAt = Instant.parse("2026-11-08T15:00:00Z"),
            postId = null
        ),
        Notification(
            notificationId = "notif-004",
            senderId = MockUsers.amir.userId,
            receiverId = MockUsers.currentUser.userId,
            type = "achievement",
            message = "Amir Noori (@amir_n) unlocked the Suomenlinna Explorer achievement!",
            isRead = true,
            createdAt = Instant.parse("2026-11-07T13:35:00Z"),
            postId = "post-001"
        )
    )
}

