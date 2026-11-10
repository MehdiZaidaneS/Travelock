package com.example.travelock.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FriendTravelProfile(
    val profile: ProfileSummary,
    val achievements: List<UserAchievement>,
    val unlocks: List<UserUnlock>,
)
