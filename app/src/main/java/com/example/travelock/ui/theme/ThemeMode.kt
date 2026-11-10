package com.example.travelock.ui.theme

/**
 * Represents the three theme modes the user can choose from.
 * Persisted to DataStore as the enum name string.
 */
enum class ThemeMode {
    /** Follow the device's system-wide dark/light setting. */
    SYSTEM,

    /** Always use the light color scheme. */
    LIGHT,

    /** Always use the dark color scheme. */
    DARK;

    companion object {
        /** Safely parse a stored string back to a ThemeMode, defaulting to SYSTEM. */
        fun fromString(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

