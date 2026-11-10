package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import com.example.travelock.data.model.VisitAttempt
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PostRepository {
    private val supabase = SupabaseProvider.client

    // Explicitly filter friends: RLS also allows public posts by other users.
    suspend fun getFriendsFeed(limit: Int = 20): List<Post> {
        require(limit in 1..100) { "Feed limit must be between 1 and 100." }
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "Sign in before loading friends' posts."
        }
        val friendIds = FriendshipRepository().getMyFriendships().map {
            if (it.requesterId == user.id) it.recipientId else it.requesterId
        }.toSet()
        if (friendIds.isEmpty()) return emptyList()
        return supabase.from("posts").select {
            filter {
                neq("user_id", user.id)
                isIn("user_id", friendIds.toList())
            }
            order("created_at", Order.DESCENDING)
            order("post_id", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<Post>()
    }

    // Public here means visible to any signed-in user, including the author.
    suspend fun getPublicFeed(limit: Int = 20): List<Post> {
        require(limit in 1..100) { "Feed limit must be between 1 and 100." }
        supabase.auth.awaitInitialization()
        checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before loading public posts." }
        return supabase.from("posts").select {
            filter { eq("visibility", "public") }
            order("created_at", Order.DESCENDING)
            order("post_id", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<Post>()
    }

    // Generate the post ID before uploading so the row uses the same photo folder.
    suspend fun createPost(
        postId: String,
        visitId: String,
        imagePath: String,
        imageDescription: String = "",
        visibility: PostVisibility = PostVisibility.FRIENDS,
    ): Post {
        require(UUID.fromString(postId).toString() == postId) { "Invalid post ID." }
        require(UUID.fromString(visitId).toString() == visitId) { "Invalid visit ID." }
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before creating a post." }
        val visit = checkNotNull(supabase.from("visit_attempts").select {
            filter {
                eq("visit_id", visitId)
                eq("user_id", user.id)
            }
        }.decodeSingleOrNull<VisitAttempt>()) { "Your visit was not found." }
        require(visit.completedAt != null && visit.cancelledAt == null && visit.photoPath != null) {
            "Complete the visit before sharing a post."
        }
        val parts = imagePath.split('/')
        require(parts.size == 3 && parts[0] == user.id && parts[1] == postId &&
            parts[2].isNotBlank() && parts[2] != "." && parts[2] != "..") {
            "Upload a photo for this post before creating it."
        }
        val data = buildJsonObject {
            put("post_id", postId)
            put("user_id", user.id)
            put("destination_id", visit.destinationId)
            put("visit_id", visit.visitId)
            put("visibility", if (visibility == PostVisibility.PUBLIC) "public" else "friends")
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
