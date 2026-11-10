
package com.example.travelock.data.mock

import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Location

object MockLocations {

    // ─────────────── KALLIO DESTINATIONS ───────────────

    val kallioLibrary = Destination(
        destinationId = "dest-kallio-library",
        locationId = "loc-kallio",
        name = "Kallio Library",
        description = "Historic 1912 red-brick library in the heart of Kallio. Famous for its cozy reading rooms, spiral staircases, and lively community culture.",
        discount = 10f,
        latitude = 60.1842,
        longitude = 24.9539,
        image = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSNFexzzjyec5kNRg8Z3oATfNwe1Qch9ljPdSPlS7mFFg&s=10",
        qrCode = "TRAVELOCK-QR-KALLIO-LIB-001"
    )

    val kallioChurch = Destination(
        destinationId = "dest-kallio-church",
        locationId = "loc-kallio",
        name = "Kallio Church",
        description = "Iconic granite National Romantic church designed by Lars Sonck in 1912.",
        discount = 0f,
        latitude = 60.1843,
        longitude = 24.9493,
        image = "https://www.myhelsinki.fi/wp-content/uploads/2024/09/2d784887-02dd-4435-a170-2ea98cbf96d9-853x1280.jpg",
        qrCode = "TRAVELOCK-QR-KALLIO-CHURCH-002"
    )

    val karhupuisto = Destination(
        destinationId = "dest-karhupuisto",
        locationId = "loc-kallio",
        name = "Bear Park (Karhupuisto)",
        description = "Beloved triangular park named after its red-granite bear statue, surrounded by independent cafés and vintage shops.",
        discount = 15f,
        latitude = 60.1840,
        longitude = 24.9530,
        image = "https://static.stadissa.fi/places/8a0e5f37c85c66c8eb3107ad6240ec76.JPG",
        qrCode = "TRAVELOCK-QR-KALLIO-BEAR-003"
    )

    // ─────────────── MYLLYPURO DESTINATIONS ───────────────

    val metropoliaMyllypuro = Destination(
        destinationId = "dest-metropolia-myllypuro",
        locationId = "loc-myllypuro",
        name = "Myllypuro Metropolia University",
        description = "Modern Metropolia University campus featuring smart labs, study spaces and vibrant student life.",
        discount = 20f,
        latitude = 60.2236,
        longitude = 25.0781,
        image = "https://www.metropolia.fi/sites/default/files/images/Kampukset/myllypuron-kampus-ilmakuva.jpg",
        qrCode = "TRAVELOCK-QR-MYLLYPURO-METRO-001"
    )

    val myllypuroSwimmingPool = Destination(
        destinationId = "dest-myllypuro-pool",
        locationId = "loc-myllypuro",
        name = "Myllypuro Swimming Pool & Sports Park",
        description = "Popular East Helsinki recreation hub offering sports facilities, swimming and outdoor activities.",
        discount = 15f,
        latitude = 60.2261,
        longitude = 25.0725,
        image = "https://stplattaprod.blob.core.windows.net/helfikuvaprod/styles/1_5_927w_618h/azure/testi_yrjonkadun_uimahalli_2021_kuva_maarit_hohteri_18.jpg.webp?h=81e9ca55&itok=14jzvFe2",
        qrCode = "TRAVELOCK-QR-MYLLYPURO-POOL-002"
    )

    val myllypuroWaterTower = Destination(
        destinationId = "dest-myllypuro-tower",
        locationId = "loc-myllypuro",
        name = "Myllypuro Old Forest Trail",
        description = "Peaceful wooded area with natural rock formations and historical trenches near the Myllypuro campus.",
        discount = 0f,
        latitude = 60.2215,
        longitude = 25.0810,
        image = "https://riveri68.blogaaja.fi/files/2023/03/etualalla-vesitorni-1024x702.jpg",
        qrCode = "TRAVELOCK-QR-MYLLYPURO-TRAIL-003"
    )

    // ─────────────── SUOMENLINNA DESTINATIONS ───────────────

    val suomenlinnaFortress = Destination(
        destinationId = "dest-suomenlinna-fortress",
        locationId = "loc-suomenlinna",
        name = "Suomenlinna Sea Fortress",
        description = "UNESCO World Heritage 18th-century sea fortress spread across six linked islands off Helsinki's coast.",
        discount = 10f,
        latitude = 60.1454,
        longitude = 24.9881,
        image = "https://suomenlinna.fi/wp-content/uploads/2025/04/23A08-108k.jpg",
        qrCode = "TRAVELOCK-QR-SUOMENLINNA-FORT-001"
    )

    val kingsGate = Destination(
        destinationId = "dest-kings-gate",
        locationId = "loc-suomenlinna",
        name = "King's Gate (Kuninkaanportti)",
        description = "The ceremonial entrance to Suomenlinna built in 1753–1754.",
        discount = 0f,
        latitude = 60.1398,
        longitude = 24.9899,
        image = "https://historia.hel.fi/sites/default/files/styles/large/public/hkm.HKMS000005_000002l7-0-master.jpg",
        qrCode = "TRAVELOCK-QR-SUOMENLINNA-GATE-002"
    )

