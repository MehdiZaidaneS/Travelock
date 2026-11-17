package com.example.travelock.ui.auth

import com.example.travelock.data.model.AuthUser
import com.example.travelock.data.model.Profile
import com.example.travelock.data.model.ProfileOnboardingState
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AuthFlowControllerTest {
    private val user = AuthUser("account-1", "test@example.com")
    private val ready = ProfileOnboardingState.Ready(user,
        Profile(user.userId, "traveler", "Traveler", createdAt = Instant.EPOCH))

    private inner class Gateway : AuthFlowGateway {
        var current: ProfileOnboardingState = ProfileOnboardingState.SignedOut
        var failRead = false
        var created = 0
        var logins = 0
        var registrations = 0
        var recovered: List<String>? = null
        var recoveryFailure = false
        var photo: ByteArray? = null
        var preset: String? = null
        var loginWait: CompletableDeferred<Unit>? = null
        override suspend fun state(): ProfileOnboardingState {
            if (failRead) error("private SDK request body")
            return current
        }
        override suspend fun register(email: String, password: String) { registrations++ }
        override suspend fun login(email: String, password: String) {
            logins++
            loginWait?.await()
            current = ProfileOnboardingState.NeedsProfile(user)
        }
        override suspend fun completeProfile(username: String, name: String, jpegBytes: ByteArray?, presetAvatar: String?) {
            photo = jpegBytes; preset = presetAvatar; created++; current = ready
        }
        override suspend fun logout() { current = ProfileOnboardingState.SignedOut }
        override suspend fun sendReset(email: String) { }
        override suspend fun recover(email: String, code: String, password: String) {
            if (recoveryFailure) error("private recovery token")
            recovered = listOf(email, code, password)
            current = ProfileOnboardingState.SignedOut
        }
    }

    @Test fun recoveryValidatesBeforeConsumingCodeAndReturnsToSignIn() = runBlocking {
        val gateway = Gateway()
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.navigate(AuthPage.LOGIN)
        flow.resetPassword(" test@example.com "); yield()
        assertEquals(AuthPage.RECOVERY, flow.state.value.page)
        flow.recoverPassword("test@example.com", "123456", "weak", "weak"); yield()
        assertNull(gateway.recovered)
        assertNotNull(flow.state.value.passwordError)
        flow.recoverPassword("test@example.com", "123456", "Strong-pass9!", "different"); yield()
        assertNull(gateway.recovered)
        assertEquals("Passwords do not match.", flow.state.value.error)
        flow.recoverPassword(" test@example.com ", " 123456 ", " Strong-pass9! ", " Strong-pass9! "); yield()
        assertEquals(listOf("test@example.com", "123456", " Strong-pass9! "), gateway.recovered)
        assertEquals(AuthPage.LOGIN, flow.state.value.page)
        assertEquals(ProfileOnboardingState.SignedOut, flow.state.value.onboarding)
        assertEquals("Password updated. Sign in with your new password.", flow.state.value.info)
    }

    @Test fun recoveryFailureStaysOnFormWithoutExposingSecrets() = runBlocking {
        val gateway = Gateway().apply { recoveryFailure = true }
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.resetPassword("test@example.com"); yield()
        flow.recoverPassword("test@example.com", "654321", "Strong-pass9!", "Strong-pass9!"); yield()
        assertEquals(AuthPage.RECOVERY, flow.state.value.page)
        assertFalse(flow.state.value.busy)
        assertFalse(flow.state.value.error!!.contains("private recovery token"))
        assertTrue(flow.state.value.error!!.contains("request a new code"))
        flow.navigate(AuthPage.LOGIN)
        assertNull(flow.state.value.error)
    }

    @Test fun registrationWaitsForConfirmationWithoutCreatingProfile() = runBlocking {
        val gateway = Gateway()
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.navigate(AuthPage.REGISTER)
        flow.register("test@example.com", "Strong-pass9!", "Strong-pass9!"); yield()
        assertEquals(AuthPage.CONFIRM_EMAIL, flow.state.value.page)
        assertEquals(ProfileOnboardingState.SignedOut, flow.state.value.onboarding)
        assertEquals(0, gateway.created)
    }

    @Test fun weakPasswordIsRejectedBeforeSendingRegistration() = runBlocking {
        val gateway = Gateway()
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.navigate(AuthPage.REGISTER)
        flow.register("test@example.com", "password", "password"); yield()
        assertEquals(0, gateway.registrations)
        assertEquals(AuthPage.REGISTER, flow.state.value.page)
        assertTrue(flow.state.value.passwordError!!.contains("uppercase"))
    }

    @Test fun choosingBundledAvatarDoesNotReadOrUploadPhotoBytes() = runBlocking {
        val gateway = Gateway().apply { current = ProfileOnboardingState.NeedsProfile(user) }
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.completeProfile("traveler", "Traveler", presetAvatar = "preset:moods_03") {
            error("Bundled avatar must not read a photo URI")
        }; yield()
        assertEquals("preset:moods_03", gateway.preset)
        assertNull(gateway.photo)
        assertEquals(AuthPage.READY, flow.state.value.page)
    }

    @Test fun unsupportedPresetIsRejectedWithoutCreatingProfile() = runBlocking {
        val gateway = Gateway().apply { current = ProfileOnboardingState.NeedsProfile(user) }
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.completeProfile("traveler", "Traveler", presetAvatar = "preset:moods_99"); yield()
        assertEquals(0, gateway.created)
        assertEquals(AuthPage.PROFILE, flow.state.value.page)
    }

    @Test fun imageReadFailureDoesNotSilentlyCreateProfileWithoutPhoto() = runBlocking {
        val gateway = Gateway().apply { current = ProfileOnboardingState.NeedsProfile(user) }
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.completeProfile("traveler", "Traveler") { error("private local URI") }; yield()
        assertEquals(0, gateway.created)
        assertEquals(AuthPage.PROFILE, flow.state.value.page)
        assertNotNull(flow.state.value.error)
        assertFalse(flow.state.value.error!!.contains("private local URI"))
        val bytes = byteArrayOf(1, 2, 3)
        flow.completeProfile("traveler", "Traveler") { bytes }; yield()
        assertArrayEquals(bytes, gateway.photo)
        assertEquals(AuthPage.READY, flow.state.value.page)
    }

    @Test fun incompleteAccountResumesSetupAndCompletedAccountEntersApp() = runBlocking {
        val gateway = Gateway().apply { current = ProfileOnboardingState.NeedsProfile(user) }
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        assertEquals(AuthPage.PROFILE, flow.state.value.page)
        flow.navigate(AuthPage.LANDING)
        assertEquals(AuthPage.PROFILE, flow.state.value.page)
        flow.completeProfile("traveler", "Traveler"); yield()
        assertEquals(AuthPage.READY, flow.state.value.page)
        assertEquals(1, gateway.created)
        val restored = AuthFlowController(this, gateway)
        restored.restore(); yield()
        assertEquals(AuthPage.READY, restored.state.value.page)
        assertEquals(1, gateway.created)
    }

    @Test fun profileReadFailureBlocksAppAndRetryDoesNotExposeException() = runBlocking {
        val gateway = Gateway().apply { current = ready; failRead = true }
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        assertEquals(AuthPage.RETRY, flow.state.value.page)
        assertFalse(flow.state.value.error!!.contains("private SDK"))
        gateway.failRead = false
        flow.restore(); yield()
        assertEquals(AuthPage.READY, flow.state.value.page)
    }

    @Test fun duplicateTapsCannotStartConcurrentSignIns() = runBlocking {
        val gateway = Gateway().apply { loginWait = CompletableDeferred() }
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.navigate(AuthPage.LOGIN)
        flow.login("test@example.com", "password")
        flow.login("test@example.com", "password"); yield()
        assertEquals(1, gateway.logins)
        assertTrue(flow.state.value.busy)
        gateway.loginWait!!.complete(Unit); yield()
        assertEquals(AuthPage.PROFILE, flow.state.value.page)
    }

    @Test fun googleUsesSameOnboardingAndLogoutClearsRoute() = runBlocking {
        val gateway = Gateway()
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        flow.google { gateway.current = ProfileOnboardingState.NeedsProfile(user) }; yield()
        assertEquals(AuthPage.PROFILE, flow.state.value.page)
        flow.logout(); yield()
        assertEquals(AuthPage.LANDING, flow.state.value.page)
        assertEquals(ProfileOnboardingState.SignedOut, flow.state.value.onboarding)
        flow.google { gateway.current = ready }; yield()
        assertEquals(AuthPage.READY, flow.state.value.page)
    }

    @Test fun profileRetryDoesNotInsertAgainAfterServerSavedIt() = runBlocking {
        val gateway = Gateway().apply { current = ProfileOnboardingState.NeedsProfile(user) }
        val flow = AuthFlowController(this, gateway)
        flow.restore(); yield()
        gateway.current = ready
        flow.completeProfile("traveler", "Traveler"); yield()
        assertEquals(0, gateway.created)
        assertEquals(AuthPage.READY, flow.state.value.page)
    }
}
