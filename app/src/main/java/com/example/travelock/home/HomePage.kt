package com.example.travelock.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.ui.components.TravelockSearchBar

@Composable
fun HomePage(
    destinations: List<Destination>,
    locationClose: Location,
    nextLocations: List<Location>,
    onLocationSelected: (Location) -> Unit,
    onDestinationSelected: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {

    // Search query state
    var searchQuery by remember { mutableStateOf("") }

    // Filter locations
    val filteredLocations = nextLocations.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // SEARCH BAR
        item {
            TravelockSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )
        }

        if (searchQuery == "")
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = "Enjoy these promotions",
                            color = Color.Black,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Discover places and save money",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (destinations.isEmpty()) {
                        Text(
                            text = "No promotions found.",
                            color = Color.Gray
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(destinations) { destination ->

                                DestinationCard(
                                    destination = destination,
                                    isLocked = true,
                                    onClick = {
                                        onDestinationSelected(destination)
                                    },
                                    modifier = Modifier.width(300.dp)
                                )
                            }
                        }
                    }
                }
            }

        // NEXT TARGETS
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (searchQuery == "")
                    Column {
                        Text(
                            text = "Your next targets",
                            color = Color.Black,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Discover new locations...",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                if (filteredLocations.isEmpty()) {
                    Text(
                        text = "No locations found.",
                        color = Color.Gray
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        filteredLocations.forEach { location ->
                            LocationCard(
                                location = location,
                                isLocked = true,
                                onViewDetails = {
                                    onLocationSelected(location)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM SPACING
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
