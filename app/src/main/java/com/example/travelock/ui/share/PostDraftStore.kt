package com.example.travelock.ui.share

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PostDraftStore(context: Context, name: String = "post_publish_requests") {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    suspend fun load(userId: String, visitId: String): PostPublishDraft? = withContext(Dispatchers.IO) {
        val saved = preferences.getString("$userId/$visitId", null) ?: return@withContext null
        runCatching { Json.decodeFromString<PostPublishDraft>(saved) }.getOrNull()?.takeIf {
            it.userId == userId && it.visitId == visitId && valid(it)
        }
    }

    suspend fun save(draft: PostPublishDraft) = withContext(Dispatchers.IO) {
        require(valid(draft))
        check(preferences.edit().putString("${draft.userId}/${draft.visitId}", Json.encodeToString(draft)).commit())
    }

    suspend fun clear(userId: String, visitId: String) = withContext(Dispatchers.IO) {
        check(preferences.edit().remove("$userId/$visitId").commit())
    }

    private fun valid(draft: PostPublishDraft) = listOf(draft.userId, draft.visitId, draft.postId).all {
        runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false)
    }
}
