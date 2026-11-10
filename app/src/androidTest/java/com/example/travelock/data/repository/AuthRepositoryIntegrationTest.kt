package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryIntegrationTest {
    // Uses an existing confirmed account. Does not send emails or create accounts.
    @Test
    fun signsInReadsSessionAndSignsOut() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = requireNotNull(arguments.getString("supabaseTestEmail")) {
            "Add the local supabaseTestEmail argument."
        }
        val password = requireNotNull(arguments.getString("supabaseTestPassword")) {
            "Add the local supabaseTestPassword argument."
        }
        val repository = AuthRepository()
        val supabase = SupabaseProvider.client
        try {
            repository.logout()
            assertNull(repository.getCurrentUser())
            val user = repository.loginWithEmail("  $email  ", password)
            assertEquals(email.trim(), user.email)
            assertEquals(checkNotNull(supabase.auth.currentUserOrNull()).id, user.userId)
            assertEquals(user, repository.getCurrentUser())
            assertNotNull(supabase.auth.currentSessionOrNull())
            try {
                repository.registerWithEmail(email, password)
                fail("Registration must not replace an active session.")
            } catch (_: IllegalStateException) {
                assertEquals(user, repository.getCurrentUser())
            }
            repository.logout()
            assertNull(repository.getCurrentUser())
            assertNull(supabase.auth.currentSessionOrNull())
            try {
                repository.loginWithEmail(email, "wrong-${UUID.randomUUID()}")
                fail("An incorrect password must not create a session.")
            } catch (error: AuthRestException) {
                assertEquals(AuthErrorCode.InvalidCredentials, error.errorCode)
                assertNull(repository.getCurrentUser())
            }
        } finally {
            repository.logout()
        }
    }
}
