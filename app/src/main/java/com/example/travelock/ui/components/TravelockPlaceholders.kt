package com.example.travelock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Offline-first styled gradient & vector illustration header for Helsinki Locations and Destinations,
 * plus colored initials avatars for Users.
 */
@Composable
fun HelsinkiIllustrationBanner(
    imageKey: String,
    title: String,
    isLocked: Boolean = false,
    height: Dp = 150.dp,
    modifier: Modifier = Modifier
) {
    // TODO: SUPABASE_STORAGE / COIL - Replace gradient placeholder with AsyncImage(model = imageUrl)
    val (colors, icon) = resolveBannerVisuals(imageKey, isLocked)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush = Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White.copy(alpha = if (isLocked) 0.55f else 0.92f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun UserAvatarCircle(
    name: String,
    username: String = "",
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    // TODO: SUPABASE_STORAGE / COIL - Replace initials avatar with AsyncImage(model = user.profileImage)
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { username.take(2).uppercase().ifEmpty { "U" } }

    val palette = listOf(
        Color(0xFF00897B),
        Color(0xFF1565C0),
        Color(0xFFEF6C00),
        Color(0xFF6A1B9A),
        Color(0xFF2E7D32)
    )
    val bgColor = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp
        )
    }
}

private fun resolveBannerVisuals(imageKey: String, isLocked: Boolean): Pair<List<Color>, ImageVector> {
    if (isLocked) {
        return listOf(Color(0xFF546E7A), Color(0xFF37474F)) to resolveIcon(imageKey)
    }
    val gradient = when (imageKey) {
        "loc_kallio", "kallio_library" -> listOf(Color(0xFF00897B), Color(0xFF004D40))
        "kallio_church" -> listOf(Color(0xFF5C6BC0), Color(0xFF283593))
        "karhupuisto" -> listOf(Color(0xFF43A047), Color(0xFF1B5E20))
        "loc_myllypuro", "metropolia_campus" -> listOf(Color(0xFFFB8C00), Color(0xFFE65100))
        "myllypuro_pool" -> listOf(Color(0xFF039BE5), Color(0xFF01579B))
        "myllypuro_trail" -> listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))
        "loc_suomenlinna", "suomenlinna_fortress", "kings_gate", "suomenlinna_church" ->
            listOf(Color(0xFF1E88E5), Color(0xFF0D47A1))
        "loc_kluuvi", "oodi_library", "helsinki_cathedral" ->
            listOf(Color(0xFF8E24AA), Color(0xFF4A148C))
        else -> listOf(Color(0xFF00897B), Color(0xFF1565C0))
    }
    return gradient to resolveIcon(imageKey)
}

private fun resolveIcon(imageKey: String): ImageVector = when (imageKey) {
    "kallio_library", "oodi_library" -> Icons.Default.LocalLibrary
    "kallio_church", "suomenlinna_church", "helsinki_cathedral" -> Icons.Default.Church
    "karhupuisto" -> Icons.Default.Park
    "metropolia_campus" -> Icons.Default.School
    "myllypuro_pool" -> Icons.Default.Pool
    "myllypuro_trail" -> Icons.Default.Forest
    "suomenlinna_fortress", "kings_gate" -> Icons.Default.Castle
    "loc_suomenlinna" -> Icons.Default.DirectionsBoat
    "loc_kluuvi" -> Icons.Default.AccountBalance
    else -> Icons.Default.LocationCity
}

