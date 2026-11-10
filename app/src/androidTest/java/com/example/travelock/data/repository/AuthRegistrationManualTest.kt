package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.RegistrationResult
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRegistrationManualTest {
    // Opt in with a new email address you own. The register stage sends an email
    // and keeps the account. Confirm it in your inbox before running confirmed.
    @Test
    fun requiresEmailConfirmationBeforeSignIn() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val stage = arguments.getString("authRegistrationStage")
        assumeTrue("Choose register or confirmed explicitly.", stage == "register" || stage == "confirmed")
        val email = requireNotNull(arguments.getString("supabaseRegistrationEmail")) {
            "Add a new email address you own as supabaseRegistrationEmail."
        }
        val password = requireNotNull(arguments.getString("supabaseRegistrationPassword")) {
            "Add supabaseRegistrationPassword locally."
        }
        val repository = AuthRepository()
        try {
            repository.logout()
            if (stage == "register") {
                assertEquals(RegistrationResult.ConfirmationRequired, repository.registerWithEmail(email, password))
                assertNull(repository.getCurrentUser())
                try {
                    repository.loginWithEmail(email, password)
                    fail("Sign-in succeeded before email confirmation. Use a new account with Confirm email enabled.")
                } catch (error: AuthRestException) {
                    assertEquals(AuthErrorCode.EmailNotConfirmed, error.errorCode)
                    assertNull(repository.getCurrentUser())
                }
            } else {
                val user = repository.loginWithEmail(email, password)
                assertEquals(email.trim(), user.email)
                assertEquals(user, repository.getCurrentUser())
                // Registration does not create an app profile before confirmation.
                assertNull(ProfileRepository().getMyProfile())
            }
        } finally {
            repository.logout()
        }
    }
}
