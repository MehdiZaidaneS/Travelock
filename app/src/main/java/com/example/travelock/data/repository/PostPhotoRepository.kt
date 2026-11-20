package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID
import kotlinx.coroutines.CancellationException

class PostPhotoRepository {
    private val supabase = SupabaseProvider.client

    // A stable filename makes an upload retry safe after a lost response. The
    // existing object is immutable; reuse it only if its bytes match this photo.
    suspend fun uploadVisitPhoto(postId: String, jpegBytes: ByteArray): String {
        require(UUID.fromString(postId).toString() == postId)
        require(jpegBytes.isNotEmpty() && jpegBytes.size <= 10 * 1024 * 1024)
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull())
        val path = "${user.id}/$postId/visit.jpg"
        val bucket = supabase.storage.from("post-photos")
        try {
            bucket.upload(path, jpegBytes) { contentType = ContentType.Image.JPEG; upsert = false }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val saved = try { bucket.downloadAuthenticated(path) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw error }
            check(saved.contentEquals(jpegBytes)) { "This post already has a different photo." }
        }
        return path
    }

    // The caller supplies JPEG bytes from its image capture or selection flow.
    suspend fun uploadPostPhoto(postId: String, jpegBytes: ByteArray): String {
        require(UUID.fromString(postId).toString() == postId) { "Invalid post ID." }
        require(jpegBytes.isNotEmpty()) { "The post photo cannot be empty." }
        require(jpegBytes.size <= 10 * 1024 * 1024) { "The post photo must be at most 10 MiB." }
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before uploading a post photo." }
        val path = "${user.id}/$postId/${UUID.randomUUID()}.jpg"
        supabase.storage.from("post-photos").upload(path, jpegBytes) {
            contentType = ContentType.Image.JPEG
            upsert = false
        }
        return path
    }
}
