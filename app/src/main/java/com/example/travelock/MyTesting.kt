
package com.example.travelock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.travelock.home.ExtendedDestinationCard

import com.example.travelock.home.Destination
import com.example.travelock.home.ExtendedLocationCard
import com.example.travelock.home.HomePage
import com.example.travelock.home.Location
import com.example.travelock.ui.theme.TravelockTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme

// HELSINKI DESTINATIONS
val helsinkiDestinations = listOf(
    Destination(
        destination_id = "dest_001",
        location_id = "loc_001",
        name = "Helsinki Cathedral",
        description = "Visit the iconic white cathedral.",
        discount = 10f,
        latitude = 60.1699f,
        longitude = 24.9524f,
        image = "https://images.unsplash.com/photo-1533044309907-0fa3413da946",
        qr_code = "qr_001"
    ),
    Destination(
        destination_id = "dest_002",
        location_id = "loc_001",
        name = "Moomin Shop",
        description = "Discover the world of Moomins.",
        discount = 10f,
        latitude = 60.1699f,
        longitude = 24.9524f,
        image = "https://www.visitfinland.com/dam/jcr:6440f5ae-cf74-42af-bc56-aaddcb1cce75/MyStar%20Moominshop%202022.jpg",
        qr_code = "qr_002"
    ),
    Destination(
        destination_id = "dest_003",
        location_id = "loc_001",
        name = "Market Square",
        description = "Enjoy food and local products.",
        discount = 0f,
        latitude = 60.1675f,
        longitude = 24.9522f,
        image = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4",
        qr_code = "qr_003"
    )
)

// ESPOO DESTINATIONS
val espooDestinations = listOf(
    Destination(
        destination_id = "dest_004",
        location_id = "loc_002",
        name = "Nuuksio National Park",
        description = "Explore Finnish forests and lakes.",
        discount = 0f,
        latitude = 60.3000f,
        longitude = 24.5000f,
        image = "https://images.unsplash.com/photo-1448375240586-882707db888b",
        qr_code = "qr_004"
    ),
    Destination(
        destination_id = "dest_005",
        location_id = "loc_002",
        name = "EMMA Museum",
        description = "Discover modern and contemporary art.",
        discount = 10f,
        latitude = 60.1770f,
        longitude = 24.7380f,
        image = "https://images.unsplash.com/photo-1577083552431-6e5fd01aa342",
        qr_code = "qr_005"
    )
)

// VANTAA DESTINATIONS
val vantaaDestinations = listOf(
    Destination(
        destination_id = "dest_006",
        location_id = "loc_003",
        name = "Heureka",
        description = "Explore science through interactive exhibits.",
        discount = 0f,
        latitude = 60.2890f,
        longitude = 25.0430f,
        image = "https://images.unsplash.com/photo-1532094349884-543bc11b234d",
        qr_code = "qr_006"
    ),
    Destination(
        destination_id = "dest_007",
        location_id = "loc_003",
        name = "Flamingo Spa",
        description = "Relax and enjoy a spa experience.",
        discount = 10f,
        latitude = 60.2910f,
        longitude = 24.9650f,
        image = "https://images.unsplash.com/photo-1540555700478-4be289fbecef",
        qr_code = "qr_007"
    )
)

// LOCATIONS
val helsinki = Location(
    location_id = "loc_001",
    name = "Helsinki",
    description = "Explore the Finnish capital.",
    latitude = 60.1699f,
    longitude = 24.9384f,
    image = "https://image.tallink.com/image/upload/f_auto,fl_lossy,q_auto:best,h_1050,w_2448/travel/destinations/finland/helsinki/helsinki-15-wide.jpg",
    destinations = helsinkiDestinations
)

val espoo = Location(
    location_id = "loc_002",
    name = "Espoo",
    description = "Discover nature, culture and experiences.",
    latitude = 60.2055f,
    longitude = 24.6559f,
    image = "https://images.unsplash.com/photo-1470770841072-f978cf4d019e",
    destinations = espooDestinations
)

val vantaa = Location(
    location_id = "loc_003",
    name = "Vantaa",
    description = "Find exciting activities and experiences.",
    latitude = 60.2934f,
    longitude = 25.0378f,
    image = "https://images.unsplash.com/photo-1518005020951-eccb494ad742",
    destinations = vantaaDestinations
)

// ALL DESTINATIONS
val destinations = helsinkiDestinations + espooDestinations + vantaaDestinations

// LOCATIONS TO RECOMMEND
val nextLocations = listOf(espoo, vantaa)

class MyTesting : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            TravelockTheme {

                // Selected location
                var selectedLocation by remember {
                    mutableStateOf<Location?>(null)
                }

                // Selected destination
                var selectedDestination by remember {
                    mutableStateOf<Destination?>(null)
                }

                BackHandler(
                    enabled = selectedDestination != null || selectedLocation != null
                ) {
                    when {
                        // If destination is open, close it first
                        selectedDestination != null -> {
                            selectedDestination = null
                        }

                        // If location is open, return to HomePage
                        selectedLocation != null -> {
                            selectedLocation = null
                        }
                    }
                }


                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {

                        // 1. Always display the underlying screen
                        when {
                            selectedLocation != null -> {
                                ExtendedLocationCard(
                                    location = selectedLocation!!,
                                    isLocked = true,
                                    onBack = {
                                        selectedLocation = null
                                    },
                                    onDestinationSelected = { destination ->
                                        selectedDestination = destination
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            else -> {
                                HomePage(
                                    destinations = destinations,
                                    locationClose = vantaa,
                                    nextLocations = nextLocations,
                                    onLocationSelected = { location ->
                                        selectedLocation = location
                                    },
                                    onDestinationSelected = { destination ->
                                        selectedDestination = destination
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // 2. Display destination as an overlay
                        if (selectedDestination != null) {
                            ExtendedDestinationCard(
                                destination = selectedDestination!!,
                                isLocked = true,
                                onBack = {
                                    selectedDestination = null
                                },
                                onUnlockCamera = {
                                    // TODO: Open camera
                                },
                                onUnlockQr = {
                                    // TODO: Open QR scanner
                                }
                            )
                        }
                    }
                }

            }
        }
    }
}