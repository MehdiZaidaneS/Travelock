package com.example.travelock.data.mock.repository

import com.example.travelock.data.mock.MockSessionState
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.data.model.User
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles Authentication & Account Management (PBI-01 through PBI-05).
 * Runs 100% on mock data now with explicit Supabase Auth integration comments.
 */
class AuthRepository {

    val currentUserFlow: StateFlow<User?> = MockSessionState.currentUser

    /**
     * PBI-03: Log in using a registered account.
     */
    suspend fun loginWithEmail(email: String, password: String): Result<User> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter both email and password."))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Invalid email or password."))
        }

        // TODO: SUPABASE - Replace mock login with Supabase Auth Email sign-in:
        // val supabase = SupabaseProvider.client
        // supabase.auth.signInWith(Email) {
        //     this.email = trimmedEmail
        //     this.password = password
        // }
        // val profile = ProfileRepository().getMyProfile()

        val matchedUser = MockUsers.allUsers.find {
            it.email.equals(trimmedEmail, ignoreCase = true)
        } ?: MockUsers.currentUser.copy(email = trimmedEmail)

        MockSessionState.setLoggedInUser(matchedUser)
        return Result.success(matchedUser)
    }

    /**
     * PBI-01: Register an account.
     */
    suspend fun registerWithEmail(
        email: String,
        password: String,
        username: String,
        name: String
    ): Result<User> {
        val trimmedEmail = email.trim()
        val trimmedUsername = username.trim()
        val trimmedName = name.trim()

        if (trimmedUsername.isBlank() || trimmedName.isBlank() || trimmedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("All fields are required."))
        }
        if (!trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }
        if (MockUsers.allUsers.any { it.email.equals(trimmedEmail, ignoreCase = true) && trimmedEmail != "mehdi@travelock.fi" }) {
            return Result.failure(IllegalArgumentException("An account with this email is already registered."))
        }

        // TODO: SUPABASE - Replace mock registration with Supabase Auth sign-up + Profile creation:
        // val supabase = SupabaseProvider.client
        // supabase.auth.signUpWith(Email) {
        //     this.email = trimmedEmail
        //     this.password = password
        // }
        // val createdProfile = ProfileRepository().createMyProfile(
        //     username = trimmedUsername,
        //     name = trimmedName
        // )

        val newUser = MockUsers.currentUser.copy(
            userId = "user-${UUID.randomUUID()}",
            username = trimmedUsername,
            name = trimmedName,
            email = trimmedEmail,
            createdAt = Instant.now()
        )
        MockSessionState.setLoggedInUser(newUser)
        return Result.success(newUser)
    }

    /**
     * PBI-02: Log in using Google.
     */
    suspend fun loginWithGoogle(idToken: String = "mock-google-id-token"): Result<User> {
        // TODO: SUPABASE - Replace mock Google Auth with Supabase IDToken / OAuth sign-in:
        // val supabase = SupabaseProvider.client
        // supabase.auth.signInWith(IDToken) {
        //     this.provider = Google
        //     this.idToken = idToken
        // }
        // val profile = ProfileRepository().getMyProfile()
        //     ?: ProfileRepository().createMyProfile(username = ..., name = ...)

        val user = MockUsers.currentUser
        MockSessionState.setLoggedInUser(user)
        return Result.success(user)
    }

    /**
     * PBI-04: Remain logged in / check active session.
     */
    suspend fun getCurrentUser(): User? {
        // TODO: SUPABASE - Replace mock session check with:
        // val supabase = SupabaseProvider.client
        // supabase.auth.awaitInitialization()
        // val sessionUser = supabase.auth.currentUserOrNull() ?: return null
        // val profile = ProfileRepository().getMyProfile()

        return MockSessionState.currentUser.value
    }

    /**
     * PBI-30: Log out of account.
     */
    suspend fun logout() {
        // TODO: SUPABASE - Replace mock logout with:
        // SupabaseProvider.client.auth.signOut()

        MockSessionState.setLoggedInUser(null)
    }

    /**
     * PBI-05: Reset password.
     */
    suspend fun resetPassword(email: String): Result<Unit> {
        val trimmed = email.trim()
        if (trimmed.isBlank() || !trimmed.contains("@")) {
            return Result.failure(IllegalArgumentException("Enter your registered email address first."))
        }

        // TODO: SUPABASE - Replace mock password reset with:
        // SupabaseProvider.client.auth.resetPasswordForEmail(trimmed)

        return Result.success(Unit)
    }
}

