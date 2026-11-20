package com.example.travelock.ui.share

import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PostPublishingCoordinatorTest {
    private val draft = PostPublishDraft("user", "visit", "post", "Caption", PostVisibility.FRIENDS)
    private val post = Post("post", "user", draft.imagePath, "Caption", "destination", Instant.EPOCH,
        visibility = PostVisibility.FRIENDS, visitId = "visit")

    @Test fun lostInsertResponseIsConfirmedByStablePostId() = runBlocking {
        var stored: Post? = null
        var uploads = 0
        var inserts = 0
        val publisher = PostPublishingCoordinator({ stored }, { _, _ -> uploads++; draft.imagePath }, { _, _ ->
            inserts++; stored = post; throw IOException("Response lost")
        })
        assertEquals(post, publisher.publish(draft, byteArrayOf(1)))
        assertEquals(post, publisher.publish(draft, byteArrayOf(1)))
        assertEquals(1, uploads); assertEquals(1, inserts)
    }

    @Test fun mismatchedExistingPostIsNotAcceptedAsSuccessfulRetry() {
        val publisher = PostPublishingCoordinator({ post.copy(visibility = PostVisibility.PUBLIC) },
            { _, _ -> error("Must not upload") }, { _, _ -> error("Must not insert") })
        assertThrows(IllegalStateException::class.java) { runBlocking { publisher.publish(draft, byteArrayOf(1)) } }
    }

    @Test fun failedInsertWithoutSavedRowPropagatesForSafeRetry() {
        val failure = IOException("Offline")
        val publisher = PostPublishingCoordinator({ null }, { _, _ -> draft.imagePath }, { _, _ -> throw failure })
        assertSame(failure, assertThrows(IOException::class.java) { runBlocking { publisher.publish(draft, byteArrayOf(1)) } })
    }
}