    val suomenlinnaChurch = Destination(
        destinationId = "dest-suomenlinna-church",
        locationId = "loc-suomenlinna",
        name = "Suomenlinna Church & Lighthouse",
        description = "Historic church built in 1854 whose steeple also functions as a maritime and air traffic lighthouse.",
        discount = 5f,
        latitude = 60.1478,
        longitude = 24.9862,
        image = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTShDmIUlGJFjK48EHOc1JPGTJ_RvKS6g6r3PdFgejdfWuWMgveR8_ZTb8&s=10",
        qrCode = "TRAVELOCK-QR-SUOMENLINNA-CHURCH-003"
    )

    // ─────────────── KLUVVI DESTINATIONS ───────────────

    val oodiLibrary = Destination(
        destinationId = "dest-oodi",
        locationId = "loc-kluuvi",
        name = "Oodi Central Library",
        description = "Helsinki's modern central library featuring creative spaces, 3D printers, studios and beautiful architecture.",
        discount = 10f,
        latitude = 60.1741,
        longitude = 24.9382,
        image = "https://oodihelsinki.fi/wp-content/uploads/2018/12/Oodi_verkkosivut_hero-kuvat_1920x1080_TuomasUusheimo_07.jpg",
        qrCode = "TRAVELOCK-QR-KLUUVI-OODI-001"
    )

    val helsinkiCathedral = Destination(
        destinationId = "dest-cathedral",
        locationId = "loc-kluuvi",
        name = "Helsinki Cathedral & Senate Square",
        description = "Neoclassical white cathedral designed by Carl Ludvig Engel overlooking historic Senate Square.",
        discount = 0f,
        latitude = 60.1704,
        longitude = 24.9522,
        image = "https://helsingintuomiokirkko.fi/material/images/wt6eYOfdf/tuomiokirkko_suoraanedesta_kirkkosivuille_hemmila.jpg",
        qrCode = "TRAVELOCK-QR-KLUUVI-CATH-002"
    )

    // ─────────────── LOCATIONS ───────────────

    val kallio = Location(
        locationId = "loc-kallio",
        name = "Kallio",
        description = "Helsinki's lively bohemian district known for historic architecture, cozy parks, community libraries and vibrant street culture.",
        latitude = 60.1841,
        longitude = 24.9514,
        image = "https://www.myhelsinki.fi/wp-content/uploads/2024/09/2d784887-02dd-4435-a170-2ea98cbf96d9-853x1280.jpg",
        destinations = listOf(
            kallioLibrary,
            kallioChurch,
            karhupuisto
        )
    )

    val myllypuro = Location(
        locationId = "loc-myllypuro",
        name = "Myllypuro",
        description = "Dynamic East Helsinki district home to the modern Metropolia University campus, sports parks and forest trails.",
        latitude = 60.2236,
        longitude = 25.0781,
        image = "https://riveri68.blogaaja.fi/files/2023/03/etualalla-vesitorni-1024x702.jpg",
        destinations = listOf(
            metropoliaMyllypuro,
            myllypuroSwimmingPool,
            myllypuroWaterTower
        )
    )

    val suomenlinna = Location(
        locationId = "loc-suomenlinna",
        name = "Suomenlinna",
        description = "UNESCO World Heritage sea fortress archipelago just a short ferry ride from Helsinki Market Square.",
        latitude = 60.1454,
        longitude = 24.9881,
        image = "https://historia.hel.fi/sites/default/files/styles/large/public/hkm.HKMS000005_000002l7-0-master.jpg",
        destinations = listOf(
            suomenlinnaFortress,
            kingsGate,
            suomenlinnaChurch
        )
    )

    val kluuvi = Location(
        locationId = "loc-kluuvi",
        name = "Kluuvi & City Center",
        description = "The bustling heart of Helsinki featuring iconic neoclassical landmarks, central parks and contemporary Finnish architecture.",
        latitude = 60.1720,
        longitude = 24.9450,
        image = "https://oodihelsinki.fi/wp-content/uploads/2018/12/Oodi_verkkosivut_hero-kuvat_1920x1080_TuomasUusheimo_07.jpg",
        destinations = listOf(
            oodiLibrary,
            helsinkiCathedral
        )
    )

    // ─────────────── ALL DATA ───────────────

    val allLocations: List<Location> = listOf(
        kallio,
        myllypuro,
        suomenlinna,
        kluuvi
    )

    val allDestinations: List<Destination> =
        allLocations.flatMap { it.destinations }

    // ─────────────── DEFAULT SIMULATED USER COORDINATES ───────────────

    const val MOCK_USER_LATITUDE = 60.1756
    const val MOCK_USER_LONGITUDE = 24.9442
}
