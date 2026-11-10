package com.example.travelock.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostInteractionSummary(
    @SerialName("like_count") val likeCount: Long,
    @SerialName("comment_count") val commentCount: Long,
    @SerialName("is_liked_by_me") val isLikedByMe: Boolean,
)
