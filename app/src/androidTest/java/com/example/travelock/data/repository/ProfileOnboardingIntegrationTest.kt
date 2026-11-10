package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.ProfileOnboardingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileOnboardingIntegrationTest {
    // Reuses the new confirmed account from the registration test. Keeps its
    // profile after the first run; later runs verify the existing-profile path.
    @Test
    fun createsMissingProfileAndRecognizesItAfterSigningInAgain() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = requireNotNull(arguments.getString("supabaseRegistrationEmail")) {
            "Add the confirmed account's local supabaseRegistrationEmail argument."
        }
        val password = requireNotNull(arguments.getString("supabaseRegistrationPassword")) {
            "Add supabaseRegistrationPassword locally."
        }
        val auth = AuthRepository()
        val onboarding = ProfileOnboardingRepository()
        val profiles = ProfileRepository()
        try {
            auth.logout()
            assertEquals(ProfileOnboardingState.SignedOut, onboarding.getState())
            try {
                onboarding.completeProfile("unused", "Unused")
                fail("Profile setup must require sign-in.")
            } catch (_: IllegalStateException) {
                assertEquals(ProfileOnboardingState.SignedOut, onboarding.getState())
            }
            val user = auth.loginWithEmail(email, password)
            val state = onboarding.getState()
            val profile = when (state) {
                is ProfileOnboardingState.NeedsProfile -> {
                    assertEquals(user, state.user)
                    val saved = onboarding.completeProfile(
                        username = "  onboarding_${user.userId.replace("-", "")}  ",
                        name = "  Onboarding Test  ",
                    )
                    assertEquals("Onboarding Test", saved.name)
                    assertEquals("onboarding_${user.userId.replace("-", "")}", saved.username)
                    assertEquals("", saved.profileImage)
                    saved
                }
                is ProfileOnboardingState.Ready -> {
                    assertEquals(user, state.user)
                    state.profile
                }
                ProfileOnboardingState.SignedOut -> error("Sign-in did not persist a session.")
            }
            assertEquals(user.userId, profile.userId)
            assertEquals(profile, profiles.getMyProfile())
            assertEquals(ProfileOnboardingState.Ready(user, profile), onboarding.getState())

            try {
                onboarding.completeProfile(profile.username, "Must not overwrite")
                fail("Completing onboarding twice must not overwrite the profile.")
            } catch (_: IllegalStateException) {
                assertEquals(profile, profiles.getMyProfile())
            }

            auth.logout()
            assertEquals(ProfileOnboardingState.SignedOut, onboarding.getState())
            val signedInAgain = auth.loginWithEmail(email, password)
            assertEquals(user.userId, signedInAgain.userId)
            val restored = onboarding.getState()
            assertTrue(restored is ProfileOnboardingState.Ready)
            assertEquals(ProfileOnboardingState.Ready(signedInAgain, profile), restored)
        } finally {
            auth.logout()
        }
    }
}
