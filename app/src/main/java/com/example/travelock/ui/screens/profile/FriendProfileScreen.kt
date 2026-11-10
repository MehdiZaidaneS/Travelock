package com.example.travelock.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.mock.MockLocations
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.ui.components.AchievementBadgeCard
import com.example.travelock.ui.components.UserAvatarCircle

/**
 * PBI-18: View Friend's Profile Screen (showing friend's achievements and unlocked destinations).
 */
@Composable
fun FriendProfileScreen(
    friendUserId: String
) {
    val friend = remember(friendUserId) {
        MockUsers.findById(friendUserId) ?: MockUsers.amir
    }

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
                        name = friend.name,
                        username = friend.username,
                        size = 76.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = friend.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${friend.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${friend.unlockedDestinations.size} Destinations Unlocked • ${friend.achievements.size} Achievements",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Text(
                text = "${friend.name}'s Achievements",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(MockLocations.allLocations, key = { "friend-ach-${it.locationId}" }) { location ->
            val isUnlocked = friend.achievements.any { it.location.locationId == location.locationId }
            AchievementBadgeCard(
                location = location,
                isUnlocked = isUnlocked
            )
        }

        item {
            Text(
                text = "${friend.name}'s Unlocked Destinations",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (friend.unlockedDestinations.isEmpty()) {
            item {
                Text(
                    text = "${friend.name} hasn't unlocked any destinations yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(friend.unlockedDestinations, key = { it.unlockId }) { unlock ->
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

