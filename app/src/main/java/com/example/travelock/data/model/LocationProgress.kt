package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationProgress(
    @SerialName("location_id") val locationId: String,
    @SerialName("location_name") val locationName: String,
    @SerialName("total_destinations") val totalDestinations: Long,
    @SerialName("verified_destinations") val verifiedDestinations: Long,
    @SerialName("is_complete") val isComplete: Boolean,
    @SerialName("is_earned") val isEarned: Boolean,
    @SerialName("achievement_id") val achievementId: String? = null,
    @SerialName("unlocked_at") @Serializable(with = InstantSerializer::class)
    val unlockedAt: Instant? = null,
    @SerialName("achievement_name") val achievementName: String,
    @SerialName("achievement_description") val achievementDescription: String,
)
