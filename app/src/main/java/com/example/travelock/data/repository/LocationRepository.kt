
package com.example.travelock.data.repository

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

    // Search locations and include their destinations
    suspend fun searchLocations(query: String): List<Location> {

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

        return locations.map { location ->
            location.copy(
                destinations = destinationRepository
                    .getDestinationsByLocation(location.locationId)
            )
        }
    }
}
