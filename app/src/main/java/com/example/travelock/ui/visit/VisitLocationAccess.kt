package com.example.travelock.ui.visit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class VisitLocationAccessState(val preciseAllowed: Boolean, val servicesEnabled: Boolean)

private fun readLocationAccess(context: Context) = VisitLocationAccessState(
    preciseAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
    servicesEnabled = context.getSystemService(LocationManager::class.java)?.let {
        LocationManagerCompat.isLocationEnabled(it)
    } == true,
)

@Composable
internal fun VisitLocationAccess(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var access by remember(context) { mutableStateOf(readLocationAccess(context)) }
    var requesting by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requesting = false
        access = readLocationAccess(context)
        denied = !access.preciseAllowed
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        access = readLocationAccess(context)
        if (access.preciseAllowed) denied = false
    }
    // Permissions and location services can change while the app is in Settings
    // or the camera. Re-read them whenever this screen regains the foreground.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = readLocationAccess(context)
                if (access.preciseAllowed) denied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    VisitLocationAccessContent(
        access = access,
        enabled = enabled && !requesting,
        denied = denied,
        requestPermission = {
            requesting = true
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        openAppSettings = {
            settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        },
        openLocationSettings = { settings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
    )
}

@Composable
internal fun VisitLocationAccessContent(
    access: VisitLocationAccessState,
    enabled: Boolean,
    denied: Boolean,
    requestPermission: () -> Unit,
    openAppSettings: () -> Unit,
    openLocationSettings: () -> Unit,
) {
    if (access.preciseAllowed) {
        Text("Precise location allowed")
    } else {
        Text(if (denied) "Precise location was not allowed. Open app settings, choose Permissions > Location, and enable precise location."
            else "Allow precise location to verify your visit within 50 metres. Choose Precise and allow access while using the app.")
        OutlinedButton(onClick = requestPermission, enabled = enabled) { Text("Allow precise location") }
        // Android can suppress requests after repeated denial. Always provide a
        // working route to change permission instead of silently requesting again.
        OutlinedButton(onClick = openAppSettings, enabled = enabled) { Text("Open app settings") }
    }
    if (!access.servicesEnabled) {
        Text("Device location services are off. Turn them on before verifying your visit.")
        OutlinedButton(onClick = openLocationSettings, enabled = enabled) { Text("Turn on location services") }
    }
}
