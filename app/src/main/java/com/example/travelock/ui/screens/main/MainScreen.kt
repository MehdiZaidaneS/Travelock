
package com.example.travelock.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.example.travelock.data.model.Location
import com.example.travelock.data.model.Destination
import com.example.travelock.data.repository.LocationRepository

import com.example.travelock.ui.components.DestinationCard
import com.example.travelock.ui.components.ExtendedDestinationCard
import com.example.travelock.ui.components.ExtendedLocationCard
import com.example.travelock.ui.components.LocationCard
import com.example.travelock.ui.components.TravelockSearchBar
import com.example.travelock.data.location.DeviceLocationResult


@Composable
fun MainScreen(
    locationRepository: LocationRepository = remember { LocationRepository() },
    onUnlockQr: (Destination) -> Unit = {},
    onUnlockCamera: (Destination) -> Unit = {},
    modifier: Modifier = Modifier,
    currentLocation: DeviceLocationResult?
) {
    var searchQuery by remember { mutableStateOf("") }

    var locations by remember {
        mutableStateOf<List<Location>>(emptyList())
    }

    var selectedLocation by remember {
        mutableStateOf<Location?>(null)
    }

    var selectedDestination by remember {
        mutableStateOf<Destination?>(null)
    }

    /*val enrolledLocationIds by
    exploreRepository.enrolledLocationIdsFlow.collectAsState()

    val unlockedDestinations by
    exploreRepository.unlockedDestinationsFlow.collectAsState()

    val activeLocationId by
    exploreRepository.activeLocationIdFlow.collectAsState()*/

    // SEARCH LOCATIONS
    LaunchedEffect(searchQuery) {
        locations = locationRepository.searchLocations(searchQuery)
    }

    // CHECK IF DESTINATION IS UNLOCKED
    /*fun isUnlocked(destinationId: String): Boolean {
        return unlockedDestinations.any {
            it.destinationId == destinationId
        }
    }*/

    // EXTENDED DESTINATION SCREEN
    if (selectedDestination != null) {
        val destination = selectedDestination!!

        ExtendedDestinationCard(
            destination = destination,
            isLocked = true/*!isUnlocked(destination.destinationId)*/,

            onBack = {
                selectedDestination = null
            },

            onUnlockQr = {
                onUnlockQr(destination)
            },

            onUnlockCamera = {
                onUnlockCamera(destination)
            }
        )

        return
    }

    // EXTENDED LOCATION SCREEN
    if (selectedLocation != null) {
        val location = selectedLocation!!



        ExtendedLocationCard(
            location = location,
            isLocked = true/*!enrolledLocationIds.contains(location.locationId)*/,

            onBack = {
                selectedLocation = null
            },

            onDestinationSelected = { destination ->
                selectedDestination = destination
            },

            modifier = modifier
        )

        return
    }

    // MAIN SCREEN
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // SEARCH BAR
        item {
            TravelockSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )
        }

        /*// ACTIVE LOCATION
        val activeLocation = locations.find {
            it.locationId == activeLocationId
        }

        if (activeLocation != null && searchQuery.isBlank()) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Your current adventure",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = activeLocation.name,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    activeLocation.destinations.forEach { destination ->

                        DestinationCard(
                            destination = destination,
                            isLocked = true,*//*!isUnlocked(destination.destinationId)*//*
                            onClick = {
                                selectedDestination = destination
                            }
                        )
                    }
                }
            }
        }
*/
        // LOCATIONS HEADER
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "Your next targets"
                    } else {
                        "Search results (${locations.size})"
                    },
                    style = MaterialTheme.typography.titleLarge
                )

                val availableLocation =
                    currentLocation as? DeviceLocationResult.Available

                Text(
                    text = if (availableLocation != null) {
                        "Latitude: ${availableLocation.latitude}\n" +
                                "Longitude: ${availableLocation.longitude}"
                    } else {
                        "Location not available yet"
                    }
                )

                Text(
                    text = if (searchQuery.isBlank()) {
                        "Discover new places around Helsinki"
                    } else {
                        "Locations matching \"$searchQuery\""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // LOCATION CARDS
        if (locations.isEmpty()) {
            item {
                Text(
                    text = "No locations found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(
                items = locations,
                key = { it.locationId }
            ) { location ->

                val isEnrolled = false

                LocationCard(
                    location = location,
                    isLocked = !isEnrolled,
                    onViewDetails = {
                        selectedLocation = location
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
