package com.example.travelock.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Post
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
            val unlocksBefore = UserUnlockRepository().getMyUnlocks().toSet()

            val missingId = UUID.randomUUID().toString()
            try {
                repository.createPost(missingId, destinationId, "${user.id}/$missingId/missing.jpg")
                fail("The server must reject a post without an uploaded photo.")
            } catch (error: PostgrestRestException) {
                assertEquals("42501", error.code)
            }

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
            val postId = UUID.randomUUID().toString()
            val path = PostPhotoRepository().uploadPostPhoto(postId, bytes)
            assertTrue(path.startsWith("${user.id}/$postId/"))
            assertArrayEquals(bytes, supabase.storage.from("post-photos").downloadAuthenticated(path))

            val post = repository.createPost(postId, destinationId, path, "  Repository photo test  ")
            assertEquals(postId, post.postId)
            assertEquals(user.id, post.userId)
            assertEquals(destinationId, post.destinationId)
            assertEquals(path, post.image)
            assertEquals("Repository photo test", post.imageDescription)
            assertTrue(post.likes.isEmpty() && post.comments.isEmpty())

            val fetched = repository.getMyPosts()
            assertEquals(post, fetched.single { it.postId == postId })
            assertEquals(before.toSet() + post, fetched.toSet())
            assertTrue(fetched.all { it.userId == user.id })
            assertTrue(fetched.zipWithNext().all { (newer, older) -> !newer.createdAt.isBefore(older.createdAt) })

            // Omit the user filter. RLS must still expose only the author's posts.
            val visible = supabase.from("posts").select {
                filter { eq("destination_id", destinationId) }
            }.decodeList<Post>()
            assertTrue(visible.all { it.userId == user.id })
            assertEquals(post, visible.single { it.postId == postId })
            assertEquals(unlocksBefore, UserUnlockRepository().getMyUnlocks().toSet())
        } finally {
            supabase.auth.signOut()
        }
    }
}
