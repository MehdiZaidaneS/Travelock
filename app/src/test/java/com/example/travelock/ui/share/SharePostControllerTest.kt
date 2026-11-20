package com.example.travelock.ui.share

import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import com.example.travelock.data.model.VisitAttempt
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SharePostControllerTest {
    private val visit = VisitAttempt("visit", "user", "destination", Instant.EPOCH, completedAt = Instant.EPOCH,
        photoPath = "user/visit/photo.jpg")
    private val photo = byteArrayOf(1, 2, 3)
    private fun post(draft: PostPublishDraft) = Post(draft.postId, draft.userId, draft.imagePath,
        draft.caption, "destination", Instant.EPOCH, visibility = draft.visibility, visitId = draft.visitId)

    private inner class Fixture {
        var draft: PostPublishDraft? = null
        var existing: Post? = null
        var source: VisitAttempt? = visit
        var photoLoads = 0
        var posts = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        val events = mutableListOf<String>()
        fun controller() = SharePostController("user", "destination", { source }, { existing },
            { photoLoads++; photo }, { draft }, { events += "save"; draft = it }, { draft = null },
            { request, bytes ->
                events += "publish"; posts++; assertArrayEquals(photo, bytes); gate?.await()
                if (fail) throw IOException("Headers: Authorization=Bearer SYNTHETIC_SECRET")
                post(request).also { existing = it }
            }, { "post" })
    }

    @Test fun preparingAndEditingNeverPublishOrSaveADraft() = runBlocking {
        val f = Fixture(); val c = f.controller(); c.prepare()
        assertEquals(PostVisibility.FRIENDS, c.state.value.visibility)
        assertArrayEquals(photo, c.state.value.photo)
        c.setCaption("A memory"); c.setVisibility(PostVisibility.PUBLIC)
        assertEquals(0, f.posts); assertNull(f.draft)
        assertEquals(visit, c.state.value.visit)
    }

    @Test fun explicitPostSavesRequestFirstAndKeepsChosenAudience() = runBlocking {
        val f = Fixture(); val c = f.controller(); c.prepare()
        c.setCaption("  My photo  "); c.setVisibility(PostVisibility.PUBLIC); c.post()
        assertEquals(listOf("save", "publish"), f.events)
        assertEquals("My photo", c.state.value.posted?.imageDescription)
        assertEquals(PostVisibility.PUBLIC, c.state.value.posted?.visibility)
        assertNull(f.draft); assertNull(c.state.value.photo)
        c.post(); assertEquals(1, f.posts)
    }

    @Test fun failedPublishRetainsFixedRequestAndReopensWithSameIdentity() = runBlocking {
        val f = Fixture(); val c = f.controller(); c.prepare(); c.setCaption("First caption"); f.fail = true; c.post()
        val request = checkNotNull(f.draft)
        c.setCaption("Changed"); c.setVisibility(PostVisibility.PUBLIC)
        assertEquals("First caption", c.state.value.caption)
        assertEquals(PostVisibility.FRIENDS, c.state.value.visibility)
        assertFalse(c.state.value.error.orEmpty().contains("SYNTHETIC_SECRET"))
        val reopened = f.controller(); reopened.prepare()
        assertEquals(request, reopened.state.value.request)
        f.fail = false; reopened.post()
        assertEquals(request.postId, reopened.state.value.posted?.postId)
        assertEquals(request.caption, reopened.state.value.posted?.imageDescription)
    }

    @Test fun repeatedTapsDuringPublishCannotCreateAnotherRequest() = runBlocking {
        val f = Fixture(); val c = f.controller(); c.prepare()
        val gate = CompletableDeferred<Unit>(); f.gate = gate
        val job = launch { c.post() }; kotlinx.coroutines.yield()
        c.post(); c.setCaption("Changed")
        assertEquals(1, f.posts); assertEquals("", c.state.value.caption)
        gate.complete(Unit); job.join()
    }

    @Test fun existingPostIsShownWithoutReadingOrUploadingVisitPhotoAgain() = runBlocking {
        val f = Fixture(); f.existing = post(PostPublishDraft("user", "visit", "post", "", PostVisibility.FRIENDS))
        val c = f.controller(); c.prepare(); c.post()
        assertEquals(f.existing, c.state.value.posted)
        assertEquals(0, f.photoLoads); assertEquals(0, f.posts)
    }

    @Test fun incompleteOrForeignVisitCannotBeShared() = runBlocking {
        for (invalid in listOf(null, visit.copy(completedAt = null), visit.copy(userId = "another"), visit.copy(photoPath = null))) {
            val f = Fixture(); f.source = invalid
            val c = f.controller(); c.prepare(); c.post()
            assertFalse(c.state.value.ready); assertEquals(0, f.posts); assertNull(f.draft)
        }
    }
}
