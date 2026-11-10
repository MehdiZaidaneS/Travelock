
package com.example.travelock.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage


data class Location(
    val location_id: String,
    val name: String,
    val description: String,
    val latitude: Float,
    val longitude: Float,
    val image: String,
    val destinations: List<Destination>
)

data class Destination(
    val destination_id: String,
    val location_id: String,
    val name: String,
    val description: String,
    val discount: Float,
    val latitude: Float,
    val longitude: Float,
    val image: String,
    val qr_code: String
)


@Composable
fun LocationCard(
    location: Location,
    isLocked: Boolean,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier
){

    Card(
        onClick = onViewDetails,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        )
    ) {

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            AsyncImage(
                model = location.image,
                contentDescription = location.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dark overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Card content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                // Lock status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {

                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = if (isLocked)
                            Color.Red.copy(alpha = 0.65f)
                        else
                            Color.Blue.copy(alpha = 0.65f)
                    ) {

                        Row(
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 7.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {

                            Icon(
                                imageVector = if (isLocked)
                                    Icons.Default.Lock
                                else
                                    Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )

                            Text(
                                text = if (isLocked) "Locked" else "Unlocked",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Location information
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {



                    Text(
                        text = location.name,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Distance: ~2 km",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row{
                        Text(
                            text = "0/3 destinations unlocked",
                            color = Color.White,
                            fontStyle = FontStyle.Italic,
                        )
                    }


                }
            }
        }
    }
}



