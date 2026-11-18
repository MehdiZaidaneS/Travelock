package com.example.travelock.ui.screens.profile

import com.example.travelock.data.model.FriendProfile
import com.example.travelock.data.model.Location
import com.example.travelock.data.model.User
import com.example.travelock.data.model.UserAchievement
import com.example.travelock.data.model.UserUnlock
import java.time.Instant

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val userId: String = "",
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val profileImage: String = "",
    val createdAt: Instant = Instant.now(),
    val unlocks: List<UserUnlock> = emptyList(),
    val achievements: List<UserAchievement> = emptyList(),
    val allLocations: List<Location> = emptyList(),
    val friends: List<FriendProfile> = emptyList(),
    val errorMessage: String? = null,
) {
    fun toDisplayUser(): User = User(
        userId = userId,
        username = username,
        name = name,
        email = email,
        profileImage = profileImage,
        createdAt = createdAt,
    )
}

data class EditProfileUiState(
    val email: String = "",
    val username: String = "",
    val fullName: String = "",
    val selectedImage: String? = null,
    val initialUsername: String = "",
    val initialFullName: String = "",
    val initialImage: String? = null,
    val usernameError: String? = null,
    val fullNameError: String? = null,
    val generalError: String? = null,
    val isSaving: Boolean = false,
) {
    val hasChanges: Boolean
        get() = username.trim() != initialUsername ||
            fullName.trim() != initialFullName ||
            selectedImage != initialImage
}
