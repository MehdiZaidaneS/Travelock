
package com.example.travelock.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun HomePage(destinations: List<Destination>, locationClose: Location, nextLocations: List<Location>,  onLocationSelected: (Location) -> Unit, modifier: Modifier = Modifier) {

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101010)),

        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 24.dp
        ),

        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // SEARCH BAR
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = Color(0xFF252525)
            ) {

                Row(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 17.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.Gray
                    )

                    Text(
                        text = "Search for locations...",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // RECOMMENDED LOCATIONS
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Column {

                    Text(
                        text = "Enjoy these promotions",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Discover places and save money",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    items(destinations) { destination ->

                        if (destination.discount > 0)
                        DestinationCard(
                            destination = destination,
                            isLocked = true,
                            modifier = Modifier.width(300.dp)
                        )
                    }
                }
            }
        }

        // NEARBY LOCATIONS
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF81C784)
                    )

                    Text(
                        text = "Explore ${locationClose.name}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Discover interesting places around you",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // DESTINATION CARDS
        items(locationClose.destinations) { destination ->

            DestinationCard(
                destination = destination,
                isLocked = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Column {

                    Text(
                        text = "Your next targets",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Discover new locations...",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    items(nextLocations) { location ->

                       LocationCard(
                           location = location,
                           isLocked = true,
                           onViewDetails = {
                               onLocationSelected(location)
                           },
                           modifier = Modifier.width(300.dp)
                       )

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
