package com.example.travelock.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendListItem(
    @SerialName("friendship_id") val friendshipId: String,
    @SerialName("user_id") val userId: String,
    // Null until this friend creates their profile.
    val profile: ProfileSummary?,
)
