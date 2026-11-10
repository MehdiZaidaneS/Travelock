
package com.example.travelock.ui.screens.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.travelock.data.mock.repository.ExploreRepository
import com.example.travelock.ui.components.ExtendedDestinationCard
import com.example.travelock.util.DistanceCalculator
import kotlinx.coroutines.launch

@Composable
fun MapScreen(
    exploreRepository: ExploreRepository = remember { ExploreRepository() }
) {
    val scope = rememberCoroutineScope()

    val activeLocationId by
    exploreRepository.activeLocationIdFlow.collectAsState()

    val unlockedDestinations by
    exploreRepository.unlockedDestinationsFlow.collectAsState()

    val routeDestinations by
    exploreRepository.selectedRouteDestinationsFlow.collectAsState()

    var selectedLocationFilter by remember(activeLocationId) {
        mutableStateOf(
            activeLocationId ?: MockLocations.kallio.locationId
        )
    }

    var selectedDestination by remember {
        mutableStateOf<Destination?>(null)
    }

    var unlockErrorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var newlyEarnedAchievement by remember {
        mutableStateOf<UserAchievement?>(null)
    }

    val focusedLocation = remember(selectedLocationFilter) {
        MockLocations.allLocations.find {
            it.locationId == selectedLocationFilter
        } ?: MockLocations.kallio
    }

    fun isDestUnlocked(destinationId: String): Boolean {
        return unlockedDestinations.any {
            it.destinationId == destinationId
        }
    }

    // Calculate route distance: User -> Stop 1 -> Stop 2...
    val totalRouteDistanceText = remember(routeDestinations) {
        var totalMeters = 0.0

        var previousLatitude = MockLocations.MOCK_USER_LATITUDE
        var previousLongitude = MockLocations.MOCK_USER_LONGITUDE

        routeDestinations.forEach { destination ->
            totalMeters += DistanceCalculator.distanceInMeters(
                previousLatitude,
                previousLongitude,
                destination.latitude,
                destination.longitude
            )

            previousLatitude = destination.latitude
            previousLongitude = destination.longitude
        }

        if (totalMeters < 1000) {
            "${totalMeters.toInt()} m"
        } else {
            String.format(
                java.util.Locale.US,
                "%.2f km",
                totalMeters / 1000
            )
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

        // LOCATION FILTER
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Helsinki Exploration Map & Route Planner",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MockLocations.allLocations.forEach { location ->
                        FilterChip(
                            selected = selectedLocationFilter == location.locationId,
                            onClick = {
                                selectedLocationFilter = location.locationId
                            },
                            label = {
                                Text(location.name)
                            }
                        )
                    }
                }
            }
        }

        // MAP CANVAS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(3.dp)
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

                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val w = size.width
                            val h = size.height

                            // Map grid
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

                            // User marker
                            val userPosition = Offset(
                                w * 0.16f,
                                h * 0.80f
                            )

                            // Destination positions
                            val markerOffsets = destinations.mapIndexed { index, _ ->
                                when (index % 3) {
                                    0 -> Offset(w * 0.42f, h * 0.35f)
                                    1 -> Offset(w * 0.72f, h * 0.28f)
                                    else -> Offset(w * 0.65f, h * 0.70f)
                                }
                            }

                            // Draw route
                            if (routeDestinations.isNotEmpty()) {
                                var previousPosition = userPosition

                                routeDestinations.forEach { stop ->
                                    val index = destinations.indexOfFirst {
                                        it.destinationId == stop.destinationId
                                    }

                                    val targetPosition =
                                        if (index >= 0) {
                                            markerOffsets[index]
                                        } else {
                                            Offset(w * 0.82f, h * 0.50f)
                                        }

                                    drawLine(
                                        color = secondaryColor,
                                        start = previousPosition,
                                        end = targetPosition,
                                        strokeWidth = 8f,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(18f, 10f),
                                            0f
                                        )
                                    )

                                    previousPosition = targetPosition
                                }
                            }

                            // User location pulse
                            drawCircle(
                                color = Color(0xFF42A5F5).copy(alpha = 0.3f),
                                radius = 30f,
                                center = userPosition
                            )

                            drawCircle(
                                color = Color(0xFF42A5F5),
                                radius = 14f,
                                center = userPosition
                            )

                            // Destination markers
                            destinations.forEachIndexed { index, destination ->
                                val position = markerOffsets[index]

                                val unlocked = isDestUnlocked(
                                    destination.destinationId
                                )

                                val inRoute = routeDestinations.any {
                                    it.destinationId == destination.destinationId
                                }

                                val markerColor = when {
                                    unlocked -> primaryColor
                                    inRoute -> secondaryColor
                                    else -> lockedColor
                                }

                                drawCircle(
                                    color = Color.White,
                                    radius = 20f,
                                    center = position
                                )

                                drawCircle(
                                    color = markerColor,
                                    radius = 15f,
                                    center = position
                                )
                            }
                        }

                        // MAP LEGEND
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 6.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = Color(0xFF42A5F5),
                                    modifier = Modifier.size(16.dp)
                                )

                                Text(
                                    text = "You (Helsinki) • Area: ${focusedLocation.name}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    // ROUTE DISTANCE
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
                            OutlinedButton(
                                onClick = {
                                    MockSessionState.clearRoute()
                                }
                            ) {
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

        // DESTINATIONS TITLE
        item {
            Text(
                text = "Destinations in ${focusedLocation.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // DESTINATION LIST
        items(
            items = focusedLocation.destinations,
            key = { it.destinationId }
        ) { destination ->

            val unlocked = isDestUnlocked(destination.destinationId)

            val inRoute = routeDestinations.any {
                it.destinationId == destination.destinationId
            }

            val distanceText = exploreRepository.formatDistanceFromUser(
                destination.latitude,
                destination.longitude
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (unlocked) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Lock
                            },
                            contentDescription = null,
                            tint = if (unlocked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = destination.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${if (unlocked) "Unlocked" else "Locked"} • $distanceText from you",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                MockSessionState.toggleRouteDestination(destination)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddRoad,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp)
                            )

                            Text(
                                if (inRoute) "Remove from Route"
                                else "Add to Route"
                            )
                        }

                        Button(
                            onClick = {
                                unlockErrorMessage = null
                                selectedDestination = destination
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (unlocked) "View Details"
                                else "Arrive & Unlock"
                            )
                        }
                    }
                }
            }
        }
    }

    // EXTENDED DESTINATION BOTTOM SHEET
    selectedDestination?.let { destination ->

        ExtendedDestinationCard(
            destination = destination,
            isLocked = !isDestUnlocked(destination.destinationId),

            onBack = {
                selectedDestination = null
            },

            // Uses the destination's mock QR code.
            // Replace this with a real QR scanner result when integrated.
            onUnlockQr = {
                scope.launch {
                    exploreRepository.unlockDestinationWithQr(
                        destination,
                        destination.qrCode
                    ).onSuccess { (_, achievement) ->

                        selectedDestination = null

                        if (achievement != null) {
                            newlyEarnedAchievement = achievement
                        }

                    }.onFailure { error ->
                        unlockErrorMessage = error.message
                    }
                }
            },

            // Camera integration is not implemented by the supplied component.
            onUnlockCamera = {
                unlockErrorMessage =
                    "Camera verification is not connected yet."
            }
        )
    }

    // ERROR DIALOG
    unlockErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {
                unlockErrorMessage = null
            },
            title = {
                Text("Unlock information")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        unlockErrorMessage = null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    // ACHIEVEMENT DIALOG
    newlyEarnedAchievement?.let { achievement ->

        AlertDialog(
            onDismissRequest = {
                newlyEarnedAchievement = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            title = {
                Text("${achievement.location.name} Explored! 🏆")
            },
            text = {
                Text(
                    "You unlocked every destination in ${achievement.location.name}! " +
                            "The '${achievement.location.name} Explorer' achievement " +
                            "has been saved to your profile."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        newlyEarnedAchievement = null
                    }
                ) {
                    Text("Great!")
                }
            }
        )
    }
}
