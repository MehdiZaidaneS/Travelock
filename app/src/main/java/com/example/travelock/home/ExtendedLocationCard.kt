
package com.example.travelock.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // Location image
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
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )

                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go back",
                        tint = Color.White
                    )
                }

                // Location name
                Text(
                    text = location.name,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Location information
        item {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Lock status
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
                        tint = Color.White
                    )

                    Text(
                        text = if (isLocked) "3 destinations to more unlock..." else "Keep discovering ${location.name}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(
                    color = Color.DarkGray
                )

                // Description
                Text(
                    text = location.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray
                )

                // Destinations heading
                Text(
                    text = "Destinations",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )



                Text(
                    text = "${location.destinations.size} destinations to explore",
                    color = Color.Gray
                )
            }
        }

        // Destination list
        items(location.destinations) { destination ->

            DestinationCard(destination = destination, isLocked = true)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
