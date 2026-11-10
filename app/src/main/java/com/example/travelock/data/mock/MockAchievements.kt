package com.example.travelock.data.mock

import com.example.travelock.data.model.UserAchievement
import com.example.travelock.data.model.UserUnlock
import java.time.Instant

/**
 * Sample unlocked destinations ([UserUnlock]) and completed location achievements ([UserAchievement])
 * for the current user and friends.
 */
object MockAchievements {

    // Current user (mehdi_z) starts with 1 destination unlocked in Myllypuro so Kallio is ready
    // for the 23-step walkthrough (or partially unlocked).
    val mehdiUnlocks = listOf(
        UserUnlock(
            unlockId = "unlock-mehdi-1",
            userId = "user-mehdi",
            destinationId = MockLocations.metropoliaMyllypuro.destinationId,
            unlockedAt = Instant.parse("2026-11-05T10:15:00Z"),
            destination = MockLocations.metropoliaMyllypuro
        ),
        UserUnlock(
            unlockId = "unlock-mehdi-2",
            userId = "user-mehdi",
            destinationId = MockLocations.oodiLibrary.destinationId,
            unlockedAt = Instant.parse("2026-11-06T14:30:00Z"),
            destination = MockLocations.oodiLibrary
        )
    )

    val mehdiAchievements = listOf(
        UserAchievement(
            achievementId = "ach-mehdi-1",
            userId = "user-mehdi",
            location = MockLocations.kluuvi,
            level = 1,
            unlockedAt = Instant.parse("2026-11-06T15:00:00Z")
        )
    )

    // Friend 1: Amir Noori
    val amirUnlocks = listOf(
        UserUnlock(
            unlockId = "unlock-amir-1",
            userId = "user-amir",
            destinationId = MockLocations.suomenlinnaFortress.destinationId,
            unlockedAt = Instant.parse("2026-11-07T11:20:00Z"),
            destination = MockLocations.suomenlinnaFortress
        ),
        UserUnlock(
            unlockId = "unlock-amir-2",
            userId = "user-amir",
            destinationId = MockLocations.kingsGate.destinationId,
            unlockedAt = Instant.parse("2026-11-07T12:45:00Z"),
            destination = MockLocations.kingsGate
        ),
        UserUnlock(
            unlockId = "unlock-amir-3",
            userId = "user-amir",
            destinationId = MockLocations.suomenlinnaChurch.destinationId,
            unlockedAt = Instant.parse("2026-11-07T13:30:00Z"),
            destination = MockLocations.suomenlinnaChurch
        )
    )

    val amirAchievements = listOf(
        UserAchievement(
            achievementId = "ach-amir-suomenlinna",
            userId = "user-amir",
            location = MockLocations.suomenlinna,
            level = 1,
            unlockedAt = Instant.parse("2026-11-07T13:30:00Z")
        )
    )

    // Friend 2: Jesper Holmström
    val jesperUnlocks = listOf(
        UserUnlock(
            unlockId = "unlock-jesper-1",
            userId = "user-jesper",
            destinationId = MockLocations.metropoliaMyllypuro.destinationId,
            unlockedAt = Instant.parse("2026-11-04T09:00:00Z"),
            destination = MockLocations.metropoliaMyllypuro
        ),
        UserUnlock(
            unlockId = "unlock-jesper-2",
            userId = "user-jesper",
            destinationId = MockLocations.myllypuroSwimmingPool.destinationId,
            unlockedAt = Instant.parse("2026-11-04T11:00:00Z"),
            destination = MockLocations.myllypuroSwimmingPool
        ),
        UserUnlock(
            unlockId = "unlock-jesper-3",
            userId = "user-jesper",
            destinationId = MockLocations.myllypuroWaterTower.destinationId,
            unlockedAt = Instant.parse("2026-11-04T12:15:00Z"),
            destination = MockLocations.myllypuroWaterTower
        )
    )

    val jesperAchievements = listOf(
        UserAchievement(
            achievementId = "ach-jesper-myllypuro",
            userId = "user-jesper",
            location = MockLocations.myllypuro,
            level = 1,
            unlockedAt = Instant.parse("2026-11-04T12:15:00Z")
        )
    )

    // Friend 3: Osama Aamer
    val osamaUnlocks = listOf(
        UserUnlock(
            unlockId = "unlock-osama-1",
            userId = "user-osama",
            destinationId = MockLocations.kallioChurch.destinationId,
            unlockedAt = Instant.parse("2026-11-08T16:10:00Z"),
            destination = MockLocations.kallioChurch
        )
    )

    val osamaAchievements = listOf<UserAchievement>()
}

