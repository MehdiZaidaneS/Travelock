package com.example.travelock.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostRepositoryIntegrationTest {
    // Makes real Storage/API calls with a synthetic JPEG. Each run keeps one
    // new post and private photo; it does not exercise camera or gallery UI.
    @Test
    fun uploadsPhotoCreatesPostAndReadsOwnPosts() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = requireNotNull(arguments.getString("supabaseTestEmail")) {
            "Add the local supabaseTestEmail test argument."
        }
        val password = requireNotNull(arguments.getString("supabaseTestPassword")) {
            "Add the local supabaseTestPassword test argument."
        }
        val destinationId = requireNotNull(arguments.getString("supabaseTestDestinationId")) {
            "Add an existing destination UUID as supabaseTestDestinationId."
        }
        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()
        try {
            supabase.auth.signInWith(Email) { this.email = email; this.password = password }
            val user = checkNotNull(supabase.auth.currentUserOrNull())
            val repository = PostRepository()
            val before = repository.getMyPosts()

            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val bytes = try {
                bitmap.eraseColor(Color.BLUE)
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
            val visit = CompletedVisitFixture.getOrCreate(destinationId, bytes)
            assertEquals(before.toSet(), repository.getMyPosts().toSet())
            val unlocksBefore = UserUnlockRepository().getMyUnlocks().toSet()
            val missingId = UUID.randomUUID().toString()
            try {
                repository.createPost(missingId, visit.visitId, "${user.id}/$missingId/missing.jpg")
                fail("The server must reject a post without an uploaded photo.")
            } catch (error: PostgrestRestException) {
                assertEquals("42501", error.code)
            }
            val postId = UUID.randomUUID().toString()
            val path = PostPhotoRepository().uploadPostPhoto(postId, bytes)
            assertTrue(path.startsWith("${user.id}/$postId/"))
            assertArrayEquals(bytes, supabase.storage.from("post-photos").downloadAuthenticated(path))

            // Bypassing the Kotlin visit check must still fail on the server.
            try {
                supabase.from("posts").insert(buildJsonObject {
                    put("post_id", postId)
                    put("user_id", user.id)
                    put("destination_id", destinationId)
                    put("image", path)
                })
                fail("The server must reject a post without a completed visit link.")
            } catch (error: PostgrestRestException) {
                assertEquals("42501", error.code)
            }
            val post = repository.createPost(postId, visit.visitId, path, "  Repository photo test  ")
            assertEquals(postId, post.postId)
            assertEquals(user.id, post.userId)
            assertEquals(destinationId, post.destinationId)
            assertEquals(visit.visitId, post.visitId)
            assertEquals(PostVisibility.FRIENDS, post.visibility)
            assertEquals(path, post.image)
            assertEquals("Repository photo test", post.imageDescription)
            assertTrue(post.likes.isEmpty() && post.comments.isEmpty())

            val fetched = repository.getMyPosts()
            assertEquals(post, fetched.single { it.postId == postId })
            assertEquals(before.toSet() + post, fetched.toSet())
            assertTrue(fetched.all { it.userId == user.id })
            assertTrue(fetched.zipWithNext().all { (newer, older) -> !newer.createdAt.isBefore(older.createdAt) })

            val friendIds = FriendshipRepository().getMyFriendships().map {
                if (it.requesterId == user.id) it.recipientId else it.requesterId
            }.toSet()
            // RLS permits own, accepted friends' and signed-in-public posts.
            val visible = supabase.from("posts").select {
                filter { eq("destination_id", destinationId) }
            }.decodeList<Post>()
            assertTrue(visible.all { it.visibility == PostVisibility.PUBLIC || it.userId == user.id || it.userId in friendIds })
            assertEquals(post, visible.single { it.postId == postId })
            val feed = repository.getFriendsFeed(100)
            assertTrue(feed.size <= 100)
            assertTrue(feed.all { it.userId != user.id && it.userId in friendIds })
            assertTrue(feed.zipWithNext().all { (newer, older) ->
                newer.createdAt.isAfter(older.createdAt) ||
                    (newer.createdAt == older.createdAt && newer.postId > older.postId)
            })
            assertEquals(feed.take(1), repository.getFriendsFeed(1))
            assertEquals(unlocksBefore, UserUnlockRepository().getMyUnlocks().toSet())
        } finally {
            supabase.auth.signOut()
        }
    }
}
