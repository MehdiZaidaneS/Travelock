package com.example.travelock.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Location(
    @SerialName("location_id")
    val locationId: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val image: String,
    val destinations: List<Destination> = emptyList(),
)
