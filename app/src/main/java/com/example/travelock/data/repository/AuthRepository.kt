package com.example.travelock.data.repository

import com.example.travelock.BuildConfig
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.auth.PasswordRecoveryLinks
import com.example.travelock.data.model.AuthUser
import com.example.travelock.data.model.RegistrationResult
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

// Supabase errors and coroutine cancellation propagate to the caller.
class AuthRepository {
    private val supabase = SupabaseProvider.client

    suspend fun registerWithEmail(email: String, password: String): RegistrationResult {
        require(email.isNotBlank()) { "Email cannot be blank." }
        require(password.isNotEmpty()) { "Password cannot be empty." }
        supabase.auth.awaitInitialization()
        check(supabase.auth.currentSessionOrNull() == null) {
            "Sign out before registering another account."
        }

        // Supabase validates the email and configured password requirements.
        val pendingUser = supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        // Confirmation is required by this app's signup contract. Detect an
        // accidental dashboard change instead of treating an auto-login as pending.
        if (supabase.auth.currentSessionOrNull() != null) {
            supabase.auth.signOut(SignOutScope.LOCAL)
            error("Enable Confirm email in Supabase before registering accounts.")
        }
        checkNotNull(pendingUser) { "Supabase did not return a pending registration." }

        // Existing accounts may receive an indistinguishable response, so this
        // result means 'check your email', not proof that a new account was created.
        return RegistrationResult.ConfirmationRequired
    }

    suspend fun loginWithEmail(email: String, password: String): AuthUser {
        require(email.isNotBlank()) { "Email cannot be blank." }
        require(password.isNotEmpty()) { "Password cannot be empty." }
        supabase.auth.awaitInitialization()
        check(supabase.auth.currentSessionOrNull() == null) {
            "Sign out before signing in to another account."
        }
        // The server rejects unconfirmed accounts and incorrect credentials.
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return checkNotNull(getCurrentUser()) { "Sign-in did not create a session." }
    }

    // Reads the restored local session. Protected requests still rely on server RLS.
    suspend fun getCurrentUser(): AuthUser? {
        supabase.auth.awaitInitialization()
        val user = supabase.auth.currentSessionOrNull()?.user ?: return null
        return AuthUser(user.id, user.email)
    }

    suspend fun logout() {
        supabase.auth.awaitInitialization()
        supabase.auth.signOut(SignOutScope.LOCAL)
    }

    // A successful request means 'check your email', not proof the account exists.
    suspend fun sendPasswordResetEmail(email: String) {
        require(email.isNotBlank()) { "Email cannot be blank." }
        supabase.auth.awaitInitialization()
        supabase.auth.resetPasswordForEmail(email = email.trim())
    }

    // Consume an unopened recovery email link, then change the verified account's
    // password. Email prevents a pasted link for another account changing it.
    suspend fun resetPasswordFromLink(
        email: String,
        recoveryLink: String,
        newPassword: String,
    ): AuthUser {
        require(email.isNotBlank()) { "Email cannot be blank." }
        require(newPassword.isNotEmpty()) { "Password cannot be empty." }
        val tokenHash = PasswordRecoveryLinks.tokenHash(recoveryLink, BuildConfig.SUPABASE_URL)
        supabase.auth.awaitInitialization()
        check(supabase.auth.currentSessionOrNull() == null) {
            "Sign out before recovering an account."
        }
        try {
            supabase.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, tokenHash = tokenHash)
            val user = checkNotNull(supabase.auth.currentUserOrNull()) { "Recovery did not create a session." }
            require(user.email.equals(email.trim(), ignoreCase = true)) {
                "The recovery link belongs to a different email address. Request a new link."
            }
            // The server checks password strength. Never trim the password.
            supabase.auth.updateUser { password = newPassword }
            return AuthUser(user.id, user.email)
        } finally {
            // A failed update may have consumed the one-use link too. Do not leave
            // a recovery session signed in, including after coroutine cancellation.
            withContext(NonCancellable) { supabase.auth.clearSession() }
        }
    }
}
