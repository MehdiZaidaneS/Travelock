package com.example.travelock.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.AuthUser
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicPostVisibilityIntegrationTest {
    // Author A and the separate confirmed registration account must not be
    // accepted friends. Keeps two posts and three small post photos per run.
    @Test
    fun publicPostsReachNonFriendsWhilePrivateContentAndGuestsStayBlocked() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(arguments.getString(name)) {
            "Add the local $name test argument."
        }
        val authorEmail = argument("supabaseTestEmail")
        val authorPassword = argument("supabaseTestPassword")
        val viewerEmail = argument("supabaseRegistrationEmail")
        val viewerPassword = argument("supabaseRegistrationPassword")
        val destinationId = argument("supabaseTestDestinationId")
        val supabase = SupabaseProvider.client
        val auth = AuthRepository()
        val posts = PostRepository()
        val photos = PostPhotoRepository()
        val postBucket = supabase.storage.from("post-photos")
        val visitBucket = supabase.storage.from("visit-photos")

        suspend fun signIn(email: String, password: String): AuthUser {
            auth.logout()
            return auth.loginWithEmail(email, password)
        }
        suspend fun requireNonFriend(otherId: String) {
            assertTrue("Use an account that is not an accepted friend of the author.",
                FriendshipRepository().getMyFriendships().none {
                    it.requesterId == otherId || it.recipientId == otherId
                })
        }
        suspend fun deniedPhoto(allowUnauthenticated: Boolean = false, download: suspend () -> ByteArray) {
            try {
                download()
                fail("Private photo was accessible.")
            } catch (error: RestException) {
                // Storage can hide inaccessible objects behind not-found responses.
                assertTrue("Expected access-denied/not-found, not a server error.",
                    error.statusCode in if (allowUnauthenticated) setOf(400, 401, 403, 404)
                        else setOf(400, 403, 404))
            }
        }

        try {
            val viewer = signIn(viewerEmail, viewerPassword)
            val author = signIn(authorEmail, authorPassword)
            assertNotEquals("Use different author and viewer accounts.", author.userId, viewer.userId)
            requireNonFriend(viewer.userId)

            val privateBytes = jpeg(Color.RED)
            val publicBytes = jpeg(Color.BLUE)
            val orphanBytes = jpeg(Color.GREEN)
            val visit = CompletedVisitFixture.getOrCreate(destinationId, privateBytes)
            val evidencePath = checkNotNull(visit.photoPath)
            assertTrue(visitBucket.downloadAuthenticated(evidencePath).isNotEmpty())

            val privateId = UUID.randomUUID().toString()
            val privatePath = photos.uploadPostPhoto(privateId, privateBytes)
            val privatePost = posts.createPost(privateId, visit.visitId, privatePath, "Friends-only visibility test")
            val publicId = UUID.randomUUID().toString()
            val publicPath = photos.uploadPostPhoto(publicId, publicBytes)
            val orphanPath = photos.uploadPostPhoto(publicId, orphanBytes)
            val publicPost = posts.createPost(publicId, visit.visitId, publicPath,
                "Public visibility test", PostVisibility.PUBLIC)
            assertEquals(PostVisibility.FRIENDS, privatePost.visibility)
            assertEquals(PostVisibility.PUBLIC, publicPost.visibility)
            assertArrayEquals(privateBytes, postBucket.downloadAuthenticated(privatePath))
            assertArrayEquals(publicBytes, postBucket.downloadAuthenticated(publicPath))
            assertArrayEquals(orphanBytes, postBucket.downloadAuthenticated(orphanPath))

            assertEquals(viewer, signIn(viewerEmail, viewerPassword))
            requireNonFriend(author.userId)
            val visiblePublic = supabase.from("posts").select {
                filter { eq("post_id", publicId) }
            }.decodeList<Post>()
            assertEquals(listOf(publicPost), visiblePublic)
            assertTrue(supabase.from("posts").select {
                filter { eq("post_id", privateId) }
            }.decodeList<Post>().isEmpty())
            val publicFeed = posts.getPublicFeed(100)
            assertEquals(publicPost, publicFeed.single { it.postId == publicId })
            assertTrue(publicFeed.none { it.postId == privateId })
            assertTrue(publicFeed.all { it.visibility == PostVisibility.PUBLIC })
            assertTrue(posts.getFriendsFeed(100).none { it.userId == author.userId })
            assertTrue(posts.getMyPosts().none { it.userId == author.userId })

            assertArrayEquals(publicBytes, postBucket.downloadAuthenticated(publicPath))
            deniedPhoto { postBucket.downloadAuthenticated(privatePath) }
            deniedPhoto { postBucket.downloadAuthenticated(orphanPath) }
            deniedPhoto { visitBucket.downloadAuthenticated(evidencePath) }

            auth.logout()
            assertNull(auth.getCurrentUser())
            try {
                supabase.from("posts").select { filter { eq("post_id", publicId) } }
                fail("Public posts must still reject guest table reads.")
            } catch (error: PostgrestRestException) {
                assertEquals("42501", error.code)
            }
            deniedPhoto(allowUnauthenticated = true) { postBucket.downloadAuthenticated(publicPath) }

            // Recheck existence after all denials, ruling out missing fixtures.
            signIn(authorEmail, authorPassword)
            assertArrayEquals(privateBytes, postBucket.downloadAuthenticated(privatePath))
            assertArrayEquals(orphanBytes, postBucket.downloadAuthenticated(orphanPath))
            assertTrue(visitBucket.downloadAuthenticated(evidencePath).isNotEmpty())
        } finally {
            auth.logout()
        }
    }

    private fun jpeg(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
