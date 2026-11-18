package com.example.travelock.ui.auth

import androidx.annotation.DrawableRes
import com.example.travelock.R

data class ProfileAvatarPreset(val id: String, @param:DrawableRes val imageRes: Int, val label: String)

/** Stable IDs are persisted in profiles.profile_image; keep existing images/IDs unchanged. */
object ProfileAvatarPresets {
    val all = listOf(
        ProfileAvatarPreset("preset:moods_01", R.drawable.avatar_moods_01, "Moods avatar 1"),
        ProfileAvatarPreset("preset:moods_02", R.drawable.avatar_moods_02, "Moods avatar 2"),
        ProfileAvatarPreset("preset:moods_03", R.drawable.avatar_moods_03, "Moods avatar 3"),
        ProfileAvatarPreset("preset:moods_04", R.drawable.avatar_moods_04, "Moods avatar 4"),
        ProfileAvatarPreset("preset:moods_05", R.drawable.avatar_moods_05, "Moods avatar 5"),
        ProfileAvatarPreset("preset:moods_06", R.drawable.avatar_moods_06, "Moods avatar 6"),
        ProfileAvatarPreset("preset:moods_07", R.drawable.avatar_moods_07, "Moods avatar 7"),
        ProfileAvatarPreset("preset:moods_08", R.drawable.avatar_moods_08, "Moods avatar 8"),
        ProfileAvatarPreset("preset:moods_09", R.drawable.avatar_moods_09, "Moods avatar 9"),
        ProfileAvatarPreset("preset:moods_10", R.drawable.avatar_moods_10, "Moods avatar 10"),
        ProfileAvatarPreset("preset:moods_11", R.drawable.avatar_moods_11, "Moods avatar 11"),
        ProfileAvatarPreset("preset:moods_12", R.drawable.avatar_moods_12, "Moods avatar 12"),
    )

    fun find(id: String?): ProfileAvatarPreset? = all.firstOrNull { it.id == id }
}
