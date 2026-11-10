package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.DestinationMapData
import com.example.travelock.data.model.DestinationMapItem
import com.example.travelock.data.model.DestinationMapStatus
import com.example.travelock.data.model.UserUnlock
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class DestinationMapRepository internal constructor(
    private val currentUserId: suspend () -> String?,
    private val loadDestinations: suspend (locationId: String?, offset: Long, size: Int) -> List<Destination>,
    private val loadUnlocks: suspend (userId: String, offset: Long, size: Int) -> List<UserUnlock>,
) {
    constructor() : this(
        { AuthRepository().getCurrentUser()?.userId },
        { locationId, offset, size ->
            SupabaseProvider.client.from("destinations").select {
                if (locationId != null) filter { eq("location_id", locationId) }
                order("destination_id", Order.ASCENDING)
                range(offset, offset + size - 1)
            }.decodeList<Destination>()
        },
        { userId, offset, size ->
            SupabaseProvider.client.from("user_unlocks").select {
                filter { eq("user_id", userId) }
                order("unlock_id", Order.ASCENDING)
                range(offset, offset + size - 1)
            }.decodeList<UserUnlock>()
        },
    )

    // No GPS permission is needed to browse markers. Refresh after a visit,
    // sign-in/out or catalogue change; this method holds no cached personal data.
    suspend fun getMapData(locationId: String? = null): DestinationMapData {
        require(locationId == null || locationId.isNotBlank()) { "Location ID cannot be blank." }
        val userId = currentUserId()
        val destinations = readAll { offset, size -> loadDestinations(locationId, offset, size) }
        val unlockedIds = if (userId == null) emptySet() else {
            readAll { offset, size -> loadUnlocks(userId, offset, size) }
                .filter { it.userId == userId }.map { it.destinationId }.toSet()
        }
        check(currentUserId() == userId) { "Account changed while loading the map. Refresh it." }
        return DestinationMapData(userId, destinations.distinctBy { it.destinationId }
            .filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
            .sortedBy { it.destinationId }
            .map { destination ->
                DestinationMapItem(destination, when {
                    userId == null -> DestinationMapStatus.SIGN_IN_REQUIRED
                    destination.destinationId in unlockedIds -> DestinationMapStatus.UNLOCKED
                    else -> DestinationMapStatus.LOCKED
                })
            })
    }

    private suspend fun <T> readAll(load: suspend (Long, Int) -> List<T>): List<T> {
        val rows = mutableListOf<T>()
        var offset = 0L
        while (true) {
            val page = load(offset, 200)
            if (page.isEmpty()) return rows
            rows.addAll(page)
            offset += page.size
        }
    }
}
