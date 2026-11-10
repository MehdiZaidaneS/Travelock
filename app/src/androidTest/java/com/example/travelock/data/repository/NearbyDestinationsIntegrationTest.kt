package com.example.travelock.data.repository

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.location.DeviceLocationResult
import com.example.travelock.data.location.NearbyDestinationsCoordinator
import com.example.travelock.data.location.NearbyDestinationsResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyDestinationsIntegrationTest {
    // Reads the real public catalogue with controlled coordinates, without GPS
    // permissions or database writes. Device GPS has its own provider test.
    @Test
    fun ranksPublicCatalogueUsingTheLocationFixWithoutSigningIn() = runBlocking {
        val destinationId = requireNotNull(InstrumentationRegistry.getArguments().getString("supabaseTestDestinationId")) {
            "Add the local supabaseTestDestinationId fixture argument."
        }
        val auth = AuthRepository()
        try {
            auth.logout()
            val fixture = checkNotNull(DestinationRepository().getDestination(destinationId))
            val repository = NearbyDestinationRepository()
            val fix = DeviceLocationResult.Available(fixture.latitude,fixture.longitude,5f)
            val coordinator = NearbyDestinationsCoordinator({ fix },repository::getNearbyDestinations)
            val ready = coordinator.getNearbyDestinations(100) as NearbyDestinationsResult.Ready
            assertEquals(fix,ready.location)
            assertTrue(ready.destinations.isNotEmpty())
            assertTrue(ready.destinations.size <= 100)
            assertEquals(0.0,ready.destinations.first().distanceMeters,0.001)
            assertEquals(ready.destinations.map { it.destination.destinationId }.distinct().size,ready.destinations.size)
            for (pair in ready.destinations.zipWithNext()) {
                assertTrue(pair.first.distanceMeters <= pair.second.distanceMeters)
                if (pair.first.distanceMeters == pair.second.distanceMeters) {
                    assertTrue(pair.first.destination.destinationId < pair.second.destination.destinationId)
                }
            }
            // Compare against Android's independent WGS84 distance calculation.
            for (item in ready.destinations) {
                val expected = FloatArray(1)
                Location.distanceBetween(fix.latitude,fix.longitude,
                    item.destination.latitude,item.destination.longitude,expected)
                assertEquals(expected[0].toDouble(),item.distanceMeters,2.0 + expected[0] * 0.01)
            }
            val top = coordinator.getNearbyDestinations(1) as NearbyDestinationsResult.Ready
            assertEquals(ready.destinations.first(),top.destinations.single())
            assertNull(auth.getCurrentUser())
        } finally { auth.logout() }
    }
}
