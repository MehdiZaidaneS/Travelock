package com.example.travelock.data.model

enum class DestinationMapStatus { SIGN_IN_REQUIRED, LOCKED, UNLOCKED }

data class DestinationMapItem(
    val destination: Destination,
    val status: DestinationMapStatus,
)

// The viewer identifies whose personal status is displayed. Null means guest.
data class DestinationMapData(
    val userId: String?,
    val destinations: List<DestinationMapItem>,
)
