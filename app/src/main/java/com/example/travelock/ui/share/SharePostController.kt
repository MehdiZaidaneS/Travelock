package com.example.travelock.ui.share

import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import com.example.travelock.data.model.VisitAttempt
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SharePostState(
    val busy: Boolean = false,
    val ready: Boolean = false,
    val visit: VisitAttempt? = null,
    val photo: ByteArray? = null,
    val caption: String = "",
    val visibility: PostVisibility = PostVisibility.FRIENDS,
    val request: PostPublishDraft? = null,
    val posted: Post? = null,
    val alreadyShared: Boolean = false,
    val error: String? = null,
)

class SharePostController(
    private val userId: String,
    private val destinationId: String,
    private val loadVisit: suspend () -> VisitAttempt?,
    private val existingPost: suspend (String) -> Post?,
    private val loadPhoto: suspend (VisitAttempt) -> ByteArray,
    private val loadDraft: suspend (String) -> PostPublishDraft?,
    private val saveDraft: suspend (PostPublishDraft) -> Unit,
    private val clearDraft: suspend (String) -> Unit,
    private val publish: suspend (PostPublishDraft, ByteArray) -> Post,
    private val newPostId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutableState = MutableStateFlow(SharePostState())
    val state = mutableState.asStateFlow()
    private var value: SharePostState
        get() = mutableState.value
        set(value) { mutableState.value = value }

    suspend fun prepare() {
        if (value.busy || value.ready) return
        value = value.copy(busy = true, error = null)
        try {
            val visit = loadVisit()
            if (visit == null) {
                value = value.copy(error = "There is no completed visit photo to share for this destination yet.")
                return
            }
            check(visit.userId == userId && visit.destinationId == destinationId && visit.completedAt != null &&
                visit.cancelledAt == null && !visit.photoPath.isNullOrBlank())
            val posted = existingPost(visit.visitId)
            if (posted != null) {
                check(posted.userId == userId && posted.visitId == visit.visitId && posted.destinationId == destinationId)
                value = value.copy(ready = true, visit = visit, posted = posted, alreadyShared = true)
                return
            }
            val draft = loadDraft(visit.visitId)
            check(draft == null || (draft.userId == userId && draft.visitId == visit.visitId))
            val bytes = loadPhoto(visit)
            require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024)
            value = value.copy(ready = true, visit = visit, photo = bytes, request = draft,
                caption = draft?.caption ?: "", visibility = draft?.visibility ?: PostVisibility.FRIENDS)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { value = value.copy(error = "Could not load your visit photo. Check your connection and try again.") }
        finally { value = value.copy(busy = false) }
    }

    fun setCaption(caption: String) { if (editable()) value = value.copy(caption = caption) }
    fun setVisibility(visibility: PostVisibility) { if (editable()) value = value.copy(visibility = visibility) }
    private fun editable() = !value.busy && value.request == null && value.posted == null

    suspend fun post() {
        if (value.busy || !value.ready || value.posted != null) return
        val visit = value.visit ?: return
        val bytes = value.photo ?: return
        val request = value.request ?: PostPublishDraft(userId, visit.visitId, newPostId(), value.caption.trim(), value.visibility)
        value = value.copy(busy = true, error = null, request = request)
        try {
            // Persist before any upload/INSERT so closing/reopening can recover
            // an ambiguous response without generating another post ID.
            saveDraft(request)
            val posted = publish(request, bytes)
            check(posted.userId == userId && posted.visitId == visit.visitId && posted.destinationId == destinationId &&
                posted.postId == request.postId && posted.visibility == request.visibility)
            value = value.copy(posted = posted, photo = null)
            try { clearDraft(visit.visitId) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* A stale local request is harmless: prepare checks existing posts first. */ }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { value = value.copy(error = "Could not confirm the post. Retry to check and finish the same post. Your completed visit is safe.") }
        finally { value = value.copy(busy = false) }
    }
}
