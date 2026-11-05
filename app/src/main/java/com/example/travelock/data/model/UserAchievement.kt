package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAchievement(
    @SerialName("achievement_id")
    val achievementId: String,
    @SerialName("user_id")
    val userId: String,
    val location: Location,
    val level: Int,
    @SerialName("unlocked_at")
    @Serializable(with = InstantSerializer::class)
    val unlockedAt: Instant,
)
