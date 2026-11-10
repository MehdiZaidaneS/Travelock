package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Friendship(
    @SerialName("friendship_id")
    val friendshipId: String,
    @SerialName("requester_id")
    val requesterId: String,
    @SerialName("recipient_id")
    val recipientId: String,
    val status: String,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @SerialName("responded_at")
    @Serializable(with = InstantSerializer::class)
    val respondedAt: Instant? = null,
)
