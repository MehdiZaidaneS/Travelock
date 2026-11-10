package com.example.travelock.data.model

sealed interface ProfileOnboardingState {
    data object SignedOut : ProfileOnboardingState
    data class NeedsProfile(val user: AuthUser) : ProfileOnboardingState
    data class Ready(val user: AuthUser, val profile: Profile) : ProfileOnboardingState
}
