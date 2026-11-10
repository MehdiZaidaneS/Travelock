package com.example.travelock.ui.visit

import com.example.travelock.data.model.VisitAttempt
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitCancellationCoordinatorTest {
    private val visit = VisitAttempt("visit", "user", "destination", Instant.EPOCH)
    private val cancelled = visit.copy(cancelledAt = Instant.ofEpochSecond(1))

    @Test
    fun clearsCheckpointOnlyAfterConfirmedServerCancellation() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator = VisitCancellationCoordinator(
            { id -> assertEquals(visit.visitId, id); calls.add("server"); cancelled },
            { userId -> assertEquals(visit.userId, userId); calls.add("clear") },
            { actual -> assertEquals(cancelled, actual); calls.add("cleanup") },
        )
        assertEquals(cancelled, coordinator.cancel(visit))
        assertEquals(listOf("server", "cleanup", "clear"), calls)
    }

    @Test
    fun interruptedCleanupKeepsCheckpointForAnotherAttempt() = runBlocking {
        var cleaning = false
        val coordinator = VisitCancellationCoordinator(
            { cancelled }, { error("Must keep checkpoint until cleanup finishes") },
            { cleaning = true; awaitCancellation() },
        )
        val job = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.cancel(visit) }
        assertTrue(cleaning)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun cleanupFailureKeepsCheckpointAndCanBeRetried() = runBlocking {
        var cleanups = 0
        var cleared = false
        val failure = IOException("Storage offline")
        val coordinator = VisitCancellationCoordinator(
            { cancelled }, { cleared = true },
            { if (++cleanups == 1) throw failure },
        )
        try {
            coordinator.cancel(visit)
            throw AssertionError("Expected cleanup failure")
        } catch (error: VisitPhotoCleanupException) {
            assertSame(failure, error.cause)
        }
        assertEquals(false, cleared)
        assertEquals(cancelled, coordinator.cancel(visit))
        assertTrue(cleared)
        assertEquals(2, cleanups)
    }

    @Test
    fun failedServerRequestKeepsCheckpoint() {
        val failure = IOException("Offline")
        val coordinator = VisitCancellationCoordinator(
            { throw failure }, { error("Must retain retry state") },
        )
        assertSame(failure, assertThrows(IOException::class.java) {
            runBlocking { coordinator.cancel(visit) }
        })
    }

    @Test
    fun cancellationOfTheRequestKeepsCheckpoint() = runBlocking {
        var requested = false
        val coordinator = VisitCancellationCoordinator(
            { requested = true; awaitCancellation() }, { error("Must retain retry state") },
        )
        val job = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.cancel(visit) }
        assertTrue(requested)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun completedVisitCannotBeCancelled() {
        val coordinator = VisitCancellationCoordinator(
            { error("Must not cancel completed visit") }, { error("Must not clear") },
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.cancel(visit.copy(completedAt = Instant.now())) }
        }
    }

    @Test
    fun clearFailureCanRetryTheSameCancelledAttempt() = runBlocking {
        var clears = 0
        var requests = 0
        val coordinator = VisitCancellationCoordinator(
            { requests++; cancelled },
            { if (++clears == 1) throw IOException("Disk error") },
        )
        try {
            coordinator.cancel(visit)
            throw AssertionError("Expected disk failure")
        } catch (_: IOException) { }
        assertEquals(cancelled, coordinator.cancel(visit))
        assertEquals(2, requests)
    }
}
