package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Slim profile projection for displaying friends in the Profile screen's friends list.
 */
@Serializable
data class FriendProfile(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    val name: String,
    @SerialName("profile_image")
    val profileImage: String = "",
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.EPOCH,
) {
    /** Converts to [User] so shared UI components like ProfilePhotoAvatar can render it. */
    fun toUser(): User = User(
        userId = userId,
        username = username,
        name = name,
        email = "",
        profileImage = profileImage,
        createdAt = createdAt,
    )
}
