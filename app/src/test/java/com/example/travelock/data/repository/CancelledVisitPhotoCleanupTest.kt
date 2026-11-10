package com.example.travelock.data.repository

import com.example.travelock.data.model.VisitAttempt
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CancelledVisitPhotoCleanupTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val visit = VisitAttempt("22222222-2222-4222-8222-222222222222", user, "dest", Instant.EPOCH,
        cancelledAt = Instant.ofEpochSecond(1))
    private val prefix = "$user/${visit.visitId}"

    @Test
    fun deletesEveryPageWithoutSkippingFilesAndCanBeRepeated() = runBlocking {
        val remaining = (1..205).map { "$it.jpg" }.toMutableList()
        val batches = mutableListOf<Int>()
        val cleanup = CancelledVisitPhotoCleanup(
            { actualPrefix -> assertEquals(prefix, actualPrefix); remaining.take(100) },
            { paths ->
                batches.add(paths.size)
                assertEquals(remaining.take(paths.size).map { "$prefix/$it" }, paths)
                repeat(paths.size) { remaining.removeAt(0) }
            },
        )
        cleanup.delete(visit, user)
        cleanup.delete(visit, user)
        assertEquals(listOf(100, 100, 5), batches)
        assertEquals(emptyList<String>(), remaining)
    }

    @Test
    fun refusesActiveCompletedOrOtherUsersVisitsBeforeListing() {
        val cleanup = CancelledVisitPhotoCleanup({ error("Must not list") }, { error("Must not delete") })
        for (invalid in listOf(visit.copy(cancelledAt = null), visit.copy(completedAt = Instant.now()),
            visit.copy(userId = "other"), visit.copy(visitId = "../other"))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { cleanup.delete(invalid, user) } }
        }
    }

    @Test
    fun unexpectedNamesCannotEscapeTheVisitFolder() {
        for (name in listOf("../other.jpg", "folder/photo.jpg", "..", "", "folder\\photo.jpg")) {
            val cleanup = CancelledVisitPhotoCleanup({ listOf(name) }, { error("Must not delete") })
            assertThrows(IllegalArgumentException::class.java) { runBlocking { cleanup.delete(visit, user) } }
        }
    }

    @Test
    fun silentDeletionFailureDoesNotReportSuccessOrLoopForever() {
        var requests = 0
        val cleanup = CancelledVisitPhotoCleanup({ listOf("photo.jpg") }, { requests++ })
        assertThrows(IllegalStateException::class.java) { runBlocking { cleanup.delete(visit, user) } }
        assertEquals(1, requests)
    }

    @Test
    fun storageFailurePropagatesForRetry() {
        val failure = IOException("Offline")
        val cleanup = CancelledVisitPhotoCleanup({ listOf("photo.jpg") }, { throw failure })
        assertSame(failure, assertThrows(IOException::class.java) { runBlocking { cleanup.delete(visit, user) } })
    }
}
