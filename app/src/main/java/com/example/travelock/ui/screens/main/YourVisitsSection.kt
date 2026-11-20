package com.example.travelock.ui.screens.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.travelock.data.model.Destination
import com.example.travelock.ui.components.DestinationCard

enum class VisitListTab { PLANNED, COMPLETED }

/** Uses the home list's scrolling and the shared destination card/sheet. */
fun LazyListScope.yourVisitsSection(
    selectedTab: VisitListTab,
    onTabSelected: (VisitListTab) -> Unit,
    planned: List<Destination>,
    completed: List<Destination>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenDestination: (Destination, Boolean) -> Unit,
) {
    val isCompleted = selectedTab == VisitListTab.COMPLETED
    item(key = "your_visits_heading") {
        Column {
            Text("Your visits", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp))
            SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                VisitListTab.entries.forEach { tab ->
                    Tab(selected = selectedTab == tab, onClick = { onTabSelected(tab) },
                        text = { Text(if (tab == VisitListTab.PLANNED) "Planned" else "Completed") })
                }
            }
        }
    }
    when {
        loading -> item(key = "visits_loading") {
            Column {
                LinearProgressIndicator()
                Text("Loading your visits…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        error != null -> item(key = "visits_error") {
            Column {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry visits") }
            }
        }
        else -> {
            val destinations = if (isCompleted) completed else planned
            if (destinations.isEmpty()) item(key = "visits_empty") {
                Text(if (isCompleted) "Your completed destinations will appear here after you verify a visit."
                    else "Enroll in a destination to plan your next visit.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isCompleted && destinations.isNotEmpty()) item(key = "completed_hint") {
                Text("Open a destination to view your visit or share its photo.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(destinations, key = { "visit_${selectedTab.name}_${it.destinationId}" }) { destination ->
                DestinationCard(destination, isLocked = !isCompleted,
                    onClick = { onOpenDestination(destination, isCompleted) })
            }
        }
    }
}
