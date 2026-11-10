package com.example.travelock.data.mock

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Location

/**
 * Realistic Helsinki mock Locations and nested Destinations based on Travelock.pdf and Objects.pdf.
 *
 * Domain terminology:
 * - Location: Broader district/area in Helsinki (e.g., Kallio, Myllypuro, Suomenlinna, Kamppi).
 * - Destination: Specific places inside a Location that users visit to unlock.
 */
object MockLocations {

    // ── 1. Kallio Destinations ──────────────────────────────────────────────────
    val kallioLibrary = Destination(
        destinationId = "dest-kallio-library",
        locationId = "loc-kallio",
        name = "Kallio Library",
        description = "Historic 1912 red-brick library in the heart of Kallio. Famous for its cozy reading rooms, spiral staircases, and lively community culture.",
        discount = 10f,
        latitude = 60.1842,
        longitude = 24.9539,
        image = "kallio_library",
        qrCode = "TRAVELOCK-QR-KALLIO-LIB-001"
    )

    val kallioChurch = Destination(
        destinationId = "dest-kallio-church",
        locationId = "loc-kallio",
        name = "Kallio Church",
        description = "Iconic granite National Romantic church designed by Lars Sonck (1912), crowning the Kallio hill with music composed by Jean Sibelius.",
        discount = 0f,
        latitude = 60.1843,
        longitude = 24.9493,
        image = "kallio_church",
        qrCode = "TRAVELOCK-QR-KALLIO-CHURCH-002"
    )

    val karhupuisto = Destination(
        destinationId = "dest-karhupuisto",
        locationId = "loc-kallio",
        name = "Bear Park (Karhupuisto)",
        description = "Beloved triangular park named after its 1931 red-granite bear statue, surrounded by indie cafés, vintage shops, and summer flower stalls.",
        discount = 15f,
        latitude = 60.1840,
        longitude = 24.9530,
        image = "karhupuisto",
        qrCode = "TRAVELOCK-QR-KALLIO-BEAR-003"
    )

    // ── 2. Myllypuro Destinations ───────────────────────────────────────────────
    val metropoliaMyllypuro = Destination(
        destinationId = "dest-metropolia-myllypuro",
        locationId = "loc-myllypuro",
        name = "Myllypuro Metropolia University",
        description = "Modern Campus of Builders at Metropolia University of Applied Sciences. Features rooftop terraces, smart labs, and vibrant student life.",
        discount = 20f,
        latitude = 60.2236,
        longitude = 25.0781,
        image = "metropolia_campus",
        qrCode = "TRAVELOCK-QR-MYLLYPURO-METRO-001"
    )

    val myllypuroSwimmingPool = Destination(
        destinationId = "dest-myllypuro-pool",
        locationId = "loc-myllypuro",
        name = "Myllypuro Swimming Pool & Sports Park",
        description = "Popular East Helsinki recreation hub offering indoor fitness facilities, floorball arenas, and scenic outdoor jogging trails.",
        discount = 15f,
        latitude = 60.2261,
        longitude = 25.0725,
        image = "myllypuro_pool",
        qrCode = "TRAVELOCK-QR-MYLLYPURO-POOL-002"
    )

    val myllypuroWaterTower = Destination(
        destinationId = "dest-myllypuro-tower",
        locationId = "loc-myllypuro",
        name = "Myllypuro Old Forest Trail",
        description = "Peaceful wooded ridge trail with glacial rock formations and WWI heritage trenches right next to the Myllypuro campus.",
        discount = 0f,
        latitude = 60.2215,
        longitude = 25.0810,
        image = "myllypuro_trail",
        qrCode = "TRAVELOCK-QR-MYLLYPURO-TRAIL-003"
    )

    // ── 3. Suomenlinna Destinations ─────────────────────────────────────────────
    val suomenlinnaFortress = Destination(
        destinationId = "dest-suomenlinna-fortress",
        locationId = "loc-suomenlinna",
        name = "Suomenlinna Sea Fortress",
        description = "UNESCO World Heritage 18th-century sea fortress spread across six linked islands off Helsinki's coast.",
        discount = 10f,
        latitude = 60.1454,
        longitude = 24.9881,
        image = "suomenlinna_fortress",
        qrCode = "TRAVELOCK-QR-SUOMENLINNA-FORT-001"
    )

