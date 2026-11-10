package com.example.travelock.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Location
import com.example.travelock.data.model.UserAchievement
import com.example.travelock.data.repository.ExploreRepository
import com.example.travelock.ui.components.DestinationCard
import com.example.travelock.ui.components.DestinationCardExpanded
import com.example.travelock.ui.components.LocationCard
import com.example.travelock.ui.components.LocationCardExpanded
import com.example.travelock.ui.components.TravelockSearchBar
import kotlinx.coroutines.launch

/**
 * T-06: Authenticated Main Page (Steps 5–17 & 20–21 of Travelock.pdf).
 * - Displays search bar to search Helsinki locations (e.g., "Kallio").
 * - When enrolled in a location, highlights that active location at the top and displays all
 *   its Destinations in [DestinationCard]s directly on the main page (Step 12).
 * - Displays recommended locations sorted by distance from the user's current location.
 * - Opens [LocationCardExpanded] and [DestinationCardExpanded] as modal bottom sheets.
 */
@Composable
fun MainScreen(
    exploreRepository: ExploreRepository = remember { ExploreRepository() }
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var locations by remember { mutableStateOf<List<Location>>(emptyList()) }

    val enrolledLocationIds by exploreRepository.enrolledLocationIdsFlow.collectAsState()
    val activeLocationId by exploreRepository.activeLocationIdFlow.collectAsState()
    val unlockedDestinations by exploreRepository.unlockedDestinationsFlow.collectAsState()

    var selectedLocationForSheet by remember { mutableStateOf<Location?>(null) }
    var selectedDestinationForSheet by remember { mutableStateOf<Destination?>(null) }
    var unlockErrorMessage by remember { mutableStateOf<String?>(null) }
    var unlockConfirmationDestination by remember { mutableStateOf<Destination?>(null) }
    var newlyEarnedAchievement by remember { mutableStateOf<UserAchievement?>(null) }

    LaunchedEffect(searchQuery) {
        locations = exploreRepository.searchLocations(searchQuery)
    }

    val activeLocation = remember(activeLocationId, locations) {
        locations.find { it.locationId == activeLocationId }
    }

    fun isDestUnlocked(destinationId: String): Boolean =
        unlockedDestinations.any { it.destinationId == destinationId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search Bar (PBI-07, Step 7)
        item {
            TravelockSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )
        }

        // Active Enrolled Location & Its Destination Cards (Step 12 of Travelock.pdf)
        if (activeLocation != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Exploring ${activeLocation.name}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap any destination card below to unlock it via QR Code or Posting a Picture:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            activeLocation.destinations.forEach { dest ->
                                DestinationCard(
                                    destination = dest,
                                    isUnlocked = isDestUnlocked(dest.destinationId),
                                    distanceText = exploreRepository.formatDistanceFromUser(
                                        dest.latitude,
                                        dest.longitude
                                    ),
                                    onClick = {
                                        unlockErrorMessage = null
                                        selectedDestinationForSheet = dest
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Header for Recommended / Search Results
        item {
            Text(
                text = if (searchQuery.isBlank()) {
                    "Recommended Locations Near You"
                } else {
                    "Search Results for \"$searchQuery\" (${locations.size})"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (locations.isEmpty()) {
            item {
                Text(
                    text = "No locations found matching \"$searchQuery\" in Helsinki.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(locations, key = { it.locationId }) { location ->
                val unlockedCount = location.destinations.count { isDestUnlocked(it.destinationId) }
                val isEnrolled = enrolledLocationIds.contains(location.locationId)
                LocationCard(
                    location = location,
                    distanceText = exploreRepository.formatDistanceFromUser(
                        location.latitude,
                        location.longitude
                    ),
                    unlockedCount = unlockedCount,
                    isEnrolled = isEnrolled,
                    onClick = { selectedLocationForSheet = location }
                )
            }
        }
    }

    // Expanded Location Card Bottom Sheet (T-07, Step 10-11)
    selectedLocationForSheet?.let { loc ->
        LocationCardExpanded(
            location = loc,
            distanceText = exploreRepository.formatDistanceFromUser(loc.latitude, loc.longitude),
            isEnrolled = enrolledLocationIds.contains(loc.locationId),
            isDestinationUnlocked = { isDestUnlocked(it) },
            formatDestinationDistance = { dest ->
                exploreRepository.formatDistanceFromUser(dest.latitude, dest.longitude)
            },
            onEnrollOrActivateClick = {
                scope.launch {
                    exploreRepository.enrollInLocation(loc)
                    selectedLocationForSheet = null
                }
            },
            onDestinationClick = { dest ->
                selectedLocationForSheet = null
                unlockErrorMessage = null
                selectedDestinationForSheet = dest
            },
            onDismiss = { selectedLocationForSheet = null }
        )
    }

    // Expanded Destination Card Bottom Sheet (T-08, Step 14-17)
    selectedDestinationForSheet?.let { dest ->
        DestinationCardExpanded(
            destination = dest,
            isUnlocked = isDestUnlocked(dest.destinationId),
            distanceText = exploreRepository.formatDistanceFromUser(dest.latitude, dest.longitude),
            errorMessage = unlockErrorMessage,
            onUnlockWithQr = { scannedCode ->
                scope.launch {
                    val result = exploreRepository.unlockDestinationWithQr(dest, scannedCode)
                    result.onSuccess { (unlockedDest, earnedAchievement) ->
                        selectedDestinationForSheet = null
                        unlockConfirmationDestination = unlockedDest
                        if (earnedAchievement != null) {
                            newlyEarnedAchievement = earnedAchievement
                        }
                    }.onFailure { err ->
                        unlockErrorMessage = err.message
                    }
                }
            },
            onUnlockWithPhoto = { caption ->
                scope.launch {
                    val result = exploreRepository.unlockDestinationWithPhoto(dest, caption)
                    result.onSuccess { (unlockedDest, earnedAchievement) ->
                        selectedDestinationForSheet = null
                        unlockConfirmationDestination = unlockedDest
                        if (earnedAchievement != null) {
                            newlyEarnedAchievement = earnedAchievement
                        }
                    }.onFailure { err ->
                        unlockErrorMessage = err.message
                    }
                }
            },
            onDismiss = { selectedDestinationForSheet = null }
        )
    }

    // PBI-14: Unlock Confirmation Dialog
    unlockConfirmationDestination?.let { unlockedDest ->
        AlertDialog(
            onDismissRequest = { unlockConfirmationDestination = null },
            title = { Text("Destination Unlocked! 🎉") },
            text = {
                Text(
                    "Congratulations! You have unlocked ${unlockedDest.name}." +
                        if (unlockedDest.discount > 0f) {
                            "\n\nYour ${unlockedDest.discount.toInt()}% partner discount is now active!"
                        } else {
                            ""
                        }
                )
            },
            confirmButton = {
                Button(onClick = { unlockConfirmationDestination = null }) {
                    Text("Awesome!")
                }
            }
        )
    }

    // Step 21 of Travelock.pdf: Location Achievement Earned Dialog
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
                        "The '${ach.location.name} Explorer' achievement (Level ${ach.level}) has been saved to your profile."
                )
            },
            confirmButton = {
                TextButton(onClick = { newlyEarnedAchievement = null }) {
                    Text("View on Profile Later")
                }
            }
        )
    }
}

