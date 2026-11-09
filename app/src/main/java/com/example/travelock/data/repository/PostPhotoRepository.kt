package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID

class PostPhotoRepository {
    private val supabase = SupabaseProvider.client

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
