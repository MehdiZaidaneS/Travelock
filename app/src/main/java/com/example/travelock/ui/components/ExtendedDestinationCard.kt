package com.example.travelock.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.travelock.data.model.Destination

/** One sheet owns destination details and verification, so steps retain context. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendedDestinationCard(
    destination: Destination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    verifying: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onBack() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (verifying) "Verify your visit" else "Destination", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(destination.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.Default.Close, contentDescription = "Close destination")
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (destination.image.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(16.dp)) {
                        AsyncImage(
                            model = destination.image, contentDescription = destination.name,
                            modifier = Modifier.fillMaxWidth().height(if (verifying) 112.dp else 176.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else if (!verifying) {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp))
                        }
                    }
                }
                if (!verifying && destination.description.isNotBlank()) {
                    Text(destination.description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!verifying && destination.discount > 0f) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.LocalOffer, contentDescription = null)
                            Text("${destination.discount.toInt()}% partner discount when unlocked", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                content()
            }
        }
    }
}
