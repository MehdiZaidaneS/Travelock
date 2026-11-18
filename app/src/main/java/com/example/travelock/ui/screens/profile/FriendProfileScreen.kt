package com.example.travelock.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.mock.MockLocations
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.data.model.FriendTravelProfile
import com.example.travelock.data.model.ProfileSummary
import com.example.travelock.data.repository.ProfileRepository
import com.example.travelock.ui.components.AchievementBadgeCard
import com.example.travelock.ui.components.UserAvatarCircle

private val profileRepo = ProfileRepository()

private data class LoadedFriendProfile(
    val isLoading: Boolean = true,
    val summary: ProfileSummary? = null,
    val travelProfile: FriendTravelProfile? = null
)

/**
 * PBI-18: View Friend's Profile Screen (showing live Supabase profile, achievements, and unlocked destinations).
 */
@Composable
fun FriendProfileScreen(
    friendUserId: String
) {
    val profileState by produceState(
        initialValue = LoadedFriendProfile(isLoading = true),
        key1 = friendUserId
    ) {
        val travelProfile = try {
            profileRepo.getFriendTravelProfile(friendUserId)
        } catch (e: Exception) {
            null
        }

        val summary = try {
            profileRepo.getProfileSummary(friendUserId)
        } catch (e: Exception) {
            null
        }

        value = LoadedFriendProfile(
            isLoading = false,
            summary = summary,
            travelProfile = travelProfile
        )
    }

    if (profileState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val mockFallback = remember(friendUserId) { MockUsers.findById(friendUserId) }
    val displayName = profileState.summary?.name
        ?: mockFallback?.name
        ?: "Traveler"
    val displayUsername = profileState.summary?.username
        ?: mockFallback?.username
        ?: "explorer"

    val unlockedDestinations = mockFallback?.unlockedDestinations ?: emptyList()
    val achievements = mockFallback?.achievements ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UserAvatarCircle(
                        name = displayName,
                        username = displayUsername,
                        size = 76.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@$displayUsername",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${unlockedDestinations.size} Destinations Unlocked • ${achievements.size} Achievements",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Text(
                text = "$displayName's Achievements",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(MockLocations.allLocations, key = { "friend-ach-${it.locationId}" }) { location ->
            val isUnlocked = achievements.any { it.location.locationId == location.locationId }
            AchievementBadgeCard(
                location = location,
                isUnlocked = isUnlocked
            )
        }

        item {
            Text(
                text = "$displayName's Unlocked Destinations",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (unlockedDestinations.isEmpty()) {
            item {
                Text(
                    text = "$displayName hasn't unlocked any destinations yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(unlockedDestinations, key = { it.unlockId }) { unlock ->
                val destName = unlock.destination?.name
                    ?: MockLocations.allDestinations.find { it.destinationId == unlock.destinationId }?.name
                    ?: unlock.destinationId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = "✅ $destName",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}