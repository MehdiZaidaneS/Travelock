package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    @SerialName("notification_id")
    val notificationId: String,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String,
    val type: String,
    val message: String,
    @SerialName("is_read")
    val isRead: Boolean,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    // Friend-request notifications, for example, need not reference a post.
    @SerialName("post_id")
    val postId: String? = null,
    @SerialName("comment_id")
    val commentId: String? = null,
    @SerialName("friendship_id")
    val friendshipId: String? = null,
)
