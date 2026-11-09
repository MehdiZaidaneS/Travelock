package com.example.travelock.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DistanceCalculator {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    // Approximate distance along the Earth's surface, not a walking or driving route.
    // Call again with updated user coordinates when their location changes.
    fun distanceInMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Double {
        require(startLatitude in -90.0..90.0 && endLatitude in -90.0..90.0) {
            "Latitude must be between -90 and 90 degrees."
        }
        require(startLongitude in -180.0..180.0 && endLongitude in -180.0..180.0) {
            "Longitude must be between -180 and 180 degrees."
        }

        val startLatitudeRadians = Math.toRadians(startLatitude)
        val endLatitudeRadians = Math.toRadians(endLatitude)
        val latitudeDifference = Math.toRadians(endLatitude - startLatitude)
        val longitudeDifference = Math.toRadians(endLongitude - startLongitude)
        val latitudeSine = sin(latitudeDifference / 2)
        val longitudeSine = sin(longitudeDifference / 2)
        val haversine = latitudeSine * latitudeSine +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) * longitudeSine * longitudeSine

        // Floating-point rounding can otherwise put antipodal points outside 0..1.
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}
