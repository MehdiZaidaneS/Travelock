package com.example.travelock.data.mock

import com.example.travelock.data.model.User
import java.time.Instant

/**
 * Sample users for Travelock based on the Group 5 team members and extra discoverable Helsinki users.
 */
object MockUsers {

    val amir = User(
        userId = "user-amir",
        username = "amir_n",
        name = "Amir Noori",
        email = "amir@travelock.fi",
        profileImage = "avatar_amir",
        createdAt = Instant.parse("2026-11-01T09:00:00Z"),
        unlockedDestinations = MockAchievements.amirUnlocks,
        achievements = MockAchievements.amirAchievements
    )

    val jesper = User(
        userId = "user-jesper",
        username = "jesper_h",
        name = "Jesper Holmström",
        email = "jesper@travelock.fi",
        profileImage = "avatar_jesper",
        createdAt = Instant.parse("2026-11-01T09:30:00Z"),
        unlockedDestinations = MockAchievements.jesperUnlocks,
        achievements = MockAchievements.jesperAchievements
    )

    val osama = User(
        userId = "user-osama",
        username = "osama_a",
        name = "Osama Aamer",
        email = "osama@travelock.fi",
        profileImage = "avatar_osama",
        createdAt = Instant.parse("2026-11-01T10:00:00Z"),
        unlockedDestinations = MockAchievements.osamaUnlocks,
        achievements = MockAchievements.osamaAchievements
    )

    val sanna = User(
        userId = "user-sanna",
        username = "sanna_k",
        name = "Sanna Korhonen",
        email = "sanna@travelock.fi",
        profileImage = "avatar_sanna",
        createdAt = Instant.parse("2026-11-02T12:00:00Z"),
        unlockedDestinations = emptyList(),
        achievements = emptyList()
    )

    val mikko = User(
        userId = "user-mikko",
        username = "mikko_l",
        name = "Mikko Virtanen",
        email = "mikko@travelock.fi",
        profileImage = "avatar_mikko",
        createdAt = Instant.parse("2026-11-03T14:20:00Z"),
        unlockedDestinations = emptyList(),
        achievements = emptyList()
    )

    val currentUser = User(
        userId = "user-mehdi",
        username = "mehdi_z",
        name = "Mehdi Zaidane",
        email = "mehdi@travelock.fi",
        profileImage = "avatar_mehdi",
        createdAt = Instant.parse("2026-11-01T08:00:00Z"),
        friends = listOf(amir, jesper),
        lockedLocations = listOf(MockLocations.kallio, MockLocations.myllypuro, MockLocations.suomenlinna),
        lockedDestinations = MockLocations.allDestinations.filter { dest ->
            MockAchievements.mehdiUnlocks.none { it.destinationId == dest.destinationId }
        },
        unlockedDestinations = MockAchievements.mehdiUnlocks,
        achievements = MockAchievements.mehdiAchievements
    )

    val allUsers: List<User> = listOf(currentUser, amir, jesper, osama, sanna, mikko)

    fun findById(userId: String): User? = allUsers.find { it.userId == userId }
}

