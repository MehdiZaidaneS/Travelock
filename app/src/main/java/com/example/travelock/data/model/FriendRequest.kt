package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a friend request between two users.
 * Status transitions: PENDING → ACCEPTED or DECLINED.
 */
@Serializable
data class FriendRequest(
    @SerialName("request_id")
    val requestId: String,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String,
    val status: String, // "pending", "accepted", "declined"
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)

