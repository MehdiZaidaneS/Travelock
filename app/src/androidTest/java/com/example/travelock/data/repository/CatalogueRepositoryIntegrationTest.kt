package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Destination
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogueRepositoryIntegrationTest {

    @Test
    fun readsLocationsAndFiltersDestinationsWhileSignedOut() = runBlocking {
        val destinationId = requireNotNull(
            InstrumentationRegistry.getArguments().getString("supabaseTestDestinationId"),
        ) {
            "Add an existing destination UUID as supabaseTestDestinationId."
        }

        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()
        // Clear any saved test login so the requests exercise public read access.
        supabase.auth.signOut()
        assertNull(supabase.auth.currentSessionOrNull())

        // A known fixture prevents an empty catalogue from passing the test.
        val fixture = supabase.from("destinations")
            .select {
                filter { eq("destination_id", destinationId) }
            }
            .decodeSingle<Destination>()
        val locationRepository = LocationRepository()
        val locations = locationRepository.getLocations()
        assertTrue(
            "The fixture's location must be visible while signed out.",
            locations.any { it.locationId == fixture.locationId },
        )

        val fixtureLocation = locations.single { it.locationId == fixture.locationId }
        assertTrue("The search fixture needs a nonblank name.", fixtureLocation.name.isNotBlank())
        val searchTerm = fixtureLocation.name.take(3).uppercase()
        val searchResults = locationRepository.searchLocations("  $searchTerm  ")
        assertTrue(searchResults.any { it.locationId == fixture.locationId })
        assertTrue(searchResults.all { it.name.contains(searchTerm, ignoreCase = true) })
        assertTrue(locationRepository.searchLocations(UUID.randomUUID().toString()).isEmpty())
        assertEquals(locations.toSet(), locationRepository.searchLocations("   ").toSet())

        val repository = DestinationRepository()
        assertEquals(fixture, repository.getDestination(destinationId))
        assertNull(repository.getDestination(UUID.randomUUID().toString()))

        val destinations = repository.getDestinationsByLocation(fixture.locationId)
        assertEquals(fixture, destinations.single { it.destinationId == destinationId })
        assertTrue(destinations.all { it.locationId == fixture.locationId })

        // This also catches an accidentally omitted location filter with one city.
        val unknownLocationId = UUID.randomUUID().toString()
        assertTrue(repository.getDestinationsByLocation(unknownLocationId).isEmpty())
        assertNull(supabase.auth.currentSessionOrNull())
    }
}
