package com.example.travelock.data.visit

import com.example.travelock.data.location.DeviceLocationResult
import com.example.travelock.data.model.UserUnlock
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

class VisitCompletionCoordinatorTest {
    private val visit = VisitAttempt("visit-1", "user-1", "destination-1", Instant.EPOCH)
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
    private val location = DeviceLocationResult.Available(60.1738, 24.9381, 5f)
    private val photoPath = "user-1/visit-1/photo.jpg"
    private val unlock = UserUnlock("unlock-1", "user-1", "destination-1", Instant.EPOCH)

    @Test
    fun cancellationAfterUploadKeepsThePathWithoutReportingSuccess() = runBlocking {
        var retainedPath: String? = null
        var finishing = false
        val coordinator = VisitCompletionCoordinator(
            getLocation = { location },
            uploadPhoto = { _, _ -> photoPath },
            finishVisit = { _, _, _, _ -> finishing = true; awaitCancellation() },
        )
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.completeCapturedVisit(visit, jpeg) { retainedPath = it }
            error("Cancelled completion must not return success")
        }
        assertTrue(finishing)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(photoPath, retainedPath)
    }

    @Test
    fun retryStillPropagatesServerRejection() {
        val rejection = IllegalStateException("Outside 50 metres")
        val coordinator = VisitCompletionCoordinator(
            getLocation = { location },
            uploadPhoto = { _, _ -> error("Retry must not upload") },
            finishVisit = { _, _, _, _ -> throw rejection },
        )
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.completeUploadedVisit(visit, photoPath) }
        }
        assertSame(rejection, thrown)
    }

    @Test
    fun completionFailureKeepsPhotoPathAndRetryUsesFreshLocationWithoutUploadingAgain() = runBlocking {
        var uploads = 0
        var finishes = 0
        var fixes = 0
        var retainedPath: String? = null
        val failure = IOException("Completion response lost")
        val coordinator = VisitCompletionCoordinator(
            getLocation = { fixes++; location.copy(latitude = location.latitude + fixes * 0.00001) },
            uploadPhoto = { _, _ -> uploads++; photoPath },
            finishVisit = { id, path, latitude, _ ->
                assertEquals(visit.visitId, id)
                assertEquals(photoPath, path)
                assertEquals(photoPath, retainedPath)
                assertEquals(location.latitude + fixes * 0.00001, latitude, 0.0)
                if (++finishes == 1) throw failure
                unlock
            },
        )

        try {
            coordinator.completeCapturedVisit(visit, jpeg) { retainedPath = it }
            throw AssertionError("Expected completion failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(VisitCompletionResult.Completed(unlock),
            coordinator.completeUploadedVisit(visit, checkNotNull(retainedPath)))
        assertEquals(1, uploads)
        assertEquals(2, fixes)
        assertEquals(2, finishes)
    }

    @Test
    fun uploadedPhotoRetryRespectsLocationFailuresWithoutFinishing() = runBlocking {
        val cases = listOf(
            DeviceLocationResult.PermissionRequired to VisitCompletionResult.LocationPermissionRequired,
            DeviceLocationResult.ServicesDisabled to VisitCompletionResult.LocationServicesDisabled,
            DeviceLocationResult.Unavailable to VisitCompletionResult.LocationUnavailable,
        )
        for ((fix, expected) in cases) {
            val coordinator = VisitCompletionCoordinator(
                getLocation = { fix },
                uploadPhoto = { _, _ -> error("Retry must not upload again") },
                finishVisit = { _, _, _, _ -> error("Retry needs a fresh location") },
            )
            assertEquals(expected, coordinator.completeUploadedVisit(visit, photoPath))
        }
    }

    @Test
    fun retryRejectsAnotherVisitsPhotoBeforeAnyWork() {
        val coordinator = VisitCompletionCoordinator(
            getLocation = { error("Invalid retry must not request location") },
            uploadPhoto = { _, _ -> error("Must not upload") },
            finishVisit = { _, _, _, _ -> error("Must not complete") },
        )
        for (path in listOf("", "https://example.com/photo.jpg", "user-2/visit-1/photo.jpg", "user-1/visit-2/photo.jpg", "user-1/visit-1/")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { coordinator.completeUploadedVisit(visit, path) }
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.completeUploadedVisit(visit.copy(completedAt = Instant.now()), photoPath) }
        }
    }

    @Test
    fun failedUploadDoesNotPublishARetryPath() {
        var published = false
        val coordinator = VisitCompletionCoordinator(
            getLocation = { location },
            uploadPhoto = { _, _ -> throw IOException("Upload failed") },
            finishVisit = { _, _, _, _ -> error("Must not complete") },
        )
        assertThrows(IOException::class.java) {
            runBlocking { coordinator.completeCapturedVisit(visit, jpeg) { published = true } }
        }
        assertEquals(false, published)
    }

    @Test
    fun sendsCameraBytesAndPhoneCoordinatesInTheCorrectOrder() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator = VisitCompletionCoordinator(
            getLocation = { calls.add("location"); location },
            uploadPhoto = { actualVisit, bytes ->
                calls.add("upload")
                assertEquals(visit, actualVisit)
                assertSame(jpeg, bytes)
                photoPath
            },
            finishVisit = { visitId, path, latitude, longitude ->
                calls.add("complete")
                assertEquals(visit.visitId, visitId)
                assertEquals(photoPath, path)
                assertEquals(location.latitude, latitude, 0.0)
                assertEquals(location.longitude, longitude, 0.0)
                unlock
            },
        )

        assertEquals(VisitCompletionResult.Completed(unlock), coordinator.completeCapturedVisit(visit, jpeg))
        assertEquals(listOf("location", "upload", "complete"), calls)
    }

    @Test
    fun locationProblemsReturnBeforeAnyUploadOrCompletion() = runBlocking {
        val cases = listOf(
            DeviceLocationResult.PermissionRequired to VisitCompletionResult.LocationPermissionRequired,
            DeviceLocationResult.ServicesDisabled to VisitCompletionResult.LocationServicesDisabled,
            DeviceLocationResult.Unavailable to VisitCompletionResult.LocationUnavailable,
        )
        for ((locationResult, expected) in cases) {
            val coordinator = VisitCompletionCoordinator(
                getLocation = { locationResult },
                uploadPhoto = { _, _ -> error("Must not upload without a location.") },
                finishVisit = { _, _, _, _ -> error("Must not complete without a location.") },
            )
            assertEquals(expected, coordinator.completeCapturedVisit(visit, jpeg))
        }
    }

    @Test
    fun invalidPhotoOrCompletedVisitFailsBeforeRequestingLocation() {
        val coordinator = VisitCompletionCoordinator(
            getLocation = { error("Invalid input must fail before requesting location.") },
            uploadPhoto = { _, _ -> error("Must not upload invalid input.") },
            finishVisit = { _, _, _, _ -> error("Must not complete invalid input.") },
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.completeCapturedVisit(visit, byteArrayOf()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.completeCapturedVisit(visit, ByteArray(10 * 1024 * 1024 + 1)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.completeCapturedVisit(visit.copy(completedAt = Instant.now()), jpeg) }
        }
    }

    @Test
    fun uploadFailureDoesNotAttemptCompletion() {
        val failure = IOException("Upload failed")
        val coordinator = VisitCompletionCoordinator(
            getLocation = { location },
            uploadPhoto = { _, _ -> throw failure },
            finishVisit = { _, _, _, _ -> error("A failed upload must not complete the visit.") },
        )
        val thrown = assertThrows(IOException::class.java) {
            runBlocking { coordinator.completeCapturedVisit(visit, jpeg) }
        }
        assertSame(failure, thrown)
    }

    @Test
    fun serverRejectionIsNotReportedAsACompletedVisit() {
        val failure = IllegalStateException("Outside the allowed distance")
        val coordinator = VisitCompletionCoordinator(
            getLocation = { location },
            uploadPhoto = { _, _ -> photoPath },
            finishVisit = { _, _, _, _ -> throw failure },
        )
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.completeCapturedVisit(visit, jpeg) }
        }
        assertSame(failure, thrown)
    }

    @Test
    fun cancellingLocationRequestDoesNotStartAnUpload() = runBlocking {
        var locationRequested = false
        val coordinator = VisitCompletionCoordinator(
            getLocation = { locationRequested = true; awaitCancellation() },
            uploadPhoto = { _, _ -> error("A cancelled request must not upload.") },
            finishVisit = { _, _, _, _ -> error("A cancelled request must not complete.") },
        )
        val job = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.completeCapturedVisit(visit, jpeg) }
        assertTrue(locationRequested)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun cancellingUploadDoesNotCompleteTheVisit() = runBlocking {
        var uploadStarted = false
        val coordinator = VisitCompletionCoordinator(
            getLocation = { location },
            uploadPhoto = { _, _ -> uploadStarted = true; awaitCancellation() },
            finishVisit = { _, _, _, _ -> error("A cancelled upload must not complete.") },
        )
        val job = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.completeCapturedVisit(visit, jpeg) }
        assertTrue(uploadStarted)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
