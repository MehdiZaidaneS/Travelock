package com.example.travelock.data.route

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.RouteLeg
import com.example.travelock.data.model.RoutePlan
import com.example.travelock.data.model.RoutePoint
import com.example.travelock.util.DistanceCalculator

object RoutePlanner {
    fun createRoute(stops: List<Destination>, start: RoutePoint? = null): RoutePlan {
        require(stops.isNotEmpty()) { "Select at least one destination." }
        return build(stops,start)
    }

    // Repeated taps do not add the same destination twice.
    fun addStop(route: RoutePlan, destination: Destination): RoutePlan {
        validate(destination)
        if (route.stops.any { it.destinationId == destination.destinationId }) return route
        return build(route.stops + destination,route.start)
    }

    // Removing the last stop leaves an empty draft for the screen to display.
    fun removeStop(route: RoutePlan, destinationId: String): RoutePlan {
        require(destinationId.isNotBlank()) { "Destination ID cannot be blank." }
        return build(route.stops.filterNot { it.destinationId == destinationId },route.start)
    }

    fun moveStop(route: RoutePlan, destinationId: String, toIndex: Int): RoutePlan {
        require(toIndex in route.stops.indices) { "Stop position is outside the route." }
        val fromIndex = route.stops.indexOfFirst { it.destinationId == destinationId }
        require(fromIndex >= 0) { "Destination is not in the route." }
        val reordered = route.stops.toMutableList()
        val stop = reordered.removeAt(fromIndex)
        reordered.add(toIndex,stop)
        return build(reordered,route.start)
    }

    fun withStart(route: RoutePlan, start: RoutePoint?): RoutePlan = build(route.stops,start)

    fun clearRoute(route: RoutePlan): RoutePlan = build(emptyList(),route.start)

    private fun validate(destination: Destination) {
        require(destination.destinationId.isNotBlank()) { "Destination ID cannot be blank." }
        RoutePoint(destination.latitude,destination.longitude)
    }

    private fun build(stops: List<Destination>, start: RoutePoint?): RoutePlan {
        stops.forEach(::validate)
        require(stops.map { it.destinationId }.distinct().size == stops.size) { "Select each destination only once." }
        val points = listOfNotNull(start) + stops.map { RoutePoint(it.latitude,it.longitude) }
        // An empty draft has no legs, even when a starting point is retained.
        val legs = if (stops.isEmpty()) emptyList() else points.zipWithNext { from,to ->
            RouteLeg(from,to,DistanceCalculator.distanceInMeters(from.latitude,from.longitude,to.latitude,to.longitude))
        }
        return RoutePlan(stops.toList(),start,legs,legs.sumOf { it.distanceMeters })
    }
}
