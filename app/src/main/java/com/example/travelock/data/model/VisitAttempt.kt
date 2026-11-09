package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisitAttempt(
    @SerialName("visit_id")
    val visitId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("destination_id")
    val destinationId: String,
    @SerialName("started_at")
    @Serializable(with = InstantSerializer::class)
    val startedAt: Instant,
    @SerialName("completed_at")
    @Serializable(with = InstantSerializer::class)
    val completedAt: Instant? = null,
    @SerialName("photo_path")
    val photoPath: String? = null,
)
