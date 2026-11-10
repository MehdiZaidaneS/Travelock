package com.example.travelock.data.repository

import com.example.travelock.data.model.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDestinationRepositoryTest {
    private fun destination(id: String, latitude: Double, longitude: Double = 0.0) = Destination(
        id,"location",id,"Test",0f,latitude,longitude,"","TEST",
    )

    @Test
    fun findsNearestAcrossShortServerPagesAndBreaksTiesById() = runBlocking {
        val rows = listOf(destination("far",2.0),destination("b",0.0),destination("a",0.0))
        val offsets = mutableListOf<Long>()
        val repository = NearbyDestinationRepository { offset, _ ->
            offsets += offset
            rows.drop(offset.toInt()).take(1) // Server cap smaller than requested page.
        }
        val result = repository.getNearbyDestinations(0.0,0.0,2)
        assertEquals(listOf("a","b"),result.map { it.destination.destinationId })
        assertTrue(result.all { it.distanceMeters == 0.0 })
        assertEquals(listOf(0L,1L,2L,3L),offsets)
    }

    @Test
    fun refreshingUsesNewCoordinatesAndCatalogueValues() = runBlocking {
        var rows = listOf(destination("south",0.0),destination("north",1.0))
        val repository = NearbyDestinationRepository { offset, _ -> if (offset == 0L) rows else emptyList() }
        assertEquals("south",repository.getNearbyDestinations(0.0,0.0,1).single().destination.destinationId)
        assertEquals("north",repository.getNearbyDestinations(1.0,0.0,1).single().destination.destinationId)
        rows = listOf(destination("new",1.0))
        assertEquals("new",repository.getNearbyDestinations(1.0,0.0,1).single().destination.destinationId)
    }

    @Test
    fun skipsInvalidCatalogueCoordinatesAndHandlesEmptyCatalogue() = runBlocking {
        val rows = listOf(destination("nan",Double.NaN),destination("bad",91.0),
            destination("infinity",0.0,Double.POSITIVE_INFINITY),destination("ok",0.0))
        val repository = NearbyDestinationRepository { offset, _ -> if (offset == 0L) rows else emptyList() }
        assertEquals(listOf("ok"),repository.getNearbyDestinations(0.0,0.0).map { it.destination.destinationId })
        assertTrue(NearbyDestinationRepository { _, _ -> emptyList() }.getNearbyDestinations(0.0,0.0).isEmpty())
    }

    @Test
    fun validatesInputsBeforeFetching() {
        val repository = NearbyDestinationRepository { _, _ -> error("Must not fetch invalid input") }
        for ((lat,lon,limit) in listOf(Triple(Double.NaN,0.0,20),Triple(0.0,181.0,20),
            Triple(0.0,0.0,0),Triple(0.0,0.0,101))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.getNearbyDestinations(lat,lon,limit) } }
        }
    }

    @Test
    fun failureOnLaterPageDoesNotReturnPartialRecommendations() {
        val repository = NearbyDestinationRepository { offset, _ ->
            if (offset == 0L) listOf(destination("one",0.0)) else throw IllegalStateException("Network failure")
        }
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.getNearbyDestinations(0.0,0.0) } }
        val cancelled = NearbyDestinationRepository { _, _ -> throw CancellationException("Cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { cancelled.getNearbyDestinations(0.0,0.0) } }
    }
}
