package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID

class ProfilePhotoRepository {
    private val supabase = SupabaseProvider.client

    // The frontend supplies JPEG bytes from its camera or image-selection flow.
    // Upload first, then save the returned path with updateMyProfilePhoto.
    suspend fun uploadProfilePhoto(jpegBytes: ByteArray): String {
        require(jpegBytes.isNotEmpty()) { "The profile photo cannot be empty." }
        require(jpegBytes.size <= 10 * 1024 * 1024) { "The profile photo must be at most 10 MiB." }
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before uploading a profile photo." }
        val path = "${user.id}/${UUID.randomUUID()}.jpg"
        supabase.storage.from("profile-photos").upload(path,jpegBytes) {
            contentType = ContentType.Image.JPEG
            upsert = false
        }
        return path
    }
}
