package com.example.travelock.data.repository

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.DestinationMapStatus
import com.example.travelock.data.model.UserUnlock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationMapRepositoryTest {
    private fun destination(id: String, location: String = "city", latitude: Double = 60.0) =
        Destination(id,location,id,"Test",0f,latitude,24.0,"https://example.invalid/$id.jpg","TEST")
    private fun unlock(id: String, user: String, destination: String) =
        UserUnlock(id,user,destination,Instant.parse("2026-11-11T00:00:00Z"))

    @Test
    fun guestsSeeMarkersWithoutQueryingPrivateUnlocks() = runBlocking {
        val item = destination("one")
        val repository = DestinationMapRepository({ null },
            { _, offset, _ -> if (offset == 0L) listOf(item) else emptyList() },
            { _, _, _ -> error("Must not read guest unlocks") })
        val result = repository.getMapData()
        assertEquals(null,result.userId)
        assertEquals(item,result.destinations.single().destination)
        assertEquals(DestinationMapStatus.SIGN_IN_REQUIRED,result.destinations.single().status)
    }

    @Test
    fun readsAllDestinationAndUnlockPagesBeforeAssigningStatus() = runBlocking {
        val items = listOf(destination("b"),destination("a"),destination("c"))
        val unlocks = listOf(unlock("old","me","gone"),unlock("yes","me","c"),unlock("foreign","other","a"))
        val destinationOffsets = mutableListOf<Long>()
        val unlockOffsets = mutableListOf<Long>()
        val repository = DestinationMapRepository({ "me" },{ _, offset, _ ->
            destinationOffsets += offset; items.drop(offset.toInt()).take(1)
        },{ user, offset, _ ->
            assertEquals("me",user); unlockOffsets += offset; unlocks.drop(offset.toInt()).take(1)
        })
        val result = repository.getMapData()
        assertEquals("me",result.userId)
        assertEquals(listOf("a","b","c"),result.destinations.map { it.destination.destinationId })
        assertEquals(listOf(DestinationMapStatus.LOCKED,DestinationMapStatus.LOCKED,DestinationMapStatus.UNLOCKED),
            result.destinations.map { it.status })
        assertEquals(listOf(0L,1L,2L,3L),destinationOffsets)
        assertEquals(destinationOffsets,unlockOffsets)
    }

    @Test
    fun refreshTracksUnlocksAccountAndFilterWithoutKeepingOldState() = runBlocking {
        var viewer: String? = "a"
        var unlocks = emptyList<UserUnlock>()
        val repository = DestinationMapRepository({ viewer },{ location, offset, _ ->
            if (offset == 0L) listOf(destination("one",location ?: "city")) else emptyList()
        },{ _, offset, _ -> if (offset == 0L) unlocks else emptyList() })
        assertEquals(DestinationMapStatus.LOCKED,repository.getMapData().destinations.single().status)
        unlocks = listOf(unlock("unlock","a","one"))
        assertEquals(DestinationMapStatus.UNLOCKED,repository.getMapData("chosen").destinations.single().status)
        assertEquals("chosen",repository.getMapData("chosen").destinations.single().destination.locationId)
        viewer = "b"
        assertEquals(DestinationMapStatus.LOCKED,repository.getMapData().destinations.single().status)
        viewer = null
        assertEquals(DestinationMapStatus.SIGN_IN_REQUIRED,repository.getMapData().destinations.single().status)
    }

    @Test
    fun skipsInvalidPositionsAndSupportsEmptyLocation() = runBlocking {
        val rows = listOf(destination("bad",latitude=Double.NaN),destination("outside",latitude=91.0),destination("ok"))
        val repository = DestinationMapRepository({ null },{ location, offset, _ ->
            if (location != "empty" && offset == 0L) rows else emptyList()
        },{ _,_,_ -> error("Guest") })
        assertEquals(listOf("ok"),repository.getMapData().destinations.map { it.destination.destinationId })
        assertTrue(repository.getMapData("empty").destinations.isEmpty())
    }

    @Test
    fun accountChangesDuringLoadingRejectTheSnapshot() {
        var viewer: String? = "a"
        val repository = DestinationMapRepository({ viewer },{ _,_,_ -> viewer="b"; emptyList() },{ _,_,_ -> emptyList() })
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.getMapData() } }
    }

    @Test
    fun invalidInputsAndFailuresDoNotBecomeLockedOrEmptyResults() {
        val invalid = DestinationMapRepository({ error("Must not load") },{ _,_,_ -> emptyList() },{ _,_,_ -> emptyList() })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invalid.getMapData(" ") } }
        val denied = DestinationMapRepository({ "a" },{ _,_,_ -> emptyList() },{ _,_,_ -> error("Unlock request failed") })
        assertThrows(IllegalStateException::class.java) { runBlocking { denied.getMapData() } }
        val cancelled = DestinationMapRepository({ null },{ _,_,_ -> throw CancellationException() },{ _,_,_ -> emptyList() })
        assertThrows(CancellationException::class.java) { runBlocking { cancelled.getMapData() } }
    }
}
