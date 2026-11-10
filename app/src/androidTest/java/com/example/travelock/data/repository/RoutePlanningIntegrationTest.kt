package com.example.travelock.data.repository

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.RoutePoint
import com.example.travelock.data.route.RoutePlanner
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutePlanningIntegrationTest {
    // Reads real public destinations; keeps selections only in this test's state.
    // Multi-stop geometry is covered by JVM tests even with a one-row catalogue.
    @Test
    fun plansAndEditsRoutesFromThePublicCatalogueWithoutWritingData() = runBlocking {
        val fixtureId = requireNotNull(InstrumentationRegistry.getArguments().getString("supabaseTestDestinationId")) {
            "Add local supabaseTestDestinationId."
        }
        val auth = AuthRepository()
        try {
            auth.logout()
            val fixture = checkNotNull(DestinationRepository().getDestination(fixtureId))
            val others = DestinationMapRepository().getMapData().destinations.map { it.destination }
                .filterNot { it.destinationId==fixtureId }.take(2)
            val chosen = listOf(fixture) + others
            val ids = chosen.map { it.destinationId }
            val start = RoutePoint(fixture.latitude,fixture.longitude)
            val repository = RoutePlanningRepository()
            val route = repository.planRoute(ids,start)
            assertEquals(chosen,route.stops)
            assertEquals(chosen.size,route.legs.size)
            var expected = 0.0
            for (leg in route.legs) {
                val metres = FloatArray(1)
                Location.distanceBetween(leg.from.latitude,leg.from.longitude,leg.to.latitude,leg.to.longitude,metres)
                expected += metres[0]
            }
            assertEquals(expected,route.totalDistanceMeters,2.0*route.legs.size + expected*0.01)
            var edited = repository.planRoute(listOf(fixtureId),start)
            for (stop in others) edited = repository.addStop(edited,stop.destinationId)
            assertEquals(route,edited)
            assertEquals(route,repository.addStop(route,fixtureId))
            val moved = RoutePlanner.moveStop(route,ids.first(),ids.lastIndex)
            assertEquals(ids.drop(1) + ids.first(),moved.stops.map { it.destinationId })
            for (id in ids) edited = RoutePlanner.removeStop(edited,id)
            assertTrue(edited.stops.isEmpty())
            assertEquals(0.0,edited.totalDistanceMeters,0.001)
            assertEquals(edited,RoutePlanner.clearRoute(route))
            try {
                repository.planRoute(listOf(fixtureId,UUID.randomUUID().toString()),start)
                fail("Missing destination should not produce a partial route.")
            } catch (_: IllegalArgumentException) { /* No replacement/mock destination. */ }
            assertNull(auth.getCurrentUser())
        } finally { auth.logout() }
    }
}
