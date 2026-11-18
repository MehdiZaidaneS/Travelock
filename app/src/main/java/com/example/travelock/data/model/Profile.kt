package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    val name: String,
    @SerialName("profile_image")
    // Empty, an owned Storage path, or a bundled preset:moods_01 through _12 ID.
    val profileImage: String = "",
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)
