package com.example.travelock.ui.visit

import android.content.Context
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.data.repository.UserUnlockRepository
import com.example.travelock.data.repository.VisitPhotoRepository
import kotlinx.coroutines.CancellationException

class VisitPhotoCleanupException(cause: Exception) : Exception(
    "Visit cancelled, but photo cleanup failed. Cancel again to retry cleanup.", cause,
)

class VisitCancellationCoordinator internal constructor(
    private val cancelOnServer: suspend (String) -> VisitAttempt,
    private val clearSavedProgress: suspend (String) -> Unit,
    private val cleanupPhotos: suspend (VisitAttempt) -> Unit = {},
) {
    constructor(context: Context) : this(
        UserUnlockRepository()::cancelVisit,
        { userId -> VisitProgressStore(context).save(userId, VisitProgress()) },
        VisitPhotoRepository()::deleteCancelledVisitPhotos,
    )

    suspend fun cancel(visit: VisitAttempt): VisitAttempt {
        require(visit.completedAt == null) { "A completed visit cannot be cancelled." }
        // Keep the checkpoint if the server fails or its response is lost. A retry
        // can cancel the same attempt again without changing the first timestamp.
        val cancelled = cancelOnServer(visit.visitId)
        check(cancelled.visitId == visit.visitId && cancelled.userId == visit.userId &&
            cancelled.cancelledAt != null && cancelled.completedAt == null)
        try {
            cleanupPhotos(cancelled)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw VisitPhotoCleanupException(error)
        }
        clearSavedProgress(visit.userId)
        return cancelled
    }
}
