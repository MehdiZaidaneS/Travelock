package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Activity(
    @SerialName("activity_id")
    val activityId: String,
    @SerialName("user_id")
    val userId: String,
    val type: String,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @SerialName("post_id")
    val postId: String? = null,
    @SerialName("destination_id")
    val destinationId: String? = null,
    @SerialName("achievement_id")
    val achievementId: String? = null,
)
