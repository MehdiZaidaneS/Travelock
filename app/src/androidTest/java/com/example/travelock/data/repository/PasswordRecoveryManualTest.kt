package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.BuildConfig
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
class PasswordRecoveryManualTest {
    // Explicit opt-in: sends real email and permanently changes a TEST account's
    // password. Keep links/passwords in local arguments, never in project files.
    @Test
    fun resetsThroughTheRecoveryEmailAndRejectsTheOldPassword() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val stage = args.getString("passwordRecoveryStage")
        assumeTrue("Choose send, complete or verify explicitly.", stage in listOf("send", "complete", "verify"))
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val email = argument("supabaseRecoveryEmail")
        val oldPassword = argument("supabaseRecoveryPassword")
        val repository = AuthRepository()
        suspend fun verifyChangedPassword(newPassword: String, expectedId: String? = null) {
            try {
                repository.loginWithEmail(email, oldPassword)
                fail("Old password still works.")
            } catch (error: AuthRestException) {
                assertEquals(AuthErrorCode.InvalidCredentials, error.errorCode)
                assertNull(repository.getCurrentUser())
            }
            val user = repository.loginWithEmail(email, newPassword)
            assertEquals(email.trim().lowercase(), user.email?.lowercase())
            if (expectedId != null) assertEquals(expectedId, user.userId)
            repository.logout()
        }
        try {
            repository.logout()
            if (stage == "send") {
                val existing = repository.loginWithEmail(email, oldPassword)
                // A recovery operation must not replace another active session.
                try {
                    repository.resetPasswordFromLink(email,
                        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/verify?token=unused-test-token&type=recovery",
                        oldPassword)
                    fail("Recovery replaced an active session.")
                } catch (_: IllegalStateException) {
                    assertEquals(existing, repository.getCurrentUser())
                }
                repository.logout()
                repository.sendPasswordResetEmail(email)
                assertNull(repository.getCurrentUser())
            } else {
                val newPassword = argument("supabaseRecoveryNewPassword")
                require(newPassword != oldPassword) { "Choose a different new test password." }
                if (stage == "verify") {
                    // Use after an interrupted completion; this needs no email/link.
                    verifyChangedPassword(newPassword)
                } else {
                    val link = argument("supabaseRecoveryLink")
                    val recovered = repository.resetPasswordFromLink(email, link, newPassword)
                    assertEquals(email.trim().lowercase(), recovered.email?.lowercase())
                    assertNull(repository.getCurrentUser())
                    verifyChangedPassword(newPassword, recovered.userId)
                    try {
                        repository.resetPasswordFromLink(email, link, newPassword)
                        fail("A consumed recovery link was accepted again.")
                    } catch (error: AuthRestException) {
                        assertEquals(AuthErrorCode.OtpExpired, error.errorCode)
                        assertNull(repository.getCurrentUser())
                    }
                }
            }
        } finally {
            repository.logout()
        }
    }
}
