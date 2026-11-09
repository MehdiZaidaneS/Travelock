package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Location
import io.github.jan.supabase.postgrest.from

class LocationRepository {
    private val supabase = SupabaseProvider.client

    // Locations are public, so browsing does not require a signed-in account.
    // Destinations are loaded separately when the user selects a location.
    suspend fun getLocations(): List<Location> {
        return supabase.from("locations")
            .select()
            .decodeList<Location>()
    }
}
