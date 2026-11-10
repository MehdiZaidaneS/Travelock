package com.example.travelock.data.model

// Account identity from Supabase Auth. App profile details live in Profile.
data class AuthUser(val userId: String, val email: String?)

sealed interface RegistrationResult {
    data object ConfirmationRequired : RegistrationResult
}
