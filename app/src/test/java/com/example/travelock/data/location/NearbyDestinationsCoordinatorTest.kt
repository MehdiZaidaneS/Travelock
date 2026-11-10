package com.example.travelock.data.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NearbyDestinationsCoordinatorTest {
    @Test
    fun locationFailuresDoNotFetchCatalogue() = runBlocking {
        for ((fix,expected) in listOf(
            DeviceLocationResult.PermissionRequired to NearbyDestinationsResult.LocationPermissionRequired,
            DeviceLocationResult.ServicesDisabled to NearbyDestinationsResult.LocationServicesDisabled,
            DeviceLocationResult.Unavailable to NearbyDestinationsResult.LocationUnavailable,
        )) {
            val coordinator = NearbyDestinationsCoordinator({ fix },{ _,_,_ -> error("Must not fetch") })
            assertEquals(expected,coordinator.getNearbyDestinations())
        }
    }

    @Test
    fun refreshUsesFreshFixAndPassesCoordinatesAndLimit() = runBlocking {
        var latitude = 60.0
        val calls = mutableListOf<Triple<Double,Double,Int>>()
        val coordinator = NearbyDestinationsCoordinator(
            { DeviceLocationResult.Available(latitude,24.0,500f) }, // Approximate location is useful for browsing.
            { lat,lon,limit -> calls += Triple(lat,lon,limit); emptyList() },
        )
        val first = coordinator.getNearbyDestinations(5) as NearbyDestinationsResult.Ready
        assertEquals(DeviceLocationResult.Available(60.0,24.0,500f),first.location)
        assertEquals(emptyList<Any>(),first.destinations)
        latitude = 61.0
        coordinator.getNearbyDestinations(10)
        assertEquals(listOf(Triple(60.0,24.0,5),Triple(61.0,24.0,10)),calls)
    }

    @Test
    fun networkAndCancellationErrorsAreNotLocationUnavailable() {
        val fix = DeviceLocationResult.Available(60.0,24.0,5f)
        val failed = NearbyDestinationsCoordinator({ fix },{ _,_,_ -> error("Network failure") })
        assertThrows(IllegalStateException::class.java) { runBlocking { failed.getNearbyDestinations() } }
        val cancelled = NearbyDestinationsCoordinator({ throw CancellationException() },{ _,_,_ -> emptyList() })
        assertThrows(CancellationException::class.java) { runBlocking { cancelled.getNearbyDestinations() } }
    }
}
