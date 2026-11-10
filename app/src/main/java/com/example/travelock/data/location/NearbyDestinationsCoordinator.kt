package com.example.travelock.data.location

import android.content.Context
import com.example.travelock.data.model.NearbyDestination
import com.example.travelock.data.repository.NearbyDestinationRepository

sealed interface NearbyDestinationsResult {
    data class Ready(
        val location: DeviceLocationResult.Available,
        val destinations: List<NearbyDestination>,
    ) : NearbyDestinationsResult
    data object LocationPermissionRequired : NearbyDestinationsResult
    data object LocationServicesDisabled : NearbyDestinationsResult
    data object LocationUnavailable : NearbyDestinationsResult
}

class NearbyDestinationsCoordinator internal constructor(
    private val getLocation: suspend () -> DeviceLocationResult,
    private val recommend: suspend (Double, Double, Int) -> List<NearbyDestination>,
) {
    constructor(context: Context) : this(
        DeviceLocationProvider(context)::getCurrentLocation,
        NearbyDestinationRepository()::getNearbyDestinations,
    )

    // One fresh fix per call. The screen requests permission and decides when
    // to refresh. Network errors and cancellation propagate to its caller.
    suspend fun getNearbyDestinations(limit: Int = 20): NearbyDestinationsResult {
        require(limit in 1..100) { "Recommendation limit must be between 1 and 100." }
        return when (val fix = getLocation()) {
            is DeviceLocationResult.Available -> NearbyDestinationsResult.Ready(
                fix, recommend(fix.latitude, fix.longitude, limit),
            )
            DeviceLocationResult.PermissionRequired -> NearbyDestinationsResult.LocationPermissionRequired
            DeviceLocationResult.ServicesDisabled -> NearbyDestinationsResult.LocationServicesDisabled
            DeviceLocationResult.Unavailable -> NearbyDestinationsResult.LocationUnavailable
        }
    }
}
