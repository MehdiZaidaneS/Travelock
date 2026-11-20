
package com.example.travelock.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.example.travelock.data.model.Location
import com.example.travelock.data.model.Destination
import com.example.travelock.data.repository.LocationRepository
import com.example.travelock.data.repository.UserUnlockRepository
import com.example.travelock.data.repository.UserEnrolmentRepository
import com.example.travelock.data.location.DeviceLocationResult
import com.example.travelock.data.repository.DestinationRepository
import com.example.travelock.ui.components.DiscountCard

import com.example.travelock.ui.components.ExtendedLocationCard
import com.example.travelock.ui.components.LocationCard
import com.example.travelock.ui.components.TravelockSearchBar
import com.example.travelock.ui.visit.VisitFlowActions


@Composable
fun MainScreen(
    locationRepository: LocationRepository = remember { LocationRepository() },
    unlockRepository: UserUnlockRepository = remember { UserUnlockRepository() },
    destinationRepository: DestinationRepository = remember { DestinationRepository() },
    enrolmentRepository: UserEnrolmentRepository = remember { UserEnrolmentRepository() },
    visitActions: VisitFlowActions? = null,
    modifier: Modifier = Modifier,
    currentLocation: DeviceLocationResult?
) {
    val unlockRevision = visitActions?.revision ?: 0
    var searchQuery by remember { mutableStateOf("") }

    var locations by remember {
        mutableStateOf<List<Location>>(emptyList())
    }

    var discounts by remember {
        mutableStateOf<List<Destination>>(emptyList())
    }

    var selectedLocation by remember {
        mutableStateOf<Location?>(null)
    }

    var unlockedDestinationIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    var isLoadingUnlocks by remember {
        mutableStateOf(true)
    }

    var unlockError by remember {
        mutableStateOf<String?>(null)
    }
    var completedDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var selectedVisitTab by rememberSaveable { mutableStateOf(VisitListTab.PLANNED) }
    var unlockRetry by remember { mutableStateOf(0) }
    var enrolledDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var isLoadingEnrolments by remember { mutableStateOf(true) }
    var enrolmentError by remember { mutableStateOf<String?>(null) }
    var enrolmentRetry by remember { mutableStateOf(0) }
    LaunchedEffect(enrolmentRepository, unlockRevision, enrolmentRetry) {
        try {
            isLoadingEnrolments = true
            enrolmentError = null
            enrolledDestinations = enrolmentRepository.getMyEnrolmentsWithDestinations().mapNotNull { it.destination }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            enrolmentError = "Could not load your enrolled destinations."
        } finally {
            isLoadingEnrolments = false
        }
    }

    // CURRENT DEVICE LOCATION
    val availableLocation =
        currentLocation as? DeviceLocationResult.Available

    // GET USER UNLOCKS
    LaunchedEffect(unlockRepository, unlockRevision, unlockRetry) {
        try {
            isLoadingUnlocks = true
            unlockError = null

            val unlocks = unlockRepository.getMyUnlocksWithDestinations()

            unlockedDestinationIds = unlocks
                .map { it.destinationId }
                .toSet()
            completedDestinations = unlocks.sortedByDescending { it.unlockedAt }
                .mapNotNull { it.destination }.distinctBy { it.destinationId }

        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            unlockError = "Could not load your completed destinations."
        } finally {
            isLoadingUnlocks = false
        }
    }

    LaunchedEffect(destinationRepository){
        discounts = destinationRepository.getDestinationsWithDiscounts()
    }

    // SEARCH LOCATIONS
    LaunchedEffect(searchQuery, currentLocation) {
        val availableLocation =
            currentLocation as? DeviceLocationResult.Available

        locations = locationRepository.searchLocations(
            query = searchQuery,
            userLatitude = availableLocation?.latitude,
            userLongitude = availableLocation?.longitude
        )
    }

    // YOUR NEXT TARGETS
    // Only show the 3 closest incomplete locations.
    // Repository already sorts locations by distance.
    val nextTargets = remember(locations, unlockedDestinationIds) {
        locations
            .filter { location ->
                val unlockedCount = location.destinations.count { destination ->
                    destination.destinationId in unlockedDestinationIds
                }

                // Exclude locations where all destinations are unlocked.
                unlockedCount < location.destinations.size
            }
            .take(3)
    }

    // CHECK IF DESTINATION IS UNLOCKED
    fun isUnlocked(destinationId: String): Boolean {
        return destinationId in unlockedDestinationIds
    }

    // EXTENDED LOCATION SCREEN
    if (selectedLocation != null) {
        val location = selectedLocation!!

        val unlockedCount = location.destinations.count { destination ->
            destination.destinationId in unlockedDestinationIds
        }

        val isCompleted = unlockedCount == location.destinations.size

        ExtendedLocationCard(
            location = location,
            unlockedDestinationIds = unlockedDestinationIds,
            isLocked = !isCompleted,
            onBack = {
                selectedLocation = null
            },
            onDestinationSelected = { destination ->
                visitActions?.onOpenDestination?.invoke(destination, isUnlocked(destination.destinationId))
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
                onQueryChange = {
                    searchQuery = it
                }
            )
        }


        if (searchQuery.isBlank()){
            yourVisitsSection(
                selectedTab = selectedVisitTab, onTabSelected = { selectedVisitTab = it },
                planned = enrolledDestinations.filter { it.destinationId !in unlockedDestinationIds },
                completed = completedDestinations,
                loading = isLoadingUnlocks || (selectedVisitTab == VisitListTab.PLANNED && isLoadingEnrolments),
                error = unlockError ?: enrolmentError.takeIf { selectedVisitTab == VisitListTab.PLANNED },
                onRetry = { unlockRetry++; enrolmentRetry++ },
                onOpenDestination = { destination, unlocked -> visitActions?.onOpenDestination?.invoke(destination, unlocked) },
            )
            // PROMOTIONS HEADER
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Enjoy these promotions",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Discover destinations with special discounts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // PROMOTIONS
            item {
                val availableDiscounts = discounts.filter { destination ->
                    destination.destinationId !in unlockedDestinationIds
                }

                if (availableDiscounts.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(
                            items = availableDiscounts,
                            key = { "discount_${it.destinationId}" }
                        ) { destination ->

                            DiscountCard(
                                destination = destination,
                                isLocked = true,
                                onClick = {
                                    visitActions?.onOpenDestination?.invoke(destination, isUnlocked(destination.destinationId))
                                },
                                modifier = Modifier.width(280.dp)
                            )
                        }
                    }
                }
            }
        }





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

        // SELECT LOCATIONS TO DISPLAY
        val displayedLocations = if (searchQuery.isBlank()) {
            nextTargets
        } else {
            locations
        }

        // LOCATION CARDS
        if (isLoadingUnlocks) {
            item {
                CircularProgressIndicator()
            }
        } else if (displayedLocations.isEmpty()) {
            item {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "No new targets available."
                    } else {
                        "No locations found."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(
                items = displayedLocations,
                key = { it.locationId }
            ) { location ->

                val unlockedCount = location.destinations.count { destination ->
                    destination.destinationId in unlockedDestinationIds
                }

                val isCompleted = unlockedCount == location.destinations.size

                LocationCard(
                    location = location,
                    unlockedDestinationIds = unlockedDestinationIds,

                    // Next targets are always locked.
                    // Search results show their actual status.
                    isLocked = if (searchQuery.isBlank()) {
                        true
                    } else {
                        !isCompleted
                    },

                    userLatitude = availableLocation?.latitude,
                    userLongitude = availableLocation?.longitude,

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
