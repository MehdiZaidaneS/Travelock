package com.example.travelock.ui.visit

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Small private checkpoints for reopening a visit. No credentials or image bytes.
class VisitProgressStore(context: Context, name: String = "visit_progress") {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    suspend fun load(userId: String): VisitProgress = withContext(Dispatchers.IO) {
        require(userId.isNotBlank())
        val saved = preferences.getString(userId, null) ?: return@withContext VisitProgress()
        runCatching { Json.decodeFromString<VisitProgress>(saved) }.getOrNull()
            ?.takeIf { validFor(userId, it) && !it.completed && it.visit?.cancelledAt == null }
            ?: VisitProgress()
    }

    suspend fun save(userId: String, progress: VisitProgress) = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && validFor(userId, progress)) { "Invalid visit checkpoint." }
        val editor = preferences.edit()
        if (progress.completed || progress.visit == null || progress.visit.cancelledAt != null) editor.remove(userId)
        else editor.putString(userId, Json.encodeToString(progress))
        check(editor.commit()) { "Could not save visit progress. Please try again." }
    }

    private fun validFor(userId: String, progress: VisitProgress): Boolean {
        val visit = progress.visit ?: return progress.uploadedPhotoPath == null
        if (visit.userId != userId) return false
        val path = progress.uploadedPhotoPath ?: return true
        return path.startsWith("$userId/${visit.visitId}/") && path.substringAfterLast('/').isNotBlank()
    }
}
