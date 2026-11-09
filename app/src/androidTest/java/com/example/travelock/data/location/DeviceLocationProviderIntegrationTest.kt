package com.example.travelock.data.location

import android.Manifest
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.MainActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceLocationProviderIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Run with both location permissions denied. Granting/revoking them during a
    // running test can restart the app, so test permission states in separate runs.
    @Test
    fun returnsPermissionRequiredWhenLocationPermissionIsDenied() = runBlocking {
        assumeTrue(!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        assumeTrue(!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))

        assertEquals(
            DeviceLocationResult.PermissionRequired,
            DeviceLocationProvider(context).getCurrentLocation(),
        )
    }

    // Emulator setup: grant fine/coarse location, enable location services and
    // allow the app's mock_location app-op. The mock GPS provider is removed below.
    @Suppress("DEPRECATION")
    @Test
    fun returnsCoordinatesFromGpsProvider() = runBlocking {
        assumeTrue(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        val manager = context.getSystemService(LocationManager::class.java)
        assertTrue(
            "Enable emulator location services before running this test.",
            LocationManagerCompat.isLocationEnabled(manager),
        )

        // Foreground permission requires a visible activity during the request.
        ActivityScenario.launch(MainActivity::class.java).use {
            manager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, true, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
            )
            try {
                manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
                val result = withTimeout(10_000L) {
                    val request = async { DeviceLocationProvider(context).getCurrentLocation() }
                    // Send fresh fixes while Android registers the asynchronous request.
                    while (!request.isCompleted) {
                        manager.setTestProviderLocation(
                            LocationManager.GPS_PROVIDER,
                            Location(LocationManager.GPS_PROVIDER).apply {
                                latitude = 60.1738
                                longitude = 24.9381
                                accuracy = 5f
                                time = System.currentTimeMillis()
                                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                            },
                        )
                        delay(200)
                    }
                    request.await()
                }

                assertTrue("Expected a location fix, got $result", result is DeviceLocationResult.Available)
                val location = result as DeviceLocationResult.Available
                assertEquals(60.1738, location.latitude, 0.000001)
                assertEquals(24.9381, location.longitude, 0.000001)
                assertEquals(5f, location.accuracyMeters, 0.01f)
            } finally {
                manager.removeTestProvider(LocationManager.GPS_PROVIDER)
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
