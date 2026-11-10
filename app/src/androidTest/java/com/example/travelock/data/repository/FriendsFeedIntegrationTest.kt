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
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendsFeedIntegrationTest {
    // Requires an existing accepted A/B friendship. Each run keeps two posts,
    // their photos, and one unattached upload used for the privacy check.
    @Test
    fun readsFriendsPostsAndOnlyTheirAttachedPhotos() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(arguments.getString(name)) {
            "Add the local $name test argument."
        }
        val emailA = argument("supabaseTestEmail")
        val passwordA = argument("supabaseTestPassword")
        val emailB = argument("supabaseSecondTestEmail")
        val passwordB = argument("supabaseSecondTestPassword")
        val destinationId = argument("supabaseTestDestinationId")
        val auth = AuthRepository()
        val posts = PostRepository()
        val photos = PostPhotoRepository()
        val bucket = SupabaseProvider.client.storage.from("post-photos")

        suspend fun signIn(email: String, password: String): AuthUser {
            auth.logout()
            return auth.loginWithEmail(email, password)
        }
        suspend fun verifyFeed(own: AuthUser, expected: Post) {
            val friends = FriendshipRepository().getMyFriendships().map {
                if (it.requesterId == own.userId) it.recipientId else it.requesterId
            }.toSet()
            val feed = posts.getFriendsFeed(100)
            assertEquals(expected, feed.single { it.postId == expected.postId })
            assertTrue(feed.size <= 100)
            assertTrue(feed.all { it.userId != own.userId && it.userId in friends })
            assertTrue(feed.zipWithNext().all { (newer, older) ->
                newer.createdAt.isAfter(older.createdAt) ||
                    (newer.createdAt == older.createdAt && newer.postId > older.postId)
            })
            assertEquals(feed.take(1), posts.getFriendsFeed(1))
            assertEquals(feed.take(20), posts.getFriendsFeed())
            assertTrue(posts.getMyPosts().all { it.userId == own.userId })
            val publicFeed = posts.getPublicFeed(100)
            assertTrue(publicFeed.all { it.visibility == PostVisibility.PUBLIC })
            if (expected.visibility == PostVisibility.PUBLIC) {
                assertEquals(expected, publicFeed.single { it.postId == expected.postId })
            } else {
                assertTrue(publicFeed.none { it.postId == expected.postId })
            }
        }

        try {
            val a = signIn(emailA, passwordA)
            val b = signIn(emailB, passwordB)
            assertNotEquals("Use two different accounts.", a.userId, b.userId)
            assertTrue("Run the friendship integration test with these accounts first.",
                FriendshipRepository().getMyFriendships().any {
                    (it.requesterId == a.userId && it.recipientId == b.userId) ||
                        (it.requesterId == b.userId && it.recipientId == a.userId)
                })

            val bBytes = jpeg(Color.BLUE)
            val bVisit = CompletedVisitFixture.getOrCreate(destinationId, bBytes)
            val bId = UUID.randomUUID().toString()
            val bPath = photos.uploadPostPhoto(bId, bBytes)
            val orphanBytes = jpeg(Color.GREEN)
            val orphanPath = photos.uploadPostPhoto(bId, orphanBytes)
            assertArrayEquals(orphanBytes, bucket.downloadAuthenticated(orphanPath))
            val bPost = posts.createPost(bId, bVisit.visitId, bPath, "Friend feed test B", PostVisibility.PUBLIC)
            assertEquals(bVisit.visitId, bPost.visitId)
            assertEquals(PostVisibility.PUBLIC, bPost.visibility)
            assertArrayEquals(bBytes, bucket.downloadAuthenticated(bPath))

            signIn(emailA, passwordA)
            val aBytes = jpeg(Color.RED)
            val aVisit = CompletedVisitFixture.getOrCreate(destinationId, aBytes)
            val aId = UUID.randomUUID().toString()
            val aPath = photos.uploadPostPhoto(aId, aBytes)
            val aPost = posts.createPost(aId, aVisit.visitId, aPath, "Friend feed test A")
            assertEquals(aVisit.visitId, aPost.visitId)
            assertEquals(PostVisibility.FRIENDS, aPost.visibility)
            verifyFeed(a, bPost)
            assertArrayEquals(bBytes, bucket.downloadAuthenticated(bPost.image))
            try {
                bucket.downloadAuthenticated(orphanPath)
                fail("Friend access must not expose an unattached upload in the same post folder.")
            } catch (error: RestException) {
                // Storage may hide an inaccessible object behind a not-found response.
                assertTrue("Expected an access-denied/not-found response.",
                    error.statusCode in setOf(400, 403, 404))
            }

            signIn(emailB, passwordB)
            verifyFeed(b, aPost)
            assertArrayEquals(aBytes, bucket.downloadAuthenticated(aPost.image))
            // Prove that the denied object still exists and remains readable by its owner.
            assertArrayEquals(orphanBytes, bucket.downloadAuthenticated(orphanPath))
            auth.logout()
            try {
                posts.getFriendsFeed()
                fail("Signed-out feed calls must fail.")
            } catch (_: IllegalStateException) {
                assertEquals(null, auth.getCurrentUser())
            }
            try {
                posts.getPublicFeed()
                fail("Public posts still require a signed-in user.")
            } catch (_: IllegalStateException) {
                assertEquals(null, auth.getCurrentUser())
            }
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
