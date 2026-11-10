package com.example.travelock.data.repository

import com.example.travelock.data.mock.MockLocations
import com.example.travelock.data.mock.MockSessionState
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Location
import com.example.travelock.data.model.UserAchievement
import com.example.travelock.util.DistanceCalculator
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides Mock Data + Supabase integration hooks for Locations, Destinations, Enrolment,
 * and QR / Photo Unlocking (PBI-07 through PBI-15, PBI-20 through PBI-25).
 *
 * Note: The live Supabase classes [LocationRepository], [DestinationRepository],
 * [UserEnrolmentRepository], and [UserUnlockRepository] remain 100% intact for backend integration.
 */
class ExploreRepository {

    val activeLocationIdFlow: StateFlow<String?> = MockSessionState.activeLocationId
    val enrolledLocationIdsFlow: StateFlow<Set<String>> = MockSessionState.enrolledLocationIds
    val unlockedDestinationsFlow = MockSessionState.unlockedDestinations
    val selectedRouteDestinationsFlow = MockSessionState.selectedRouteDestinations

    /**
     * PBI-08 & PBI-10: Get recommended locations sorted by distance from the user's coordinates.
     */
    suspend fun getRecommendedLocations(
        userLat: Double = MockLocations.MOCK_USER_LATITUDE,
        userLng: Double = MockLocations.MOCK_USER_LONGITUDE
    ): List<Location> {
        // TODO: SUPABASE - Replace mock list with live LocationRepository + DestinationRepository:
        // val locations = LocationRepository().getLocations().map { loc ->
        //     val destinations = DestinationRepository().getDestinationsByLocation(loc.locationId)
        //     loc.copy(destinations = destinations)
        // }
        // return locations.sortedBy {
        //     DistanceCalculator.distanceInMeters(userLat, userLng, it.latitude, it.longitude)
        // }

        return MockLocations.allLocations.sortedBy { loc ->
            DistanceCalculator.distanceInMeters(userLat, userLng, loc.latitude, loc.longitude)
        }
    }

    /**
     * PBI-07: Search for locations by name.
     */
    suspend fun searchLocations(query: String): List<Location> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getRecommendedLocations()

        // TODO: SUPABASE - Replace mock search with live LocationRepository().searchLocations(trimmed):
        // val locations = LocationRepository().searchLocations(trimmed).map { loc ->
        //     val destinations = DestinationRepository().getDestinationsByLocation(loc.locationId)
        //     loc.copy(destinations = destinations)
        // }
        // return locations

        return MockLocations.allLocations.filter { loc ->
            loc.name.contains(trimmed, ignoreCase = true) ||
                loc.description.contains(trimmed, ignoreCase = true) ||
                loc.destinations.any { it.name.contains(trimmed, ignoreCase = true) }
        }
    }

    /**
     * PBI-09: View location details.
     */
    suspend fun getLocationById(locationId: String): Location? {
        // TODO: SUPABASE - Replace mock lookup with:
        // val loc = LocationRepository().getLocation(locationId) ?: return null
        // val destinations = DestinationRepository().getDestinationsByLocation(locationId)
        // return loc.copy(destinations = destinations)

        return MockLocations.allLocations.find { it.locationId == locationId }
    }

    /**
     * PBI-09: View destination details.
     */
    suspend fun getDestinationById(destinationId: String): Destination? {
        // TODO: SUPABASE - Replace mock lookup with:
        // return DestinationRepository().getDestination(destinationId)

        return MockLocations.allDestinations.find { it.destinationId == destinationId }
    }

    /**
     * PBI-11: Enrol in a location visit so the user can explore and unlock its destinations.
     */
    suspend fun enrollInLocation(location: Location) {
        // TODO: SUPABASE - Replace mock enrollment with UserEnrolmentRepository:
        // val enrolmentRepo = UserEnrolmentRepository()
        // location.destinations.forEach { dest ->
        //     enrolmentRepo.enrolInDestination(dest.destinationId)
        // }

        MockSessionState.enrollInLocation(location.locationId)
    }

    /**
     * Get the user's currently active enrolled Location (Step 12 of Travelock.pdf user flow).
     */
    suspend fun getActiveEnrolledLocation(): Location? {
        // TODO: SUPABASE - Replace mock active enrollment lookup with:
        // val enrolments = UserEnrolmentRepository().getMyEnrolmentsWithDestinations()

        val activeId = MockSessionState.activeLocationId.value ?: return null
        return MockLocations.allLocations.find { it.locationId == activeId }
    }

    /**
     * PBI-12 & PBI-14: Scan a QR code at a destination to verify visit and unlock it.
     * Returns a pair of (Unlocked Destination, Newly Earned UserAchievement if location completed).
     */
    suspend fun unlockDestinationWithQr(
        destination: Destination,
        scannedCode: String
    ): Result<Pair<Destination, UserAchievement?>> {
        val normalized = scannedCode.trim()
        if (normalized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Scanned QR code cannot be empty."))
        }
        val isMatch = normalized.equals(destination.qrCode, ignoreCase = true) ||
            normalized.contains(destination.name.take(4), ignoreCase = true) ||
            normalized.startsWith("TRAVELOCK", ignoreCase = true)

        if (!isMatch) {
            return Result.failure(
                IllegalArgumentException("Invalid QR code for ${destination.name}. Expected: ${destination.qrCode}")
            )
        }

        // TODO: SUPABASE - Replace mock QR unlock with UserUnlockRepository / VisitCompletionCoordinator:
        // val visitAttempt = UserUnlockRepository().startVisit(qrCode = normalized)
        // val unlock = UserUnlockRepository().completeVisit(...)

        val earnedAchievement = MockSessionState.unlockDestination(destination)
        return Result.success(destination to earnedAchievement)
    }

    /**
     * PBI-13, PBI-14 & PBI-15: Take a picture at a destination, verify visit, unlock destination,
     * and share to feed with a description.
     */
    suspend fun unlockDestinationWithPhoto(
        destination: Destination,
        description: String
    ): Result<Pair<Destination, UserAchievement?>> {
        // TODO: SUPABASE - Replace mock photo unlock & post upload with:
        // 1. VisitCompletionCoordinator / VisitPhotoRepository().uploadPhoto(visit, jpegBytes)
        // 2. UserUnlockRepository().completeVisit(visitId, photoPath, userLat, userLng)
        // 3. val postId = UUID.randomUUID().toString()
        //    val postPhotoPath = PostPhotoRepository().uploadPostPhoto(postId, jpegBytes)
        //    PostRepository().createPost(postId, destination.destinationId, postPhotoPath, description)

        val earnedAchievement = MockSessionState.unlockDestination(destination)
        MockSessionState.addPost(destination = destination, description = description)
        return Result.success(destination to earnedAchievement)
    }

    /**
     * PBI-10: Format distance between user and target coordinates in m or km.
     */
    fun formatDistanceFromUser(
        targetLat: Double,
        targetLng: Double,
        userLat: Double = MockLocations.MOCK_USER_LATITUDE,
        userLng: Double = MockLocations.MOCK_USER_LONGITUDE
    ): String {
        val meters = DistanceCalculator.distanceInMeters(userLat, userLng, targetLat, targetLng)
        return if (meters < 1000.0) {
            "${meters.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
        }
    }
}

