package com.example.travelock.data.repository

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.RoutePlan
import com.example.travelock.data.model.RoutePoint
import com.example.travelock.data.route.RoutePlanner

class RoutePlanningRepository internal constructor(
    private val loadDestination: suspend (String) -> Destination?,
) {
    constructor() : this(DestinationRepository()::getDestination)

    // Fetch current real catalogue data in the selected order, including before
    // sign-in. A missing stop fails the request instead of silently shortening it.
    suspend fun planRoute(destinationIds: List<String>, start: RoutePoint? = null): RoutePlan {
        require(destinationIds.isNotEmpty()) { "Select at least one destination." }
        require(destinationIds.all { it.isNotBlank() }) { "Destination ID cannot be blank." }
        require(destinationIds.distinct().size == destinationIds.size) { "Select each destination only once." }
        return RoutePlanner.createRoute(destinationIds.map { load(it) },start)
    }

    suspend fun addStop(route: RoutePlan, destinationId: String): RoutePlan =
        RoutePlanner.addStop(route,load(destinationId))

    private suspend fun load(id: String): Destination {
        require(id.isNotBlank()) { "Destination ID cannot be blank." }
        return requireNotNull(loadDestination(id)) { "A selected destination is no longer available. Refresh the route." }
    }
}
