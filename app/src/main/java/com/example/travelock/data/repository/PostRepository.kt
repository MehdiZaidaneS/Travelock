package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Post
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PostRepository {
    private val supabase = SupabaseProvider.client

    // Generate the post ID before uploading so the row uses the same photo folder.
    suspend fun createPost(
        postId: String,
        destinationId: String,
        imagePath: String,
        imageDescription: String = "",
    ): Post {
        require(UUID.fromString(postId).toString() == postId) { "Invalid post ID." }
        require(destinationId.isNotBlank()) { "Destination ID cannot be blank." }
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before creating a post." }
        val parts = imagePath.split('/')
        require(parts.size == 3 && parts[0] == user.id && parts[1] == postId &&
            parts[2].isNotBlank() && parts[2] != "." && parts[2] != "..") {
            "Upload a photo for this post before creating it."
        }
        val data = buildJsonObject {
            put("post_id", postId)
            put("user_id", user.id)
            put("destination_id", destinationId)
            put("image", imagePath)
            put("image_description", imageDescription.trim())
        }
        return supabase.from("posts").insert(data) { select() }.decodeSingle<Post>()
    }

    suspend fun getMyPosts(): List<Post> {
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before loading your posts." }
        return supabase.from("posts").select {
            filter { eq("user_id", user.id) }
            order("created_at", Order.DESCENDING)
            order("post_id", Order.DESCENDING)
        }.decodeList<Post>()
    }
}
