package com.example.travelock.ui.visit

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.data.visit.VisitCompletionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VisitFlowState(
    val destination: Destination? = null,
    val destinationUnlocked: Boolean = false,
    val verifying: Boolean = false,
    val progress: VisitProgress = VisitProgress(),
    val enrolledIds: Set<String> = emptySet(),
    val busy: Boolean = true,
    val initialized: Boolean = false,
    val error: String? = null,
    val unlockRevision: Int = 0,
    val enrolmentRevision: Int = 0,
    val notice: String? = null,
)

// All actions are serialized on the UI scope. Photo bytes are never kept in state.
class VisitFlowController(
    private val userId: String,
    private val load: suspend () -> VisitProgress,
    private val save: suspend (VisitProgress) -> Unit,
    private val getDestination: suspend (String) -> Destination?,
    private val getEnrolledIds: suspend () -> Set<String>,
    private val enrol: suspend (String) -> Unit,
    private val start: suspend (String) -> VisitAttempt,
    private val capture: suspend (VisitAttempt, ByteArray, suspend (String) -> Unit) -> VisitCompletionResult,
    private val retry: suspend (VisitAttempt, String) -> VisitCompletionResult,
    private val cancel: suspend (VisitAttempt) -> Unit,
    private val unenrol: suspend (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(VisitFlowState())
    val state = mutableState.asStateFlow()
    private var value: VisitFlowState
        get() = mutableState.value
        set(value) { mutableState.value = value }

    suspend fun initialize() {
        if (value.initialized) return
        value = value.copy(busy = false)
        action {
            val progress = load()
            check(progress.visit == null || progress.visit.userId == userId)
            val enrolledIds = getEnrolledIds()
            value = value.copy(progress = progress, enrolledIds = enrolledIds, initialized = true)
            progress.visit?.let { visit ->
                value = value.copy(destination = getDestination(visit.destinationId), verifying = true)
            }
        }
    }

    suspend fun open(destination: Destination, unlocked: Boolean = false) = action {
        check(value.initialized) { "Load saved visit progress before starting a visit." }
        value = value.copy(destination = destination, verifying = false, notice = null,
            destinationUnlocked = unlocked || (value.progress.completed && value.progress.visit?.destinationId == destination.destinationId),
            progress = if (value.progress.completed) VisitProgress() else value.progress)
    }

    suspend fun resume() = action {
        val visit = checkNotNull(value.progress.visit)
        value = value.copy(destination = checkNotNull(getDestination(visit.destinationId)) {
            "This destination is unavailable. Try again later."
        }, verifying = true, destinationUnlocked = false)
    }

    suspend fun beginVerification(destination: Destination) {
        if (value.busy) return
        val pending = value.progress.takeUnless { it.completed }?.visit
        if (pending != null && pending.destinationId != destination.destinationId) {
            showError("Finish or cancel your saved visit before starting another destination.")
            return
        }
        if (destination.destinationId !in value.enrolledIds && pending == null) {
            showError("Enroll in this destination first.")
            return
        }
        open(destination)
        if (value.error == null && !value.destinationUnlocked) value = value.copy(verifying = true)
    }

    fun close() {
        if (!value.busy) value = value.copy(destination = null, verifying = false, error = null, notice = null)
    }

    fun showError(message: String) { value = value.copy(error = message) }

    suspend fun enrol(destination: Destination? = value.destination) = action {
        check(value.initialized) { "Load saved visit progress before enrolling." }
        val selected = checkNotNull(destination)
        if (selected.destinationId !in value.enrolledIds) {
            enrol(selected.destinationId)
            value = value.copy(enrolledIds = value.enrolledIds + selected.destinationId,
                enrolmentRevision = value.enrolmentRevision + 1, notice = "Added to Your visits.")
        }
    }

    suspend fun unenrol() = action {
        val destination = checkNotNull(value.destination)
        val progress = value.progress
        if (progress.visit?.destinationId == destination.destinationId && !progress.completed) {
            // Keep both the plan and checkpoint if cancellation/cleanup fails.
            cancel(progress.visit)
            value = value.copy(progress = VisitProgress(), verifying = false)
        }
        unenrol(destination.destinationId)
        value = value.copy(enrolledIds = value.enrolledIds - destination.destinationId,
            enrolmentRevision = value.enrolmentRevision + 1, verifying = false,
            notice = "Removed from Your visits.")
    }

    suspend fun scan(code: String) = action {
        val destination = checkNotNull(value.destination)
        check(value.progress.visit == null) { "Resume or cancel the current visit first." }
        check(destination.destinationId in value.enrolledIds) { "Enrol in this destination first." }
        require(code == destination.qrCode) { "Scan the QR code for the selected destination." }
        val visit = start(code)
        check(visit.userId == userId && visit.destinationId == destination.destinationId)
        checkpoint(VisitProgress(visit = visit))
    }

    suspend fun complete(visit: VisitAttempt, bytes: ByteArray) = action {
        check(value.progress.visit?.visitId == visit.visitId && !value.progress.completed)
        complete(capture(visit, bytes) { path ->
            checkpoint(value.progress.copy(uploadedPhotoPath = path))
        })
    }

    suspend fun retry() = action {
        val progress = value.progress
        complete(retry(checkNotNull(progress.visit), checkNotNull(progress.uploadedPhotoPath)))
    }

    suspend fun cancel() = action {
        cancel(checkNotNull(value.progress.visit))
        value = value.copy(progress = VisitProgress(), verifying = false, notice = "Verification cancelled. Your enrollment is saved.")
    }

    private suspend fun complete(result: VisitCompletionResult) {
        when (result) {
            is VisitCompletionResult.Completed -> {
                check(result.unlock.destinationId == value.progress.visit?.destinationId &&
                    result.unlock.userId == userId)
                value = value.copy(unlockRevision = value.unlockRevision + 1, destinationUnlocked = true)
                checkpoint(value.progress.copy(completed = true))
            }
            VisitCompletionResult.LocationPermissionRequired -> error("Allow precise location, then take the photo again or retry.")
            VisitCompletionResult.LocationServicesDisabled -> error("Turn on location services, then try again.")
            VisitCompletionResult.LocationUnavailable -> error("Could not get your location. Try again outdoors.")
        }
    }

    private suspend fun checkpoint(progress: VisitProgress) {
        // Keep the path in memory even if disk saving fails, so a retry reuses it.
        value = value.copy(progress = progress)
        save(progress)
    }

    private suspend fun action(block: suspend () -> Unit) {
        if (value.busy) return
        value = value.copy(busy = true, error = null)
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { value = value.copy(error = visitErrorMessage(error)) }
        finally { value = value.copy(busy = false) }
    }
}
