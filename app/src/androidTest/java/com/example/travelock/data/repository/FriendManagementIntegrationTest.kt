package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendManagementIntegrationTest {
    // Temporarily removes A/B, then reconnects them in finally with a NEW record.
    // Run only with the dedicated accepted test pair, without concurrent tests.
    @Test
    fun removalRevokesPrivateAccessAndOldRetriesCannotRemoveANewFriendship() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val auth = AuthRepository()
        val friends = FriendshipRepository()
        val profiles = ProfileRepository()
        val supabase = SupabaseProvider.client
        val bucket = supabase.storage.from("post-photos")
        // Each URL forces a new authorization check instead of reusing a CDN hit
        // authorised while the relationship was still accepted.
        suspend fun freshPhoto(path: String) = bucket.downloadAuthenticated(path) {
            httpOverride {
                url.parameters.append("cacheNonce", UUID.randomUUID().toString())
                headers.append("Cache-Control", "no-cache, no-store")
            }
        }
        suspend fun login(prefix: String) = auth.loginWithEmail(
            argument("${prefix}Email"), argument("${prefix}Password"),
        )
        suspend fun postsBy(author: String) = supabase.from("posts").select {
            filter { eq("user_id", author) }
        }.decodeList<Post>()
        var restore = false
        var userB = ""
        suspend fun reconnect(): String {
            auth.logout()
            val a = login("supabaseTest")
            val request = friends.sendFriendRequest(userB)
            if (request.status == "accepted") return request.friendshipId
            if (request.recipientId == a.userId) {
                return friends.acceptFriendRequest(request.friendshipId).friendshipId
            }
            auth.logout()
            login("supabaseSecondTest")
            return friends.acceptFriendRequest(request.friendshipId).friendshipId
        }
        try {
            auth.logout()
            val a = login("supabaseTest")
            val own = postsBy(a.userId)
            val privatePost = checkNotNull(own.firstOrNull { it.visibility == PostVisibility.FRIENDS }) {
                "Run the existing public/friends post test with A first."
            }
            val publicPost = checkNotNull(own.firstOrNull { it.visibility == PostVisibility.PUBLIC })
            auth.logout()
            val b = login("supabaseSecondTest")
            userB = b.userId
            val relationship = friends.getMyFriends().single { it.userId == a.userId }
            assertEquals(profiles.getProfileSummary(a.userId), relationship.profile)
            assertNotNull(profiles.getFriendTravelProfile(a.userId))
            assertTrue(postsBy(a.userId).any { it.postId == privatePost.postId })
            assertTrue(freshPhoto(privatePost.image).isNotEmpty())
            restore = true // Restore even if a network failure hides a successful removal.
            assertTrue(friends.removeFriend(relationship.friendshipId))
            assertFalse(friends.removeFriend(relationship.friendshipId))
            assertTrue(friends.getMyFriends().none { it.userId == a.userId })
            assertTrue(friends.getMyFriendships().none { it.friendshipId == relationship.friendshipId })
            val remaining = postsBy(a.userId)
            assertTrue(remaining.none { it.postId == privatePost.postId })
            assertTrue(remaining.any { it.postId == publicPost.postId })
            assertTrue(PostRepository().getFriendsFeed().none { it.userId == a.userId })
            assertTrue(freshPhoto(publicPost.image).isNotEmpty())
            assertFalse("Current photo policy still grants the removed friend access.",
                supabase.postgrest.rpc("can_read_post_photo", buildJsonObject {
                    put("object_name", privatePost.image)
                    put("object_owner_id", a.userId)
                }).decodeAs<Boolean>())
            try {
                freshPhoto(privatePost.image)
                fail("Removed friend could download a friends-only photo.")
            } catch (error: RestException) {
                assertTrue(error.message, error.statusCode in setOf(400, 403, 404))
            }
            try {
                profiles.getFriendTravelProfile(a.userId)
                fail("Removed friend could load travel history.")
            } catch (error: PostgrestRestException) {
                assertEquals(error.message, "42501", error.code)
            }
            auth.logout()
            login("supabaseTest")
            assertTrue(friends.getMyFriends().none { it.userId == b.userId })
            val newId = reconnect()
            assertNotEquals(relationship.friendshipId, newId)
            assertFalse(friends.removeFriend(relationship.friendshipId))
            // reconnect can leave either account signed in; explicitly choose B.
            auth.logout()
            login("supabaseSecondTest")
            assertEquals(newId, friends.getMyFriends().single { it.userId == a.userId }.friendshipId)
            assertNotNull(profiles.getFriendTravelProfile(a.userId))
            assertTrue(freshPhoto(privatePost.image).isNotEmpty())
            auth.logout()
            for (rpc in listOf("get_my_friends", "remove_friend")) {
                try {
                    if (rpc == "remove_friend") {
                        supabase.postgrest.rpc(rpc, buildJsonObject {
                            put("relationship_id", newId)
                        })
                    } else supabase.postgrest.rpc(rpc)
                    fail("Guest could call $rpc.")
                } catch (error: PostgrestRestException) {
                    assertEquals(error.message, "42501", error.code)
                }
            }
        } finally {
            try { if (restore) reconnect() } finally { auth.logout() }
        }
    }
}
