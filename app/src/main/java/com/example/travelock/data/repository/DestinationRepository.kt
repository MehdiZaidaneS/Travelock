package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Destination
import io.github.jan.supabase.postgrest.from

class DestinationRepository {
    private val supabase = SupabaseProvider.client

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
