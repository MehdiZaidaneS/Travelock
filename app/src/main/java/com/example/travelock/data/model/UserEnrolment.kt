package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserEnrolment(
    @SerialName("enrolment_id")
    val enrolmentId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("destination_id")
    val destinationId: String,
    @SerialName("enrolled_at")
    @Serializable(with = InstantSerializer::class)
    val enrolledAt: Instant,
    // Filled when the query includes the related destination.
    val destination: Destination? = null,
)
