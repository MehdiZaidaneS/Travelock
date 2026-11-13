package com.example.travelock.ui.auth

import com.example.travelock.data.model.ProfileOnboardingState
import com.example.travelock.data.repository.AuthRepository
import com.example.travelock.data.repository.ProfileOnboardingRepository
import com.example.travelock.data.repository.ProfilePhotoRepository
import com.example.travelock.data.auth.RegistrationPasswordPolicy
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthPage { CHECKING, LANDING, LOGIN, REGISTER, CONFIRM_EMAIL, PROFILE, READY, RETRY }

data class AuthFlowState(
    val page: AuthPage = AuthPage.CHECKING,
    val onboarding: ProfileOnboardingState = ProfileOnboardingState.SignedOut,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val pendingEmail: String = "",
    val passwordError: String? = null,
)

// The gateway keeps routing tests independent of Android and live accounts.
interface AuthFlowGateway {
    suspend fun state(): ProfileOnboardingState
    suspend fun register(email: String, password: String)
    suspend fun login(email: String, password: String)
    suspend fun completeProfile(username: String, name: String, jpegBytes: ByteArray?, presetAvatar: String?)
    suspend fun logout()
    suspend fun sendReset(email: String)
}

class SupabaseAuthFlowGateway : AuthFlowGateway {
    private val auth by lazy { AuthRepository() }
    private val onboarding by lazy { ProfileOnboardingRepository() }
    private val photos by lazy { ProfilePhotoRepository() }
    private data class PendingPhoto(val userId: String, val bytes: ByteArray, val path: String)
    private var pendingPhoto: PendingPhoto? = null
    override suspend fun state() = onboarding.getState()
    override suspend fun register(email: String, password: String) { auth.registerWithEmail(email, password) }
    override suspend fun login(email: String, password: String) { auth.loginWithEmail(email, password) }
    override suspend fun completeProfile(username: String, name: String, jpegBytes: ByteArray?, presetAvatar: String?) {
        require(presetAvatar == null || ProfileAvatarPresets.find(presetAvatar) != null) { "Choose an available avatar." }
        require(presetAvatar == null || jpegBytes == null) { "Choose either an avatar or an uploaded photo." }
        val user = checkNotNull(auth.getCurrentUser())
        val path = if (presetAvatar != null) presetAvatar else if (jpegBytes == null) "" else {
            val previous = pendingPhoto
            if (previous != null && previous.userId == user.userId && previous.bytes.contentEquals(jpegBytes)) previous.path
            else photos.uploadProfilePhoto(jpegBytes).also {
                pendingPhoto = PendingPhoto(user.userId, jpegBytes.copyOf(), it)
            }
        }
        onboarding.completeProfile(username, name, path)
        pendingPhoto = null
    }
    override suspend fun logout() { auth.logout(); pendingPhoto = null }
    override suspend fun sendReset(email: String) = auth.sendPasswordResetEmail(email)
}

class AuthFlowController(private val scope: CoroutineScope, private val gateway: AuthFlowGateway) {
    private val mutableState = MutableStateFlow(AuthFlowState())
    val state = mutableState.asStateFlow()

    fun navigate(page: AuthPage) {
        if (!state.value.busy && state.value.page in setOf(
                AuthPage.LANDING, AuthPage.LOGIN, AuthPage.REGISTER, AuthPage.CONFIRM_EMAIL
            ) && page in setOf(AuthPage.LANDING, AuthPage.LOGIN, AuthPage.REGISTER)) {
            mutableState.value = state.value.copy(page = page, error = null, info = null)
        }
    }

    fun restore() = action("Unable to load your account. Check your connection and retry.") { route() }

    fun clearPasswordError() {
        if (!state.value.busy) mutableState.value = state.value.copy(passwordError = null, error = null)
    }

