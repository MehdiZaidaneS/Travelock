package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.NearbyDestination
import com.example.travelock.util.DistanceCalculator
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class NearbyDestinationRepository internal constructor(
    private val loadPage: suspend (offset: Long, size: Int) -> List<Destination>,
) {
    constructor() : this({ offset, size ->
        SupabaseProvider.client.from("destinations").select {
            order("destination_id", Order.ASCENDING)
            range(offset, offset + size - 1)
        }.decodeList<Destination>()
    })

    // Browse before sign-in. Read every page before limiting, otherwise a nearer
    // destination outside the first server page could be missed.
    suspend fun getNearbyDestinations(
        latitude: Double,
        longitude: Double,
        limit: Int = 20,
    ): List<NearbyDestination> {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees." }
        require(limit in 1..100) { "Recommendation limit must be between 1 and 100." }
        val candidates = mutableListOf<Destination>()
        var offset = 0L
        while (true) {
            val page = loadPage(offset, 200)
            if (page.isEmpty()) break
            candidates.addAll(page)
            offset += page.size
        }
        return candidates.distinctBy { it.destinationId }
            .filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
            .map { destination ->
                NearbyDestination(destination, DistanceCalculator.distanceInMeters(
                    latitude, longitude, destination.latitude, destination.longitude,
                ))
            }
            .sortedWith(compareBy<NearbyDestination> { it.distanceMeters }.thenBy { it.destination.destinationId })
            .take(limit)
    }
}
