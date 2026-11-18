
package com.example.travelock.data.repository

import android.location.Location as AndroidLocation
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Location
import io.github.jan.supabase.postgrest.from

class LocationRepository(
    private val destinationRepository: DestinationRepository =
        DestinationRepository()
) {

    private val supabase = SupabaseProvider.client

    // Get one location
    suspend fun getLocation(locationId: String): Location? {
        require(locationId.isNotBlank()) {
            "Location ID cannot be blank."
        }

        return supabase.from("locations")
            .select {
                filter {
                    eq("location_id", locationId)
                }
            }
            .decodeSingleOrNull<Location>()
    }

    // Get all locations
    suspend fun getLocations(): List<Location> {
        return supabase.from("locations")
            .select()
            .decodeList<Location>()
    }

    // Sort locations based on distance from the user's current position
    private fun sortLocationsByDistance(
        locations: List<Location>,
        userLatitude: Double,
        userLongitude: Double
    ): List<Location> {

        return locations.sortedBy { location ->

            val results = FloatArray(1)
            AndroidLocation.distanceBetween(
                userLatitude,
                userLongitude,
                location.latitude,
                location.longitude,
                results
            )
            results[0]
        }
    }

    // Search locations and include their destinations
    suspend fun searchLocations(
        query: String,
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): List<Location> {

        val searchTerm = query.trim()

        val locations = if (searchTerm.isEmpty()) {
            getLocations()
        } else {
            supabase.from("locations")
                .select {
                    filter {
                        ilike("name", "%$searchTerm%")
                    }
                }
                .decodeList<Location>()
        }

        // Include destinations
        val locationsWithDestinations = locations.map { location ->
            location.copy(
                destinations = destinationRepository
                    .getDestinationsByLocation(location.locationId)
            )
        }

        // Sort by distance if coordinates are available
        if (userLatitude != null && userLongitude != null) {
            return sortLocationsByDistance(
                locationsWithDestinations,
                userLatitude,
                userLongitude
            )
        }

        return locationsWithDestinations
    }
}