    val kingsGate = Destination(
        destinationId = "dest-kings-gate",
        locationId = "loc-suomenlinna",
        name = "King's Gate (Kuninkaanportti)",
        description = "The ceremonial entrance to Suomenlinna built in 1753–1754 where King Adolf Frederick of Sweden anchored his ship.",
        discount = 0f,
        latitude = 60.1398,
        longitude = 24.9899,
        image = "kings_gate",
        qrCode = "TRAVELOCK-QR-SUOMENLINNA-GATE-002"
    )

    val suomenlinnaChurch = Destination(
        destinationId = "dest-suomenlinna-church",
        locationId = "loc-suomenlinna",
        name = "Suomenlinna Church & Lighthouse",
        description = "Built in 1854, its steeple doubles as a functioning maritime and air traffic lighthouse blinking the Morse code letter 'H' for Helsinki.",
        discount = 5f,
        latitude = 60.1478,
        longitude = 24.9862,
        image = "suomenlinna_church",
        qrCode = "TRAVELOCK-QR-SUOMENLINNA-CHURCH-003"
    )

    // ── 4. Kluuvi & Central Helsinki Destinations ───────────────────────────────
    val oodiLibrary = Destination(
        destinationId = "dest-oodi",
        locationId = "loc-kluuvi",
        name = "Oodi Central Library",
        description = "Helsinki's architectural wooden-and-glass living room across from Parliament House, offering 3D printers, studios, and panoramic rooftop views.",
        discount = 10f,
        latitude = 60.1741,
        longitude = 24.9382,
        image = "oodi_library",
        qrCode = "TRAVELOCK-QR-KLUUVI-OODI-001"
    )

    val helsinkiCathedral = Destination(
        destinationId = "dest-cathedral",
        locationId = "loc-kluuvi",
        name = "Helsinki Cathedral & Senate Square",
        description = "Neoclassical white cathedral designed by Carl Ludvig Engel overlooking the historic Senate Square.",
        discount = 0f,
        latitude = 60.1704,
        longitude = 24.9522,
        image = "helsinki_cathedral",
        qrCode = "TRAVELOCK-QR-KLUUVI-CATH-002"
    )

    // ── Locations List ──────────────────────────────────────────────────────────
    val kallio = Location(
        locationId = "loc-kallio",
        name = "Kallio",
        description = "Helsinki's lively bohemian district known for historic granite architecture, cozy parks, community libraries, and vibrant street culture.",
        latitude = 60.1841,
        longitude = 24.9514,
        image = "loc_kallio",
        destinations = listOf(kallioLibrary, kallioChurch, karhupuisto)
    )

    val myllypuro = Location(
        locationId = "loc-myllypuro",
        name = "Myllypuro",
        description = "Dynamic East Helsinki district home to the modern Metropolia University campus, sports parks, and scenic forest trails.",
        latitude = 60.2236,
        longitude = 25.0781,
        image = "loc_myllypuro",
        destinations = listOf(metropoliaMyllypuro, myllypuroSwimmingPool, myllypuroWaterTower)
    )

    val suomenlinna = Location(
        locationId = "loc-suomenlinna",
        name = "Suomenlinna",
        description = "UNESCO World Heritage sea fortress archipelago just a 15-minute ferry ride from Helsinki Market Square.",
        latitude = 60.1454,
        longitude = 24.9881,
        image = "loc_suomenlinna",
        destinations = listOf(suomenlinnaFortress, kingsGate, suomenlinnaChurch)
    )

    val kluuvi = Location(
        locationId = "loc-kluuvi",
        name = "Kluuvi & City Center",
        description = "The bustling heart of Helsinki featuring iconic neoclassical landmarks, central parks, and contemporary Finnish architecture.",
        latitude = 60.1720,
        longitude = 24.9450,
        image = "loc_kluuvi",
        destinations = listOf(oodiLibrary, helsinkiCathedral)
    )

    val allLocations: List<Location> = listOf(kallio, myllypuro, suomenlinna, kluuvi)

    val allDestinations: List<Destination> = allLocations.flatMap { it.destinations }

    // Default simulated user coordinates in central Helsinki (near Rautatientori / Kaisaniemi)
    const val MOCK_USER_LATITUDE = 60.1756
    const val MOCK_USER_LONGITUDE = 24.9442
}

