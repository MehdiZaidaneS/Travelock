package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Destination
import io.github.jan.supabase.postgrest.from

class DestinationRepository {
    private val supabase = SupabaseProvider.client

    // A destination may have been removed since the user last viewed the list.
    suspend fun getDestination(destinationId: String): Destination? {
        require(destinationId.isNotBlank()) { "Destination ID cannot be blank." }

        return supabase.from("destinations")
            .select {
                filter {
                    eq("destination_id", destinationId)
                }
            }
            .decodeSingleOrNull<Destination>()
    }

    // Visitors can browse destinations before signing in.
    suspend fun getDestinationsByLocation(locationId: String): List<Destination> {
        require(locationId.isNotBlank()) { "Location ID cannot be blank." }

        return supabase.from("destinations")
            .select {
                filter {
                    eq("location_id", locationId)
                }
            }
            .decodeList<Destination>()
    }
}
