package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Post(
    @SerialName("post_id")
    val postId: String,
    @SerialName("user_id")
    val userId: String,
    val image: String,
    @SerialName("image_description")
    val imageDescription: String,
    @SerialName("destination_id")
    val destinationId: String,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val likes: List<User> = emptyList(),
    val comments: List<Comment> = emptyList(),
)
