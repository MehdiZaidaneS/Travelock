package com.example.travelock.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The basic fields available when finding or viewing another user.
@Serializable
data class ProfileSummary(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    val name: String,
    @SerialName("profile_image")
    val profileImage: String,
)
