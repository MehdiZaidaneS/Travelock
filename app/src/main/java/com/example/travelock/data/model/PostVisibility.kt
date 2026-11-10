package com.example.travelock.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PostVisibility {
    @SerialName("friends") FRIENDS,
    @SerialName("public") PUBLIC,
}