    fun register(email: String, password: String, confirmation: String) {
        if (state.value.busy) return
        if (email.isBlank() || password.isEmpty() || password != confirmation) {
            mutableState.value = state.value.copy(error = if (password != confirmation)
                "Passwords do not match." else "Enter your email and password.")
            return
        }
        val missing = RegistrationPasswordPolicy.missingRequirements(password)
        if (missing.isNotEmpty()) {
            mutableState.value = state.value.copy(passwordError = missing.joinToString(". ") + ".", error = null)
            return
        }
        action("Unable to register. Check your email, password requirements and connection, then retry.") {
            gateway.register(email.trim(), password)
            mutableState.value = AuthFlowState(page = AuthPage.CONFIRM_EMAIL, pendingEmail = email.trim(), busy = true)
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            if (!state.value.busy) mutableState.value = state.value.copy(error = "Enter your email and password.")
            return
        }
        action("Unable to sign in. Check your credentials and connection.") {
            gateway.login(email.trim(), password)
            route()
        }
    }

    fun google(signIn: suspend () -> Unit) = action("Google sign-in failed. Check your Google setup and connection, then retry.") {
        signIn()
        route()
    }

    fun completeProfile(username: String, name: String, presetAvatar: String? = null, loadPhoto: suspend () -> ByteArray? = { null }) {
        if (username.isBlank() || name.isBlank()) {
            if (!state.value.busy) mutableState.value = state.value.copy(error = "Enter a username and full name.")
            return
        }
        action("Unable to save your profile. Check the selected photo and your connection, or try a different username if it is already taken.") {
            // Re-read after a retry: a previous insert may have reached the server.
            when (gateway.state()) {
                is ProfileOnboardingState.NeedsProfile -> {
                    require(presetAvatar == null || ProfileAvatarPresets.find(presetAvatar) != null)
                    gateway.completeProfile(username.trim(), name.trim(),
                        if (presetAvatar == null) loadPhoto() else null, presetAvatar)
                }
                else -> Unit
            }
            route()
        }
    }

    fun logout(afterLogout: suspend () -> Unit = {}) = action("Unable to sign out. Check your connection and retry.") {
        gateway.logout()
        mutableState.value = AuthFlowState(page = AuthPage.LANDING, busy = true)
        // Supabase logout is authoritative even if the device credential provider is unavailable.
        try { afterLogout() } catch (error: Exception) {
            if (error is CancellationException) throw error
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            if (!state.value.busy) mutableState.value = state.value.copy(error = "Enter your email address first.")
            return
        }
        action("Unable to request a password reset. Check your email address and connection.") {
            gateway.sendReset(email.trim())
            mutableState.value = state.value.copy(info = "If an account exists, check your email for a password reset link.")
        }
    }

    private suspend fun route() {
        val onboarding = try { gateway.state() } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = state.value.copy(page = AuthPage.RETRY, onboarding = ProfileOnboardingState.SignedOut)
            throw error
        }
        mutableState.value = state.value.copy(onboarding = onboarding, page = when (onboarding) {
            ProfileOnboardingState.SignedOut -> AuthPage.LANDING
            is ProfileOnboardingState.NeedsProfile -> AuthPage.PROFILE
            is ProfileOnboardingState.Ready -> AuthPage.READY
        }, error = null, info = null)
    }

    private fun action(fallback: String, block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null, info = null, passwordError = null)
        scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                // Never display/log SDK exception messages: they can contain request headers.
                val message = when {
                    error is AuthWeakPasswordException -> RegistrationPasswordPolicy.serverRejection(error.reasons)
                    error is AuthRestException && error.errorCode?.value == "weak_password" ->
                        RegistrationPasswordPolicy.serverRejection(emptyList())
                    error is AuthRestException && error.errorCode?.value == "email_not_confirmed" ->
                        "Confirm your email address before signing in."
                    error is RestException && error.statusCode == 429 -> "Too many requests. Please wait before retrying."
                    else -> fallback
                }
                val weakPassword = state.value.page == AuthPage.REGISTER && (error is AuthWeakPasswordException ||
                    (error is AuthRestException && error.errorCode?.value == "weak_password"))
                mutableState.value = state.value.copy(error = if (weakPassword) null else message,
                    passwordError = if (weakPassword) message else null)
            } finally { mutableState.value = state.value.copy(busy = false) }
        }
    }
}
