package com.example.travelock.data.repository

import com.example.travelock.data.model.VisitAttempt
import java.util.UUID

internal class CancelledVisitPhotoCleanup(
    private val listNames: suspend (String) -> List<String>,
    private val deletePaths: suspend (List<String>) -> Unit,
) {
    suspend fun delete(visit: VisitAttempt, userId: String) {
        require(visit.userId == userId && userId.isNotBlank()) { "You can only clean up your own visit." }
        require(visit.cancelledAt != null && visit.completedAt == null) { "Only cancelled visit photos can be deleted." }
        require(UUID.fromString(visit.visitId).toString() == visit.visitId) { "Invalid visit ID." }
        require(UUID.fromString(userId).toString() == userId) { "Invalid user ID." }
        val prefix = "$userId/${visit.visitId}"
        var previousBatch: Set<String>? = null
        while (true) {
            // Always read the first page: deleting it shifts the remaining files.
            val names = listNames(prefix)
            if (names.isEmpty()) return
            require(names.all { it.isNotBlank() && it != "." && it != ".." && '/' !in it && '\\' !in it }) {
                "Unexpected object name in visit photo folder."
            }
            check(names.toSet() != previousBatch) { "Photo cleanup made no progress. Please retry." }
            previousBatch = names.toSet()
            deletePaths(names.map { "$prefix/$it" })
        }
    }
}
