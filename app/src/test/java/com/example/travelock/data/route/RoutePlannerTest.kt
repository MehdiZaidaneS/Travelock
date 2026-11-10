package com.example.travelock.data.route

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {
    private fun stop(id: String, longitude: Double) = Destination(id,"city",id,"Test",0f,0.0,longitude,"","TEST")
    private val origin = RoutePoint(0.0,0.0)
    private val oneDegree = 111_195.08

    @Test
    fun totalFollowsSelectedOrderAndIncludesOptionalStart() {
        val stops = listOf(stop("a",1.0),stop("b",3.0),stop("c",2.0))
        val route = RoutePlanner.createRoute(stops,origin)
        assertEquals(stops,route.stops)
        assertEquals(3,route.legs.size)
        assertEquals(oneDegree * 4,route.totalDistanceMeters,1.0)
        assertEquals(oneDegree * 3,RoutePlanner.createRoute(stops).totalDistanceMeters,1.0)
    }

    @Test
    fun addingAndRemovingRecalculateWithoutChangingEarlierPlans() {
        val first = RoutePlanner.createRoute(listOf(stop("a",1.0)),origin)
        val added = RoutePlanner.addStop(first,stop("b",3.0))
        assertEquals(oneDegree,first.totalDistanceMeters,1.0)
        assertEquals(oneDegree * 3,added.totalDistanceMeters,1.0)
        assertEquals(first,RoutePlanner.removeStop(added,"b"))
        assertEquals(added,RoutePlanner.addStop(added,stop("b",3.0)))
        assertEquals(added,RoutePlanner.removeStop(added,"missing"))
    }

    @Test
    fun reorderingUsesFinalZeroBasedIndexAndRecalculatesDistance() {
        val route = RoutePlanner.createRoute(listOf(stop("a",1.0),stop("b",3.0),stop("c",2.0)),origin)
        val moved = RoutePlanner.moveStop(route,"c",1)
        assertEquals(listOf("a","c","b"),moved.stops.map { it.destinationId })
        assertEquals(oneDegree * 3,moved.totalDistanceMeters,1.0)
        assertEquals(listOf("c","b","a"),RoutePlanner.moveStop(moved,"a",2).stops.map { it.destinationId })
        assertEquals(listOf("a","b","c"),route.stops.map { it.destinationId })
    }

    @Test
    fun changingStartRecalculatesAndRemovingLastStopLeavesEmptyDraft() {
        val route = RoutePlanner.createRoute(listOf(stop("a",1.0)),origin)
        assertEquals(0.0,RoutePlanner.withStart(route,RoutePoint(0.0,1.0)).totalDistanceMeters,0.001)
        assertEquals(0.0,RoutePlanner.withStart(route,null).totalDistanceMeters,0.001)
        val empty = RoutePlanner.removeStop(route,"a")
        assertTrue(empty.stops.isEmpty())
        assertTrue(empty.legs.isEmpty())
        assertEquals(0.0,empty.totalDistanceMeters,0.001)
        assertEquals(origin,empty.start)
        assertEquals(empty,RoutePlanner.clearRoute(route))
        assertEquals(route,RoutePlanner.addStop(empty,stop("a",1.0)))
    }

    @Test
    fun rejectsEmptyCreationDuplicatesInvalidPositionsAndInvalidEdits() {
        assertThrows(IllegalArgumentException::class.java) { RoutePlanner.createRoute(emptyList()) }
        val a = stop("a",1.0)
        assertThrows(IllegalArgumentException::class.java) { RoutePlanner.createRoute(listOf(a,a)) }
        for (bad in listOf(a.copy(latitude=Double.NaN),a.copy(longitude=181.0),a.copy(destinationId=""))) {
            assertThrows(IllegalArgumentException::class.java) { RoutePlanner.createRoute(listOf(bad)) }
        }
        assertThrows(IllegalArgumentException::class.java) { RoutePoint(91.0,0.0) }
        val route = RoutePlanner.createRoute(listOf(a))
        assertThrows(IllegalArgumentException::class.java) { RoutePlanner.moveStop(route,"a",1) }
        assertThrows(IllegalArgumentException::class.java) { RoutePlanner.moveStop(route,"missing",0) }
    }

    @Test
    fun snapshotDoesNotRetainTheCallersMutableStopList() {
        val stops = mutableListOf(stop("a",1.0))
        val route = RoutePlanner.createRoute(stops,origin)
        stops.add(stop("b",3.0))
        assertEquals(1,route.stops.size)
        assertEquals(oneDegree,route.totalDistanceMeters,1.0)
    }
}
