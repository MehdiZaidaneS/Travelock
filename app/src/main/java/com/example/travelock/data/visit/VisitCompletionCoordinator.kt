package com.example.travelock.data.visit

import android.content.Context
import com.example.travelock.data.location.DeviceLocationProvider
import com.example.travelock.data.location.DeviceLocationResult
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.data.repository.UserUnlockRepository
import com.example.travelock.data.repository.VisitPhotoRepository

sealed interface VisitCompletionResult {
    data class Completed(val unlock: UserUnlock) : VisitCompletionResult
    data object LocationPermissionRequired : VisitCompletionResult
    data object LocationServicesDisabled : VisitCompletionResult
    data object LocationUnavailable : VisitCompletionResult
}

class VisitCompletionCoordinator internal constructor(
    private val getLocation: suspend () -> DeviceLocationResult,
    private val uploadPhoto: suspend (VisitAttempt, ByteArray) -> String,
    private val finishVisit: suspend (String, String, Double, Double) -> UserUnlock,
) {
    constructor(context: Context) : this(
        DeviceLocationProvider(context)::getCurrentLocation,
        VisitPhotoRepository()::uploadPhoto,
        UserUnlockRepository()::completeVisit,
    )

    // Called after the camera returns a JPEG for a visit started by a valid QR.
    // Network/database errors propagate so the screen can present them to the user.
    suspend fun completeCapturedVisit(
        visit: VisitAttempt,
        jpegBytes: ByteArray,
    ): VisitCompletionResult {
        require(visit.completedAt == null) { "This visit has already been completed." }
        require(jpegBytes.isNotEmpty()) { "The visit photo cannot be empty." }
        require(jpegBytes.size <= 10 * 1024 * 1024) { "The visit photo must be at most 10 MiB." }

        // Do not upload anything until the phone can provide a location.
        val location = when (val result = getLocation()) {
            is DeviceLocationResult.Available -> result
            DeviceLocationResult.PermissionRequired -> return VisitCompletionResult.LocationPermissionRequired
            DeviceLocationResult.ServicesDisabled -> return VisitCompletionResult.LocationServicesDisabled
            DeviceLocationResult.Unavailable -> return VisitCompletionResult.LocationUnavailable
        }

        val photoPath = uploadPhoto(visit, jpegBytes)
        // The server remains responsible for ownership, photo and 50m validation.
        val unlock = finishVisit(visit.visitId, photoPath, location.latitude, location.longitude)
        return VisitCompletionResult.Completed(unlock)
    }
}
