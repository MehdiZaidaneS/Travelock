package com.example.travelock.ui.screens.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.travelock.data.location.DeviceLocationProvider
import com.example.travelock.data.location.DeviceLocationResult
import com.example.travelock.data.model.DestinationMapItem
import com.example.travelock.data.model.DestinationMapStatus
import com.example.travelock.data.model.RoutePoint
import com.example.travelock.data.repository.DestinationMapRepository
import com.example.travelock.data.route.RoutePlanner
import com.example.travelock.ui.visit.VisitFlowActions
import java.util.Locale
import kotlinx.coroutines.CancellationException

@Composable
fun MapScreen(userId: String, visitActions: VisitFlowActions) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapRepository = remember { DestinationMapRepository() }
    val locationProvider = remember(context) { DeviceLocationProvider(context) }
    var destinations by remember(userId) { mutableStateOf<List<DestinationMapItem>?>(null) }
    var loadingDestinations by remember(userId) { mutableStateOf(true) }
    var destinationError by remember(userId) { mutableStateOf(false) }
    var refreshDestinations by remember { mutableIntStateOf(0) }
    var refreshLocation by remember { mutableIntStateOf(0) }
    var location by remember { mutableStateOf<DeviceLocationResult?>(null) }
    var locating by remember { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var centerOnFix by remember { mutableStateOf(false) }
    var centerRequest by remember { mutableIntStateOf(0) }
    var camera by rememberSaveable(userId) { mutableStateOf<DoubleArray?>(null) }
    var mapAttempt by remember { mutableIntStateOf(0) }
    var showDestinations by rememberSaveable(userId) { mutableStateOf(false) }
    var routeIds by rememberSaveable(userId, stateSaver = listSaver<List<String>, String>(
        save = { it },
        restore = { it },
    )) { mutableStateOf(emptyList()) }

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    fun hasLocationPermission() = locationPermissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionDenied = !hasLocationPermission()
        if (permissionDenied) centerOnFix = false
        refreshLocation++
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshLocation++
                refreshDestinations++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(userId, visitActions.revision, refreshDestinations) {
        loadingDestinations = true
        destinationError = false
        try {
            val result = mapRepository.getMapData()
            check(result.userId == userId)
            destinations = result.destinations
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            destinationError = true
        } finally {
            loadingDestinations = false
        }
    }

    LaunchedEffect(refreshLocation) {
        locating = hasLocationPermission()
        if (!locating) location = DeviceLocationResult.PermissionRequired
        try {
            location = locationProvider.getCurrentLocation()
            if (location is DeviceLocationResult.Available) {
                permissionDenied = false
                if (centerOnFix) centerRequest++
            }
            centerOnFix = false
        } finally {
            locating = false
        }
    }

    val mapItems = destinations.orEmpty()
    val fix = location as? DeviceLocationResult.Available
    val route = remember(mapItems, routeIds, fix) {
        val itemsById = mapItems.associateBy { it.destination.destinationId }
        val stops = routeIds.mapNotNull { itemsById[it]?.destination }
        if (stops.isEmpty()) null else RoutePlanner.createRoute(
            stops,
            fix?.let { RoutePoint(it.latitude, it.longitude) },
        )
    }
    // Keep last-known pins visible during a refresh. Only open details once
    // their personal status has been fetched successfully.
    val canOpenDestination = !loadingDestinations && !destinationError
    val openDestination: (DestinationMapItem) -> Unit = { item ->
        showDestinations = false
        visitActions.onOpenDestination(item.destination, item.status == DestinationMapStatus.UNLOCKED)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DestinationMap(
                items = mapItems,
                route = route,
                location = fix,
                centerRequest = centerRequest,
                savedCamera = camera,
                onCameraChanged = { camera = it },
                attempt = mapAttempt,
                onRetry = { mapAttempt++ },
                onSelect = { if (canOpenDestination) openDestination(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MapStatus(DestinationMapStatus.LOCKED)
                    MapStatus(DestinationMapStatus.UNLOCKED)
                }
                when {
                    destinationError -> TextButton(onClick = { refreshDestinations++ }) {
                        Text("Retry loading destinations")
                    }
                    loadingDestinations -> MapMessage("Loading destinations…")
                    mapItems.isEmpty() -> MapMessage("No destinations available")
                }
                when {
                    locating -> MapMessage("Finding your location…")
                    location == DeviceLocationResult.ServicesDisabled -> TextButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    ) { Text("Turn on device location") }
                    permissionDenied && location == DeviceLocationResult.PermissionRequired -> TextButton(
                        onClick = {
                            context.startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:${context.packageName}".toUri(),
                            ))
                        },
                    ) { Text("Allow location in settings") }
                    location == DeviceLocationResult.Unavailable -> MapMessage("Location unavailable. Try again.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !locating,
                        onClick = {
                            centerOnFix = true
                            if (hasLocationPermission()) refreshLocation++
                            else permission.launch(locationPermissions)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("My location", Modifier.padding(start = 6.dp))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = mapItems.isNotEmpty(),
                        onClick = { showDestinations = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) { Text("Destinations") }
                }
            }
        }
    }

    if (showDestinations) {
        MapDestinationsSheet(
            items = mapItems,
            routeIds = routeIds,
            routeDistance = route?.takeIf { it.legs.isNotEmpty() }?.totalDistanceMeters,
            destinationError = destinationError,
            canOpenDestination = canOpenDestination,
            onRefresh = { refreshDestinations++ },
            onOpen = openDestination,
            onToggleStop = { id ->
                routeIds = if (id in routeIds) routeIds - id else routeIds + id
            },
            onClearRoute = { routeIds = emptyList() },
            onDismiss = { showDestinations = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapDestinationsSheet(
    items: List<DestinationMapItem>,
    routeIds: List<String>,
    routeDistance: Double?,
    destinationError: Boolean,
    canOpenDestination: Boolean,
    onRefresh: () -> Unit,
    onOpen: (DestinationMapItem) -> Unit,
    onToggleStop: (String) -> Unit,
    onClearRoute: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Destinations", style = MaterialTheme.typography.titleLarge)
                if (routeIds.isNotEmpty()) {
                    Text("Route draft · ${routeIds.size} stops", style = MaterialTheme.typography.titleSmall)
                    routeDistance?.let {
                        val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
                        val distance = if (it < 1000) "${it.toInt()} m"
                        else String.format(locale, "%.2f km", it / 1000)
                        MapMessage("Straight-line distance: $distance")
                    }
                    TextButton(onClick = onClearRoute) { Text("Clear route") }
                }
                if (destinationError) {
                    TextButton(onClick = onRefresh) { Text("Retry loading destinations") }
                } else if (!canOpenDestination) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
            items(items, key = { it.destination.destinationId }) { item ->
                val destination = item.destination
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(destination.name, style = MaterialTheme.typography.titleMedium)
                        MapStatus(item.status)
                        TextButton(onClick = { onToggleStop(destination.destinationId) }) {
                            Text(if (destination.destinationId in routeIds) "Remove from route" else "Add to route")
                        }
                        OutlinedButton(onClick = { onOpen(item) }, enabled = canOpenDestination) {
                            Text("View details")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapStatus(status: DestinationMapStatus) {
    val unlocked = status == DestinationMapStatus.UNLOCKED
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when (status) {
                DestinationMapStatus.UNLOCKED -> "Unlocked"
                DestinationMapStatus.LOCKED -> "Locked"
                DestinationMapStatus.SIGN_IN_REQUIRED -> "Sign in to see status"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MapMessage(message: String) {
    Text(
        message,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}
