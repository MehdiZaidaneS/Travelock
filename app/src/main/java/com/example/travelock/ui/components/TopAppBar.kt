package com.example.travelock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.model.ThemeMode

/**
 * Top App Bar for authenticated Travelock views.
 * - Shows active exploring location badge in top-left when enrolled (Step 12 of Travelock.pdf).
 * - Provides quick Theme Mode menu (System / Light / Dark).
 * - Displays the Notification button at top-right with unread badge count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelockTopAppBar(
    title: String,
    activeLocationName: String? = null,
    unreadNotificationCount: Int = 0,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNotificationsClick: () -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    var showThemeMenu by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!activeLocationName.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Exploring $activeLocationName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        actions = {
            // Theme Mode selector dropdown
            IconButton(onClick = { showThemeMenu = true }) {
                val themeIcon = when (currentThemeMode) {
                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                }
                Icon(
                    imageVector = themeIcon,
                    contentDescription = "Change Theme"
                )
            }
            DropdownMenu(
                expanded = showThemeMenu,
                onDismissRequest = { showThemeMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("System Default") },
                    leadingIcon = { Icon(Icons.Default.BrightnessAuto, contentDescription = null) },
                    onClick = {
                        onThemeModeChange(ThemeMode.SYSTEM)
                        showThemeMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Light Mode") },
                    leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null) },
                    onClick = {
                        onThemeModeChange(ThemeMode.LIGHT)
                        showThemeMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Dark Mode") },
                    leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                    onClick = {
                        onThemeModeChange(ThemeMode.DARK)
                        showThemeMenu = false
                    }
                )
            }

            // Notification Bell Icon with Unread Badge
            IconButton(onClick = onNotificationsClick) {
                BadgedBox(
                    badge = {
                        if (unreadNotificationCount > 0) {
                            Badge {
                                Text(text = unreadNotificationCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

