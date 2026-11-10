package com.example.travelock.data.repository

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.RoutePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutePlanningRepositoryTest {
    private fun stop(id: String) = Destination(id,"city",id,"Real catalogue fixture",0f,0.0,1.0,"","TEST")

    @Test
    fun fetchesCurrentCatalogueStopsInTheChosenOrder() = runBlocking {
        val requested = mutableListOf<String>()
        var name = "First name"
        val repository = RoutePlanningRepository { id -> requested += id; stop(id).copy(name=name) }
        val route = repository.planRoute(listOf("b","a"),RoutePoint(0.0,0.0))
        assertEquals(listOf("b","a"),requested)
        assertEquals(listOf("b","a"),route.stops.map { it.destinationId })
        name = "New catalogue name"
        assertEquals(name,repository.planRoute(listOf("a")).stops.single().name)
        assertEquals(listOf("b","a","c"),repository.addStop(route,"c").stops.map { it.destinationId })
    }

    @Test
    fun rejectsInvalidSelectionsBeforeFetchingAndMissingStopsWithoutPartialPlan() {
        var calls = 0
        val repository = RoutePlanningRepository { id -> calls++; if (id=="missing") null else stop(id) }
        for (ids in listOf(emptyList(),listOf(" "),listOf("a","a"))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.planRoute(ids) } }
        }
        assertEquals(0,calls)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.planRoute(listOf("a","missing")) } }
    }

    @Test
    fun propagatesFetchErrorsAndCancellation() {
        val failed = RoutePlanningRepository { error("Network failure") }
        assertThrows(IllegalStateException::class.java) { runBlocking { failed.planRoute(listOf("a")) } }
        val cancelled = RoutePlanningRepository { throw CancellationException() }
        assertThrows(CancellationException::class.java) { runBlocking { cancelled.planRoute(listOf("a")) } }
    }
}
