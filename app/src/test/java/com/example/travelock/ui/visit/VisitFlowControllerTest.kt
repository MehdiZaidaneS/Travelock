package com.example.travelock.ui.visit

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.data.visit.VisitCompletionResult
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VisitFlowControllerTest {
    private val destination = Destination("destination", "city", "Library", "", 0f, 60.0, 24.0, "", "QR")
    private val visit = VisitAttempt("visit", "user", destination.destinationId, Instant.EPOCH)
    private val path = "user/visit/photo.jpg"
    private val unlock = UserUnlock("unlock", "user", destination.destinationId, Instant.EPOCH)

    private inner class Fixture {
        var saved = VisitProgress()
        var starts = 0
        var captures = 0
        var retries = 0
        var enrolments = 0
        var removals = 0
        val events = mutableListOf<String>()
        var failRemove = false
        var failCompletion = false
        var failCancel = false
        var failLoad = false
        var failEnrolmentRead = false
        var enrolGate: CompletableDeferred<Unit>? = null
        val controller = VisitFlowController(
            userId = "user", load = { if (failLoad) throw IOException("Disk failed"); saved },
            save = { saved = it }, getDestination = { destination },
            getEnrolledIds = { if (failEnrolmentRead) throw IOException("Read failed"); emptySet() },
            enrol = { enrolments++; enrolGate?.await() },
            start = { starts++; visit },
            capture = { _, _, onUploaded ->
                captures++
                onUploaded(path)
                if (failCompletion) throw IOException("Response lost")
                VisitCompletionResult.Completed(unlock)
            },
            retry = { actualVisit, actualPath ->
                assertEquals(visit, actualVisit); assertEquals(path, actualPath)
                retries++; VisitCompletionResult.Completed(unlock)
            },
            cancel = { events += "cancel"; if (failCancel) throw IOException("Cleanup failed"); saved = VisitProgress() },
            unenrol = { events += "remove"; if (failRemove) throw IOException("Delete failed"); removals++ },
        )
        suspend fun start() {
            controller.initialize(); controller.open(destination); controller.enrol(); controller.scan("QR")
        }
    }

    @Test fun enrollmentAndMatchingQrAreRequiredBeforeStarting() = runBlocking {
        val f = Fixture()
        f.controller.initialize(); f.controller.open(destination)
        f.controller.scan("QR")
        assertEquals(0, f.starts)
        f.controller.enrol(); f.controller.scan("WRONG")
        assertEquals(0, f.starts)
        f.controller.scan("QR")
        assertEquals(1, f.starts)
        assertEquals(visit, f.saved.visit)
        assertFalse(f.saved.completed)
    }

    @Test fun enrollmentSavesWithoutNavigatingOrStartingVerification() = runBlocking {
        val f = Fixture(); f.controller.initialize()
        f.controller.enrol(destination)
        assertTrue(destination.destinationId in f.controller.state.value.enrolledIds)
        assertNull(f.controller.state.value.destination)
        assertNull(f.saved.visit)
        assertEquals(0, f.starts)
        f.controller.enrol(destination)
        assertEquals(1, f.enrolments)
        f.controller.beginVerification(destination)
        assertEquals(destination, f.controller.state.value.destination)
        assertEquals(0, f.starts)
    }

    @Test fun failedCompletionRetainsPhotoAndRetryAwardsSuccessWithoutRecapture() = runBlocking {
        val f = Fixture(); f.start(); f.failCompletion = true
        f.controller.complete(visit, byteArrayOf(1))
        assertEquals(path, f.saved.uploadedPhotoPath)
        assertFalse(f.controller.state.value.progress.completed)
        assertEquals(0, f.controller.state.value.unlockRevision)
        f.controller.retry()
        assertEquals(1, f.captures); assertEquals(1, f.retries)
        assertTrue(f.controller.state.value.progress.completed)
        assertEquals(1, f.controller.state.value.unlockRevision)
    }

    @Test fun savedVisitRestoresAndBlocksReplacingWithAnotherDestination() = runBlocking {
        val f = Fixture(); f.saved = VisitProgress(visit, path)
        f.controller.initialize()
        assertEquals(destination, f.controller.state.value.destination)
        val other = destination.copy(destinationId = "other")
        f.controller.open(other)
        f.controller.beginVerification(other)
        assertFalse(f.controller.state.value.verifying)
        assertEquals(other, f.controller.state.value.destination)
        assertEquals(visit, f.controller.state.value.progress.visit)
        f.controller.resume()
        f.controller.retry()
        assertTrue(f.controller.state.value.progress.completed)
        assertEquals(0, f.starts)
    }

    @Test fun unenrollingAPlanDoesNotCancelAnyVisit() = runBlocking {
        val f = Fixture(); f.controller.initialize(); f.controller.enrol(destination); f.controller.open(destination)
        f.controller.unenrol()
        assertEquals(1, f.removals)
        assertEquals(listOf("remove"), f.events)
        assertFalse(destination.destinationId in f.controller.state.value.enrolledIds)
        assertEquals(2, f.controller.state.value.enrolmentRevision)
    }

    @Test fun pendingVisitMustCancelAndCleanUpBeforeEnrollmentIsRemoved() = runBlocking {
        val f = Fixture(); f.start(); f.failCancel = true
        f.controller.unenrol()
        assertEquals(0, f.removals)
        assertEquals(visit, f.controller.state.value.progress.visit)
        assertTrue(destination.destinationId in f.controller.state.value.enrolledIds)
        f.failCancel = false; f.controller.unenrol()
        assertEquals(listOf("cancel", "cancel", "remove"), f.events)
        assertEquals(1, f.removals)
        assertNull(f.controller.state.value.progress.visit)
    }

    @Test fun failedRemovalKeepsEnrollmentAndCanRetryAfterSuccessfulCancellation() = runBlocking {
        val f = Fixture(); f.start(); f.failRemove = true
        f.controller.unenrol()
        assertTrue(destination.destinationId in f.controller.state.value.enrolledIds)
        assertNull(f.controller.state.value.progress.visit)
        f.failRemove = false; f.controller.unenrol()
        assertEquals(listOf("cancel", "remove", "remove"), f.events)
        assertEquals(1, f.removals)
    }

    @Test fun removingCompletedPlanPreservesCompletedProgressAndNeverCancels() = runBlocking {
        val f = Fixture(); f.start(); f.controller.complete(visit, byteArrayOf(1))
        f.controller.unenrol()
        assertEquals(listOf("remove"), f.events)
        assertTrue(f.controller.state.value.progress.completed)
        assertTrue(f.controller.state.value.destinationUnlocked)
    }

    @Test fun failedCancellationKeepsCheckpointForCleanupRetry() = runBlocking {
        val f = Fixture(); f.start(); f.failCancel = true
        f.controller.cancel()
        assertEquals(visit, f.controller.state.value.progress.visit)
        f.failCancel = false; f.controller.cancel()
        assertNull(f.saved.visit)
        assertNull(f.controller.state.value.progress.visit)
    }

    @Test fun loadFailureCannotOverwriteUnknownSavedVisit() = runBlocking {
        val f = Fixture(); f.failLoad = true
        f.controller.initialize(); f.controller.open(destination)
        assertNull(f.controller.state.value.destination)
        assertFalse(f.controller.state.value.initialized)
        f.failLoad = false; f.controller.initialize()
        assertTrue(f.controller.state.value.initialized)
    }

    @Test fun failedEnrollmentReadCanRetryBeforeStartingNewActions() = runBlocking {
        val f = Fixture(); f.failEnrolmentRead = true
        f.controller.initialize()
        assertFalse(f.controller.state.value.initialized)
        f.controller.enrol(destination)
        assertEquals(0, f.enrolments)
        f.failEnrolmentRead = false; f.controller.initialize()
        assertTrue(f.controller.state.value.initialized)
        f.controller.enrol(destination)
        assertEquals(1, f.enrolments)
    }

    @Test fun repeatedActionsDuringEnrollmentDoNotStartConcurrentRequests() = runBlocking {
        val f = Fixture(); f.controller.initialize(); f.controller.open(destination)
        val gate = CompletableDeferred<Unit>(); f.enrolGate = gate
        val job = launch { f.controller.enrol() }
        kotlinx.coroutines.yield()
        f.controller.enrol(); f.controller.scan("QR"); f.controller.close()
        assertEquals(1, f.enrolments); assertEquals(0, f.starts)
        assertEquals(destination, f.controller.state.value.destination)
        gate.complete(Unit); job.join()
        f.controller.scan("QR"); assertEquals(1, f.starts)
    }
}
