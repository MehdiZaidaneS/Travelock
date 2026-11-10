package com.example.travelock.data.model

data class RoutePoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees." }
    }
}

data class RouteLeg(val from: RoutePoint, val to: RoutePoint, val distanceMeters: Double)

// Derived app state, not a saved Supabase route or walking-directions response.
@ConsistentCopyVisibility
data class RoutePlan internal constructor(
    val stops: List<Destination>,
    val start: RoutePoint?,
    val legs: List<RouteLeg>,
    val totalDistanceMeters: Double,
)
