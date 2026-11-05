package com.example.travelock.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Destination(
    @SerialName("destination_id")
    val destinationId: String,
    @SerialName("location_id")
    val locationId: String,
    val name: String,
    val description: String,
    // Percentage: 10 means 10% off.
    val discount: Float,
    val latitude: Double,
    val longitude: Double,
    val image: String,
    @SerialName("qr_code")
    val qrCode: String,
)
