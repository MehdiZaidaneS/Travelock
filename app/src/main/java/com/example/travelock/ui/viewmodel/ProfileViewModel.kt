package com.example.travelock.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelock.data.model.User
import com.example.travelock.data.repository.DuplicateUsernameException
import com.example.travelock.data.repository.ProfileDetailsRepository
import com.example.travelock.ui.auth.ProfileAvatarPresets
import com.example.travelock.ui.auth.ProfileImageSelection
import com.example.travelock.ui.screens.profile.EditProfileUiState
import com.example.travelock.ui.screens.profile.ProfileUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for ProfileScreen and EditProfileScreen backed by live Supabase repositories.
 * Scoped at TravelockNavGraph so profile edits immediately reflect on ProfileScreen without stale reads.
 */
class ProfileViewModel(
    private val repository: ProfileDetailsRepository = ProfileDetailsRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _editState = MutableStateFlow(EditProfileUiState())
    val editState: StateFlow<EditProfileUiState> = _editState.asStateFlow()

    fun syncWithAuthUser(seedUser: User?) {
        if (seedUser == null) {
            _uiState.value = ProfileUiState()
            _editState.value = EditProfileUiState()
            return
        }
        if (seedUser.userId != _uiState.value.userId) {
            _uiState.value = ProfileUiState(
                isLoading = true,
                userId = seedUser.userId,
                username = seedUser.username,
                name = seedUser.name,
                email = seedUser.email,
                profileImage = seedUser.profileImage,
                createdAt = seedUser.createdAt,
            )
            loadProfile(forceRefresh = true)
        }
    }

    fun loadProfile(forceRefresh: Boolean = false) {
        val showInitialLoading = _uiState.value.userId.isEmpty() || _uiState.value.allLocations.isEmpty()
        _uiState.value = _uiState.value.copy(
            isLoading = showInitialLoading,
            isRefreshing = !showInitialLoading && forceRefresh,
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                val bundle = repository.loadMyProfileBundle()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    userId = bundle.profile.userId,
                    username = bundle.profile.username,
                    name = bundle.profile.name,
                    email = bundle.email,
                    profileImage = bundle.profile.profileImage,
                    createdAt = bundle.profile.createdAt,
                    unlocks = bundle.unlocks,
                    achievements = bundle.achievements,
                    allLocations = bundle.allLocations,
                    friends = bundle.friends,
                    errorMessage = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "Unable to load latest profile details. Check your connection and retry.",
                )
            }
        }
    }

    fun startEditing() {
        val current = _uiState.value
        val currentImg = current.profileImage.ifBlank { null }
        _editState.value = EditProfileUiState(
            email = current.email,
            username = current.username,
            fullName = current.name,
            selectedImage = currentImg,
            initialUsername = current.username,
            initialFullName = current.name,
            initialImage = currentImg,
        )
    }

    fun onUsernameChange(value: String) {
        _editState.value = _editState.value.copy(
            username = value,
            usernameError = null,
            generalError = null,
        )
    }

    fun onFullNameChange(value: String) {
        _editState.value = _editState.value.copy(
            fullName = value,
            fullNameError = null,
            generalError = null,
        )
    }

    fun onImageChange(value: String?) {
        _editState.value = _editState.value.copy(
            selectedImage = value,
            generalError = null,
        )
    }

    fun saveProfile(context: Context, onSuccess: () -> Unit) {
        val currentEdit = _editState.value
        if (currentEdit.isSaving) return

        val trimmedUsername = currentEdit.username.trim()
        val trimmedName = currentEdit.fullName.trim()

        val usernameError = if (trimmedUsername.length !in 3..30) {
            "Username must be between 3 and 30 characters."
        } else {
            null
        }
        val fullNameError = if (trimmedName.isBlank()) {
            "Full name cannot be blank."
        } else {
            null
        }

        if (usernameError != null || fullNameError != null) {
            _editState.value = currentEdit.copy(
                usernameError = usernameError,
                fullNameError = fullNameError,
            )
            return
        }

        _editState.value = currentEdit.copy(
            isSaving = true,
            usernameError = null,
            fullNameError = null,
            generalError = null,
        )

        val currentProfileImage = _uiState.value.profileImage
        val appContext = context.applicationContext

        viewModelScope.launch {
            try {
                val updatedProfile = repository.updateMyProfile(
                    username = trimmedUsername,
                    fullName = trimmedName,
                    selectedImage = currentEdit.selectedImage,
                    currentProfileImage = currentProfileImage,
                    loadPhotoBytes = {
                        val img = currentEdit.selectedImage
                        if (img != null && ProfileAvatarPresets.find(img) == null && img != currentProfileImage) {
                            ProfileImageSelection.readJpeg(appContext, Uri.parse(img))
                        } else {
                            null
                        }
                    },
                )

                _uiState.value = _uiState.value.copy(
                    username = updatedProfile.username,
                    name = updatedProfile.name,
                    profileImage = updatedProfile.profileImage,
                )
                _editState.value = _editState.value.copy(
                    isSaving = false,
                    initialUsername = updatedProfile.username,
                    initialFullName = updatedProfile.name,
                    initialImage = updatedProfile.profileImage.ifBlank { null },
                )
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: DuplicateUsernameException) {
                _editState.value = _editState.value.copy(
                    isSaving = false,
                    usernameError = error.message ?: "That username is taken.",
                )
            } catch (error: IllegalArgumentException) {
                _editState.value = _editState.value.copy(
                    isSaving = false,
                    generalError = error.message ?: "Invalid profile details.",
                )
            } catch (_: Exception) {
                _editState.value = _editState.value.copy(
                    isSaving = false,
                    generalError = "Unable to save profile changes. Check your connection and try again.",
                )
            }
        }
    }
}
