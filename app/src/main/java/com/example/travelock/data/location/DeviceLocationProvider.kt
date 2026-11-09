package com.example.travelock.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

sealed interface DeviceLocationResult {
    data class Available(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
    ) : DeviceLocationResult

    data object PermissionRequired : DeviceLocationResult
    data object ServicesDisabled : DeviceLocationResult
    data object Unavailable : DeviceLocationResult
}

class DeviceLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    // The screen must request runtime permission before calling this method.
    // Each call gets one fix; cancelling the caller also cancels the location request.
    suspend fun getCurrentLocation(): DeviceLocationResult {
        val hasFinePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarsePermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFinePermission && !hasCoarsePermission) {
            return DeviceLocationResult.PermissionRequired
        }

        val manager = locationManager ?: return DeviceLocationResult.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            return DeviceLocationResult.ServicesDisabled
        }

        try {
            // Approximate permission can use network location without requiring GPS.
            val providers = buildList {
                if (hasFinePermission) add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
            }.filter { manager.isProviderEnabled(it) }

            for (provider in providers) {
                val location = withTimeoutOrNull(15_000L) {
                    requestLocation(manager, provider)
                } ?: continue

                return DeviceLocationResult.Available(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                )
            }
        } catch (_: SecurityException) {
            // Permission can be revoked after the initial check.
            return DeviceLocationResult.PermissionRequired
        }

        return DeviceLocationResult.Unavailable
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private suspend fun requestLocation(
        manager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        try {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(appContext),
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (error: SecurityException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
