package com.example.travelock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.example.travelock.data.model.Location
import com.example.travelock.data.model.Destination
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ExtendedLocationCard(
    location: Location,
    isLocked: Boolean,
    onBack: () -> Unit,
    onDestinationSelected: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
    ) {

        // LOCATION IMAGE
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                AsyncImage(
                    model = location.image,
                    contentDescription = location.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Dark gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.8f)
                                )
                            )
                        )
                )

                // BACK BUTTON
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            Color.White.copy(alpha = 0.7f),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go back",
                    )
                }

                // LOCATION NAME
                Text(
                    text = location.name,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // LOCATION INFORMATION
        item {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = location.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black
                )

                // DESTINATIONS HEADING
                Text(
                    text = "Destinations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // LOCK STATUS
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked)
                            Icons.Default.Lock
                        else
                            Icons.Default.LockOpen,
                        contentDescription = null,

                    )

                    Text(
                        text = if (isLocked)
                            "${location.destinations.size}/3 destinations to unlock..."
                        else
                            "Keep discovering ${location.name}",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // DESTINATION CARDS
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                location.destinations.forEach { destination ->

                    DestinationCard(
                        destination = destination,
                        isLocked = true,
                        onClick = {
                            onDestinationSelected(destination)
                        }
                    )
                }
            }
        }

        // BOTTOM SPACING
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
