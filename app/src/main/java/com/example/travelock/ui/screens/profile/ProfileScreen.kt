package com.example.travelock.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.model.User
import com.example.travelock.ui.components.AchievementBadgeCard
import com.example.travelock.ui.components.ProfilePhotoAvatar
import com.example.travelock.ui.components.ThemeSwitcher
import com.example.travelock.ui.theme.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * PBI-26 to PBI-30: Personal User Profile Screen backed by live Supabase state.
 * Displays user info, exploration stats, Achievements (unlocked vs locked),
 * Unlocked Destinations, Friends list, App Theme Switcher, Edit Profile button, and Logout button.
 */
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onEditProfileClick: () -> Unit,
    onFriendClick: (String) -> Unit,
    onLogoutClick: () -> Unit,
    onRefresh: () -> Unit = {},
    allowProfileEditing: Boolean = true,
) {
    val user = remember(uiState.userId, uiState.username, uiState.name, uiState.email, uiState.profileImage, uiState.createdAt) {
        uiState.toDisplayUser()
    }
    val allDestinations = remember(uiState.allLocations) {
        uiState.allLocations.flatMap { it.destinations }
    }
    val joinedText = remember(uiState.createdAt) {
        val formatter = DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault())
        "Joined ${formatter.format(uiState.createdAt)}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Header Card (PBI-26)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !uiState.isLoading && !uiState.isRefreshing
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh profile"
                            )
                        }
                    }

                    ProfilePhotoAvatar(user = user, size = 80.dp)

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.name.ifBlank { "Travelock Traveler" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = buildString {
                        if (uiState.username.isNotBlank()) append("@${uiState.username}")
                        if (uiState.email.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(uiState.email)
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = joinedText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatColumn(count = uiState.unlocks.size, label = "Unlocked")
                        ProfileStatColumn(count = uiState.achievements.size, label = "Achievements")
                        ProfileStatColumn(count = uiState.friends.size, label = "Friends")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onEditProfileClick,
                            enabled = allowProfileEditing && !uiState.isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text("Edit Profile")
                        }

                        Button(
                            onClick = onLogoutClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text("Log Out")
                        }
                    }
                }
            }
        }

        // Loading / Error Banner
        if (uiState.isLoading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Loading profile from Supabase…")
                    }
                }
            }
        } else if (!uiState.errorMessage.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Theme Mode Switcher Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Appearance & Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeSwitcher(
                        currentMode = currentThemeMode,
                        onModeSelected = onThemeModeChange
                    )
                }
            }
        }

        // Achievements Section (PBI-27)
        item {
            Text(
                text = "Achievements (${uiState.achievements.size}/${uiState.allLocations.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.allLocations.isEmpty() && !uiState.isLoading) {
            item {
                Text(
                    text = "No locations available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(uiState.allLocations, key = { "ach-${it.locationId}" }) { location ->
                val unlocked = uiState.achievements.any { it.location.locationId == location.locationId }
                AchievementBadgeCard(
                    location = location,
                    isUnlocked = unlocked
                )
            }
        }

        // Unlocked Destinations Section (PBI-26)
        item {
            Text(
                text = "Unlocked Destinations (${uiState.unlocks.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.unlocks.isEmpty() && !uiState.isLoading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = "You haven't unlocked any destinations yet. Explore a location to unlock places!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(uiState.unlocks, key = { it.unlockId }) { unlock ->
                val destName = unlock.destination?.name
                    ?: allDestinations.find { it.destinationId == unlock.destinationId }?.name
                    ?: unlock.destinationId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "✅ $destName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Visited",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Friends List Section (PBI-28)
        item {
            Text(
                text = "Friends (${uiState.friends.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.friends.isEmpty() && !uiState.isLoading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = "No accepted friends yet. Search for travelers in the Feed tab to connect!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(uiState.friends, key = { "friend-${it.friendshipId}" }) { friend ->
                val friendName = friend.profile?.name?.ifBlank { null } ?: "Travelock Traveler"
                val friendUsername = friend.profile?.username?.ifBlank { null } ?: "traveler"
                val friendUser = User(
                    userId = friend.userId,
                    username = friendUsername,
                    name = friendName,
                    email = "",
                    profileImage = friend.profile?.profileImage.orEmpty(),
                    createdAt = Instant.EPOCH,
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFriendClick(friend.userId) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfilePhotoAvatar(user = friendUser, size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friendName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@$friendUsername",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "View Profile",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatColumn(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
