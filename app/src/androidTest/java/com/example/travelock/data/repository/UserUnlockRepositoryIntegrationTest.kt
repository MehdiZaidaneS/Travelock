package com.example.travelock.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.data.model.VisitAttempt
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.storage.storage
import java.util.UUID
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserUnlockRepositoryIntegrationTest {

    @Test
    fun readsOwnUnlocksAndHidesOtherUsersUnlocks() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val testEmail = requireNotNull(arguments.getString("supabaseTestEmail")) {
            "Add the supabaseTestEmail test argument."
        }
        val testPassword = requireNotNull(arguments.getString("supabaseTestPassword")) {
            "Add the supabaseTestPassword test argument."
        }
        val destinationId = requireNotNull(arguments.getString("supabaseTestDestinationId")) {
            "Add the seeded destination UUID as supabaseTestDestinationId."
        }

        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()

        try {
            supabase.auth.signInWith(Email) {
                email = testEmail
                password = testPassword
            }

            val user = checkNotNull(supabase.auth.currentUserOrNull())
            val ownUnlocks = UserUnlockRepository().getMyUnlocks()

            // A seeded row must be present so an empty response cannot pass the test.
            assertEquals(
                "Seed one unlock for this account and destination before running the test.",
                1,
                ownUnlocks.count { it.destinationId == destinationId },
            )
            assertTrue(ownUnlocks.all { it.userId == user.id })

            // Omit the user filter: the database must still hide the other account's row.
            // Seed the same destination for both test accounts to exercise this check.
            val visibleUnlocks = supabase.from("user_unlocks")
                .select {
                    filter { eq("destination_id", destinationId) }
                }
                .decodeList<UserUnlock>()

            assertEquals(1, visibleUnlocks.size)
            assertEquals(user.id, visibleUnlocks.single().userId)
            assertEquals(
                ownUnlocks.single { it.destinationId == destinationId },
                visibleUnlocks.single(),
            )

            // Read the fixture's code so this test needs no extra runner argument.
            val destination = supabase.from("destinations")
                .select {
                    filter { eq("destination_id", destinationId) }
                }
                .decodeSingle<Destination>()
            val repository = UserUnlockRepository()
            val detailedUnlocks = repository.getMyUnlocksWithDestinations()
            assertEquals(ownUnlocks.toSet(), detailedUnlocks.map { it.copy(destination = null) }.toSet())
            assertTrue(detailedUnlocks.all { it.userId == user.id })
            assertEquals(destination, detailedUnlocks.single { it.destinationId == destinationId }.destination)
            assertTrue(detailedUnlocks.all { it.destination?.destinationId == it.destinationId })
            // Starting creates a pending attempt, but must not award an unlock.
            val visit = repository.startVisit(destination.qrCode)
            assertEquals(user.id, visit.userId)
            assertEquals(destinationId, visit.destinationId)
            assertNull(visit.completedAt)
            assertNull(visit.photoPath)
            assertEquals(ownUnlocks.toSet(), repository.getMyUnlocks().toSet())

            suspend fun assertVisitStillPending() {
                val saved = supabase.from("visit_attempts").select {
                    filter { eq("visit_id", visit.visitId) }
                }.decodeSingle<VisitAttempt>()
                assertNull("A rejected completion must leave the attempt pending.", saved.completedAt)
                assertNull("A rejected completion must not attach a photo.", saved.photoPath)
                assertEquals(ownUnlocks.toSet(), repository.getMyUnlocks().toSet())
            }

            val attemptsBeforeInvalidQr = supabase.from("visit_attempts").select {
                filter { eq("user_id", user.id) }
            }.decodeList<VisitAttempt>().toSet()

            try {
                repository.startVisit("INVALID-TEST-${UUID.randomUUID()}")
                fail("An unknown QR code must be rejected by the database.")
            } catch (error: PostgrestRestException) {
                assertEquals("22023", error.code)
                assertTrue(error.message.orEmpty().contains("Invalid QR code."))
            }
            assertEquals(
                "An invalid QR must not create an attempt.",
                attemptsBeforeInvalidQr,
                supabase.from("visit_attempts").select {
                    filter { eq("user_id", user.id) }
                }.decodeList<VisitAttempt>().toSet(),
            )
            assertVisitStillPending()

            try {
                repository.completeVisit(
                    visitId = visit.visitId,
                    photoPath = "${user.id}/${visit.visitId}/missing.jpg",
                    latitude = destination.latitude,
                    longitude = destination.longitude,
                )
                fail("Completing a visit without an uploaded photo must be rejected.")
            } catch (error: PostgrestRestException) {
                assertEquals("22023", error.code)
                assertTrue(error.message.orEmpty().contains("Upload a photo"))
            }
            assertVisitStillPending()

            // A small synthetic JPEG tests Storage/API wiring, not live camera capture.
            // The private uploaded test photo and completed attempt are kept afterward.
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val photoBytes = try {
                bitmap.eraseColor(Color.BLUE)
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
            val photoPath = VisitPhotoRepository().uploadPhoto(visit, photoBytes)
            assertTrue(photoPath.startsWith("${user.id}/${visit.visitId}/"))

            // Just beyond the boundary, rather than only testing a distant city.
            val latitudeOffset = Math.toDegrees(51.0 / 6_371_000.0)
            val outsideLatitude = destination.latitude +
                if (destination.latitude >= 0) -latitudeOffset else latitudeOffset
            try {
                repository.completeVisit(
                    visit.visitId,
                    photoPath,
                    outsideLatitude,
                    destination.longitude,
                )
                fail("Completing a visit far from the destination must be rejected.")
            } catch (error: PostgrestRestException) {
                assertEquals("22023", error.code)
                assertTrue(error.message.orEmpty().contains("within 50 metres"))
            }
            // Check before the successful retry: rejection must not partially save
            // completion details or alter an existing unlock.
            assertVisitStillPending()

            val completed = repository.completeVisit(
                visit.visitId, photoPath, destination.latitude, destination.longitude,
            )
            val repeated = repository.completeVisit(
                visit.visitId, photoPath, destination.latitude, destination.longitude,
            )
            assertEquals(visibleUnlocks.single(), completed)
            assertEquals(completed, repeated)

            // The existing fixture keeps its original unlock ID and timestamp.
            assertEquals(ownUnlocks.toSet(), repository.getMyUnlocks().toSet())

            // These temporary images exercise the actual Storage API. Only the
            // cancelled attempt's files are removed; completed photos stay private.
            val photos = VisitPhotoRepository()
            val bucket = supabase.storage.from("visit-photos")
            suspend fun assertProtected(path: String) {
                try {
                    bucket.delete(listOf(path))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Storage may reject the request or return no deleted objects.
                }
                assertTrue("An active/completed visit photo must remain present.",
                    bucket.list(path.substringBeforeLast('/')).any { it.name == path.substringAfterLast('/') })
            }
            assertProtected(photoPath)
            val cleanupVisit = repository.startVisit(destination.qrCode)
            val cleanupPath = photos.uploadPhoto(cleanupVisit, photoBytes)
            photos.uploadPhoto(cleanupVisit, photoBytes)
            assertProtected(cleanupPath)
            val cancelledVisit = repository.cancelVisit(cleanupVisit.visitId)
            photos.deleteCancelledVisitPhotos(cancelledVisit)
            photos.deleteCancelledVisitPhotos(cancelledVisit)
            assertTrue("Cancelled visit folder must be empty after cleanup.",
                bucket.list("${user.id}/${cleanupVisit.visitId}").isEmpty())
            assertTrue("Cleanup must preserve the completed visit photo.",
                bucket.list("${user.id}/${visit.visitId}").any { it.name == photoPath.substringAfterLast('/') })
            assertEquals(ownUnlocks.toSet(), repository.getMyUnlocks().toSet())
        } finally {
            supabase.auth.signOut()
        }
    }
}
