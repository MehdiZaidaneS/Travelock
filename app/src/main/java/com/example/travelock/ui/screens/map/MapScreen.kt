package com.example.travelock.ui.screens.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.mock.MockLocations
import com.example.travelock.data.mock.MockSessionState
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.UserAchievement
import com.example.travelock.data.repository.ExploreRepository
import com.example.travelock.ui.components.DestinationCardExpanded
import com.example.travelock.util.DistanceCalculator
import kotlinx.coroutines.launch

/**
 * PBI-20 to PBI-25: Interactive Map & Route Planning Screen (Steps 18–20 of Travelock.pdf).
 * - Displays an interactive visual Helsinki Map Canvas without requiring an external Google Maps API key.
 * - Shows the user's current GPS position (PBI-20), locked vs. unlocked destination markers (PBI-21, PBI-25),
 *   and a multi-stop route builder that draws a polyline between selected destinations (PBI-22, PBI-23)
 *   and calculates the total route distance in metres/kilometres (PBI-24).
 * - Clicking a destination opens [DestinationCardExpanded] to unlock it with QR or Photo (Step 20).
 */
@Composable
fun MapScreen(
    exploreRepository: ExploreRepository = remember { ExploreRepository() }
) {
    // TODO: GOOGLE_MAPS / SUPABASE - Replace Canvas mock map with GoogleMap() composable once Maps API key is added,
    // using DeviceLocationProvider for live GPS coordinates and DestinationRepository for markers.
    val scope = rememberCoroutineScope()
    val activeLocationId by exploreRepository.activeLocationIdFlow.collectAsState()
    val unlockedDestinations by exploreRepository.unlockedDestinationsFlow.collectAsState()
    val routeDestinations by exploreRepository.selectedRouteDestinationsFlow.collectAsState()

    var selectedLocationFilter by remember(activeLocationId) {
        mutableStateOf(activeLocationId ?: MockLocations.kallio.locationId)
    }
    var selectedDestinationForSheet by remember { mutableStateOf<Destination?>(null) }
    var unlockErrorMessage by remember { mutableStateOf<String?>(null) }
    var newlyEarnedAchievement by remember { mutableStateOf<UserAchievement?>(null) }

    val focusedLocation = remember(selectedLocationFilter) {
        MockLocations.allLocations.find { it.locationId == selectedLocationFilter }
            ?: MockLocations.kallio
    }

    fun isDestUnlocked(destId: String): Boolean =
        unlockedDestinations.any { it.destinationId == destId }

    // Calculate total route distance (PBI-24): User -> Stop 1 -> Stop 2 -> ...
    val totalRouteDistanceText = remember(routeDestinations) {
        if (routeDestinations.isEmpty()) {
            "0 m"
        } else {
            var totalMeters = 0.0
            var prevLat = MockLocations.MOCK_USER_LATITUDE
            var prevLng = MockLocations.MOCK_USER_LONGITUDE
            routeDestinations.forEach { stop ->
                totalMeters += DistanceCalculator.distanceInMeters(
                    prevLat,
                    prevLng,
                    stop.latitude,
                    stop.longitude
                )
                prevLat = stop.latitude
                prevLng = stop.longitude
            }
            if (totalMeters < 1000.0) {
                "${totalMeters.toInt()} m"
            } else {
                String.format(java.util.Locale.US, "%.2f km", totalMeters / 1000.0)
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val lockedColor = MaterialTheme.colorScheme.outline

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Location Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Helsinki Exploration Map & Route Planner",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MockLocations.allLocations.forEach { loc ->
                        FilterChip(
                            selected = selectedLocationFilter == loc.locationId,
                            onClick = { selectedLocationFilter = loc.locationId },
                            label = { Text(loc.name) }
                        )
                    }
                }
            }
        }

        // Interactive Visual Map Canvas (PBI-20, PBI-21, PBI-23, PBI-25)
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(Color(0xFF1A2B3C))
                    ) {
                        val destinations = focusedLocation.destinations

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Subtle map grid lines
                            val gridStep = 45.dp.toPx()
                            var x = gridStep
                            while (x < w) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, h),
                                    strokeWidth = 1f
                                )
                                x += gridStep
                            }
                            var y = gridStep
                            while (y < h) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1f
                                )
                                y += gridStep
                            }

                            // User current location marker at bottom-left
                            val userPos = Offset(w * 0.16f, h * 0.80f)

                            // Fixed visual positions for the destinations inside the map box
                            val markerOffsets = destinations.mapIndexed { idx, _ ->
                                when (idx % 3) {
                                    0 -> Offset(w * 0.42f, h * 0.35f)
                                    1 -> Offset(w * 0.72f, h * 0.28f)
                                    else -> Offset(w * 0.65f, h * 0.70f)
                                }
                            }

                            // Draw active route polyline connecting User -> Selected Route Destinations (PBI-23)
                            if (routeDestinations.isNotEmpty()) {
                                var prevOffset = userPos
                                routeDestinations.forEach { stop ->
                                    val idx = destinations.indexOfFirst { it.destinationId == stop.destinationId }
                                    val targetOffset = if (idx >= 0) {
                                        markerOffsets[idx]
                                    } else {
                                        Offset(w * 0.82f, h * 0.50f)
                                    }
                                    drawLine(
                                        color = secondaryColor,
                                        start = prevOffset,
                                        end = targetOffset,
                                        strokeWidth = 8f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f)
                                    )
                                    prevOffset = targetOffset
                                }
                            }

                            // Draw User Location pulse
                            drawCircle(
                                color = Color(0xFF42A5F5).copy(alpha = 0.3f),
                                radius = 30f,
                                center = userPos
                            )
                            drawCircle(
                                color = Color(0xFF42A5F5),
                                radius = 14f,
                                center = userPos
                            )

                            // Draw Destination Markers (green/teal if unlocked, amber if in route, gray if locked)
                            destinations.forEachIndexed { idx, dest ->
                                val pos = markerOffsets[idx]
                                val unlocked = isDestUnlocked(dest.destinationId)
                                val inRoute = routeDestinations.any { it.destinationId == dest.destinationId }
                                val markerColor = when {
                                    unlocked -> primaryColor
                                    inRoute -> secondaryColor
                                    else -> lockedColor
                                }
                                drawCircle(
                                    color = Color.White,
                                    radius = 20f,
                                    center = pos
                                )
                                drawCircle(
                                    color = markerColor,
                                    radius = 15f,
                                    center = pos
                                )
                            }
                        }

                        // Map Legend Overlay
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = Color(0xFF42A5F5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "You (Helsinki) • Area: ${focusedLocation.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Route Distance Summary Bar (PBI-22, PBI-24)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Active Route (${routeDestinations.size} stops)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Total Route Distance: $totalRouteDistanceText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (routeDestinations.isNotEmpty()) {
                            OutlinedButton(onClick = { MockSessionState.clearRoute() }) {
                                Icon(
                                    imageVector = Icons.Default.ClearAll,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }

        // Destination Markers & Route Builder List (Steps 19-20 of Travelock.pdf)
        item {
            Text(
                text = "Destinations in ${focusedLocation.name} — Add to Route or Tap to Unlock",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(focusedLocation.destinations, key = { it.destinationId }) { dest ->
            val unlocked = isDestUnlocked(dest.destinationId)
            val inRoute = routeDestinations.any { it.destinationId == dest.destinationId }
            val distText = exploreRepository.formatDistanceFromUser(dest.latitude, dest.longitude)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (unlocked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Column {
                                Text(
                                    text = dest.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${if (unlocked) "Unlocked" else "Locked"} • $distText from you",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { MockSessionState.toggleRouteDestination(dest) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddRoad,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(if (inRoute) "Remove from Route" else "Add to Route")
                        }

                        Button(
                            onClick = {
                                unlockErrorMessage = null
                                selectedDestinationForSheet = dest
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (unlocked) "View Details" else "Arrive & Unlock")
                        }
                    }
                }
            }
        }
    }

    selectedDestinationForSheet?.let { dest ->
        DestinationCardExpanded(
            destination = dest,
            isUnlocked = isDestUnlocked(dest.destinationId),
            distanceText = exploreRepository.formatDistanceFromUser(dest.latitude, dest.longitude),
            errorMessage = unlockErrorMessage,
            onUnlockWithQr = { scannedCode ->
                scope.launch {
                    exploreRepository.unlockDestinationWithQr(dest, scannedCode)
                        .onSuccess { (_, ach) ->
                            selectedDestinationForSheet = null
                            if (ach != null) newlyEarnedAchievement = ach
                        }
                        .onFailure { err ->
                            unlockErrorMessage = err.message
                        }
                }
            },
            onUnlockWithPhoto = { caption ->
                scope.launch {
                    exploreRepository.unlockDestinationWithPhoto(dest, caption)
                        .onSuccess { (_, ach) ->
                            selectedDestinationForSheet = null
                            if (ach != null) newlyEarnedAchievement = ach
                        }
                        .onFailure { err ->
                            unlockErrorMessage = err.message
                        }
                }
            },
            onDismiss = { selectedDestinationForSheet = null }
        )
    }

    newlyEarnedAchievement?.let { ach ->
        AlertDialog(
            onDismissRequest = { newlyEarnedAchievement = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            title = { Text("${ach.location.name} Explored! 🏆") },
            text = {
                Text(
                    "You unlocked every destination in ${ach.location.name}! " +
                        "The '${ach.location.name} Explorer' achievement has been saved to your profile."
                )
            },
            confirmButton = {
                TextButton(onClick = { newlyEarnedAchievement = null }) {
                    Text("Great!")
                }
            }
        )
    }
}

