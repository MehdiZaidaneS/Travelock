package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Application user data. Passwords belong to Supabase Auth, not this model. */
@Serializable
data class User(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    val name: String,
    val email: String,
    @SerialName("profile_image")
    val profileImage: String,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val friends: List<User> = emptyList(),
    @SerialName("locked_locations")
    val lockedLocations: List<Location> = emptyList(),
    @SerialName("locked_destinations")
    val lockedDestinations: List<Destination> = emptyList(),
    @SerialName("unlocked_destinations")
    val unlockedDestinations: List<UserUnlock> = emptyList(),
    val posts: List<Post> = emptyList(),
    val notifications: List<Notification> = emptyList(),
    val achievements: List<UserAchievement> = emptyList(),
)
