package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.VisitAttempt
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID

class VisitPhotoRepository {
    private val supabase = SupabaseProvider.client

    suspend fun deleteCancelledVisitPhotos(visit: VisitAttempt) {
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before cleaning up visit photos." }
        val bucket = supabase.storage.from("visit-photos")
        CancelledVisitPhotoCleanup(
            listNames = { prefix -> bucket.list(prefix) { limit = 100; offset = 0 }.map { it.name } },
            deletePaths = { paths -> bucket.delete(paths) },
        ).delete(visit, user.id)
    }

    // The camera flow supplies a newly captured JPEG. This upload helper cannot
    // determine where the bytes came from; it does not open a camera or gallery.
    suspend fun uploadPhoto(visit: VisitAttempt, jpegBytes: ByteArray): String {
        require(jpegBytes.isNotEmpty()) { "The visit photo cannot be empty." }
        require(jpegBytes.size <= 10 * 1024 * 1024) { "The visit photo must be at most 10 MiB." }
        require(visit.completedAt == null) { "This visit has already been completed." }
        require(visit.cancelledAt == null) { "This visit has been cancelled." }
        require(UUID.fromString(visit.visitId).toString() == visit.visitId) { "Invalid visit ID." }

        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before uploading a visit photo."
        }
        require(visit.userId == user.id) { "You can only upload a photo for your own visit." }

        // The server also requires this user/visit folder and an open attempt.
        // A new filename avoids replacing an earlier photo when retrying a capture.
        val path = "${user.id}/${visit.visitId}/${UUID.randomUUID()}.jpg"
        supabase.storage.from("visit-photos").upload(path, jpegBytes) {
            contentType = ContentType.Image.JPEG
            upsert = false
        }
        return path
    }
}
