package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Comment
import com.example.travelock.data.model.PostInteractionSummary
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PostInteractionRepository {
    private val supabase = SupabaseProvider.client

    // These request a state instead of toggling it, so retrying is safe.
    suspend fun likePost(postId: String): Boolean = setLike(postId, true)
    suspend fun unlikePost(postId: String): Boolean = setLike(postId, false)

    private suspend fun setLike(postId: String, liked: Boolean): Boolean {
        validatePostId(postId)
        awaitSignedIn()
        return supabase.postgrest.rpc("set_post_like", buildJsonObject {
            put("target_post_id", postId)
            put("should_like", liked)
        }).decodeAs<Boolean>()
    }

    suspend fun getSummary(postId: String): PostInteractionSummary {
        validatePostId(postId)
        awaitSignedIn()
        return supabase.postgrest.rpc("get_post_interaction_summary", buildJsonObject {
            put("target_post_id", postId)
        }).decodeList<PostInteractionSummary>().single()
    }

    suspend fun addComment(postId: String, content: String): Comment {
        validatePostId(postId)
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "Comment cannot be blank." }
        awaitSignedIn()
        return supabase.postgrest.rpc("add_post_comment", buildJsonObject {
            put("target_post_id", postId)
            put("comment_content", trimmed)
        }).decodeAs<Comment>()
    }

    // Inaccessible or missing posts return an empty list through RLS.
    suspend fun getComments(postId: String, limit: Int = 50): List<Comment> {
        validatePostId(postId)
        require(limit in 1..100) { "Comment limit must be between 1 and 100." }
        awaitSignedIn()
        return supabase.from("comments").select {
            filter { eq("post_id", postId) }
            order("created_at", Order.DESCENDING)
            order("comment_id", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<Comment>()
    }

    private fun validatePostId(postId: String) {
        require(UUID.fromString(postId).toString() == postId) { "Invalid post ID." }
    }

    private suspend fun awaitSignedIn() {
        supabase.auth.awaitInitialization()
        checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before using post interactions." }
    }
}
