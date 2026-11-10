package com.example.travelock.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.travelock.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = TravelockTealLight,
    onPrimary = OnTravelockTeal,
    primaryContainer = TravelockTealDark,
    onPrimaryContainer = OnTravelockTeal,
    secondary = TravelockAmberLight,
    onSecondary = OnTravelockAmber,
    secondaryContainer = TravelockAmberDark,
    onSecondaryContainer = OnTravelockTeal,
    tertiary = TravelockBlueLight,
    onTertiary = OnTravelockBlue,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = TravelockErrorLight,
    onError = OnTravelockError
)

private val LightColorScheme = lightColorScheme(
    primary = TravelockTeal,
    onPrimary = OnTravelockTeal,
    primaryContainer = UnlockedAccentLight,
    onPrimaryContainer = TravelockTealDark,
    secondary = TravelockAmber,
    onSecondary = OnTravelockAmber,
    secondaryContainer = TravelockAmberLight,
    onSecondaryContainer = OnTravelockAmber,
    tertiary = TravelockBlue,
    onTertiary = OnTravelockBlue,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = TravelockError,
    onError = OnTravelockError
)

/**
 * Provides the current [ThemeMode] and a callback to change it down the composition tree.
 */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalOnThemeModeChange = staticCompositionLocalOf<(ThemeMode) -> Unit> { {} }

@Composable
fun TravelockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    // Strictly disabled per requirement 2 so the custom Travelock palette is always used
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalOnThemeModeChange provides onThemeModeChange
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = TravelockShapes,
            content = content
        )
    }
}
