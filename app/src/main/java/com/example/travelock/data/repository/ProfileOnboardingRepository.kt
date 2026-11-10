package com.example.travelock.data.repository

import com.example.travelock.data.model.Profile
import com.example.travelock.data.model.ProfileOnboardingState

// Combines the real Auth session with the signed-in user's Supabase profile.
class ProfileOnboardingRepository {
    private val authRepository = AuthRepository()
    private val profileRepository = ProfileRepository()

    suspend fun getState(): ProfileOnboardingState {
        val user = authRepository.getCurrentUser() ?: return ProfileOnboardingState.SignedOut
        val profile = profileRepository.getMyProfile()
        check(authRepository.getCurrentUser()?.userId == user.userId) {
            "The account changed. Reload profile setup."
        }
        return if (profile == null) {
            ProfileOnboardingState.NeedsProfile(user)
        } else {
            check(profile.userId == user.userId) { "The profile belongs to another account." }
            ProfileOnboardingState.Ready(user, profile)
        }
    }

    // Create once. Editing an existing profile uses ProfileRepository.updateMyProfile.
    suspend fun completeProfile(
        username: String,
        name: String,
        profileImage: String = "",
    ): Profile {
        when (getState()) {
            ProfileOnboardingState.SignedOut -> error("Sign in before setting up your profile.")
            is ProfileOnboardingState.Ready -> error("Your profile is already set up.")
            is ProfileOnboardingState.NeedsProfile -> Unit
        }
        // Required fields, username uniqueness and ownership use the existing
        // repository and database rules. Errors remain available to the caller.
        return profileRepository.createMyProfile(username, name, profileImage)
    }
}
