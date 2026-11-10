package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.AuthUser
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostInteractionIntegrationTest {
    // Uses existing A Friends/Public posts, accepted friend B, and the unrelated
    // confirmed registration account. Keeps two comments; restores own like states.
    @Test
    fun likesAndCommentsFollowPostAccessAndUseTheSignedInActor() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val emailA = argument("supabaseTestEmail")
        val passwordA = argument("supabaseTestPassword")
        val emailB = argument("supabaseSecondTestEmail")
        val passwordB = argument("supabaseSecondTestPassword")
        val emailC = argument("supabaseRegistrationEmail")
        val passwordC = argument("supabaseRegistrationPassword")
        val supabase = SupabaseProvider.client
        val auth = AuthRepository()
        val posts = PostRepository()
        val interactions = PostInteractionRepository()

        suspend fun signIn(email: String, password: String): AuthUser {
            auth.logout()
            return auth.loginWithEmail(email, password)
        }
        suspend fun denied(code: String = "42501", call: suspend () -> Unit) {
            try { call(); fail("Expected server rejection $code.") }
            catch (error: PostgrestRestException) { assertEquals(code, error.code) }
        }
        suspend fun exercise(post: Post, actor: AuthUser) {
            val before = interactions.getSummary(post.postId)
            try {
                assertTrue(interactions.likePost(post.postId))
                assertTrue(interactions.likePost(post.postId))
                val liked = interactions.getSummary(post.postId)
                assertEquals(before.likeCount + if (before.isLikedByMe) 0L else 1L, liked.likeCount)
                assertTrue(liked.isLikedByMe)
                assertEquals(false, interactions.unlikePost(post.postId))
                assertEquals(false, interactions.unlikePost(post.postId))
                val unliked = interactions.getSummary(post.postId)
                assertEquals(before.likeCount - if (before.isLikedByMe) 1L else 0L, unliked.likeCount)
                assertEquals(false, unliked.isLikedByMe)
                val content = "Interaction test ${UUID.randomUUID()}"
                val comment = interactions.addComment(post.postId, " \t$content\n ")
                assertEquals(content, comment.content)
                assertEquals(actor.userId, comment.userId)
                assertEquals(post.postId, comment.postId)
                assertEquals(comment, interactions.getComments(post.postId, 100).single { it.commentId == comment.commentId })
                assertEquals(before.commentCount + 1L, interactions.getSummary(post.postId).commentCount)
                assertEquals(listOf(comment), interactions.getComments(post.postId, 1))
            } finally {
                if (before.isLikedByMe) interactions.likePost(post.postId)
                else interactions.unlikePost(post.postId)
            }
            assertEquals(before.isLikedByMe, interactions.getSummary(post.postId).isLikedByMe)
        }

        try {
            val c = signIn(emailC, passwordC)
            val b = signIn(emailB, passwordB)
            val a = signIn(emailA, passwordA)
            assertNotEquals(a.userId, b.userId)
            assertNotEquals(a.userId, c.userId)
            assertNotEquals(b.userId, c.userId)
            val friends = FriendshipRepository().getMyFriendships().map {
                if (it.requesterId == a.userId) it.recipientId else it.requesterId
            }.toSet()
            assertTrue("A and B must already be accepted friends.", b.userId in friends)
            assertTrue("Registration account must not be A's accepted friend.", c.userId !in friends)
            val own = posts.getMyPosts()
            val privatePost = checkNotNull(own.firstOrNull { it.visibility == PostVisibility.FRIENDS }) {
                "Run the previous posting/visibility tests for A first."
            }
            val publicPost = checkNotNull(own.firstOrNull { it.visibility == PostVisibility.PUBLIC }) {
                "Run PublicPostVisibilityIntegrationTest for A first."
            }
            val privateBefore = interactions.getSummary(privatePost.postId)
            val publicBefore = interactions.getSummary(publicPost.postId)

            signIn(emailB, passwordB)
            exercise(privatePost, b)
            signIn(emailC, passwordC)
            assertTrue(interactions.getComments(privatePost.postId).isEmpty())
            denied { interactions.likePost(privatePost.postId) }
            denied { interactions.unlikePost(privatePost.postId) }
            denied { interactions.addComment(privatePost.postId, "Hidden") }
            denied { interactions.getSummary(privatePost.postId) }
            exercise(publicPost, c)
            denied {
                supabase.from("post_likes").insert(buildJsonObject {
                    put("post_id", publicPost.postId); put("user_id", a.userId)
                })
            }
            denied {
                supabase.from("comments").insert(buildJsonObject {
                    put("post_id", publicPost.postId); put("user_id", a.userId); put("content", "Spoofed")
                })
            }
            denied("22023") {
                supabase.postgrest.rpc("add_post_comment", buildJsonObject {
                    put("target_post_id", publicPost.postId); put("comment_content", "\t\n\u00a0\u1680\u2007\u202f")
                })
            }
            auth.logout()
            denied { supabase.postgrest.rpc("set_post_like", buildJsonObject {
                put("target_post_id", publicPost.postId); put("should_like", true)
            }) }
            denied { supabase.postgrest.rpc("add_post_comment", buildJsonObject {
                put("target_post_id", publicPost.postId); put("comment_content", "Guest")
            }) }
            denied { supabase.postgrest.rpc("get_post_interaction_summary", buildJsonObject {
                put("target_post_id", publicPost.postId)
            }) }
            signIn(emailA, passwordA)
            val privateAfter = interactions.getSummary(privatePost.postId)
            val publicAfter = interactions.getSummary(publicPost.postId)
            assertEquals(privateBefore.likeCount, privateAfter.likeCount)
            assertEquals(publicBefore.likeCount, publicAfter.likeCount)
            assertEquals(privateBefore.commentCount + 1L, privateAfter.commentCount)
            assertEquals(publicBefore.commentCount + 1L, publicAfter.commentCount)
        } finally {
            auth.logout()
        }
    }
}
