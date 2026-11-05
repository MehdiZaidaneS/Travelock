package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    @SerialName("comment_id")
    val commentId: String,
    @SerialName("post_id")
    val postId: String,
    @SerialName("user_id")
    val userId: String,
    val content: String,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)
