package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserUnlock(
    @SerialName("unlock_id")
    val unlockId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("destination_id")
    val destinationId: String,
    @SerialName("unlocked_at")
    @Serializable(with = InstantSerializer::class)
    val unlockedAt: Instant,

    val location: String, // String?
)
