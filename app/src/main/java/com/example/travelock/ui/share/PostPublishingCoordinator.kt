package com.example.travelock.ui.share

import com.example.travelock.data.model.Post
import kotlinx.coroutines.CancellationException

class PostPublishingCoordinator(
    private val findPost: suspend (String) -> Post?,
    private val upload: suspend (String, ByteArray) -> String,
    private val create: suspend (PostPublishDraft, String) -> Post,
) {
    suspend fun publish(draft: PostPublishDraft, bytes: ByteArray): Post {
        findPost(draft.postId)?.let { return requireMatching(it, draft) }
        val path = upload(draft.postId, bytes)
        check(path == draft.imagePath)
        val post = try { create(draft, path) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            // A successful INSERT can lose its response. Confirm by the stable
            // primary key before asking the user to retry the same request.
            val saved = try { findPost(draft.postId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw error }
            saved ?: throw error
        }
        return requireMatching(post, draft)
    }

    private fun requireMatching(post: Post, draft: PostPublishDraft): Post {
        check(post.postId == draft.postId && post.userId == draft.userId && post.visitId == draft.visitId &&
            post.image == draft.imagePath && post.imageDescription == draft.caption && post.visibility == draft.visibility)
        return post
    }
}
