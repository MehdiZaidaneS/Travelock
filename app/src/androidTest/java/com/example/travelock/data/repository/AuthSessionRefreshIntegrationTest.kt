package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.BuildConfig
import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthSessionRefreshIntegrationTest {
    // Revokes only the fresh test session, not other sessions for this account.
    @Test
    fun refreshesValidSessionsAndClearsRevokedRefreshCredentials() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val repository = AuthRepository()
        val supabase = SupabaseProvider.client
        try {
            repository.logout()
            assertNull(repository.refreshSession())
            val user = repository.loginWithEmail(argument("supabaseTestEmail"),argument("supabaseTestPassword"))
            assertEquals(user,repository.refreshSession())
            assertEquals(user.userId,supabase.auth.retrieveUserForCurrentSession().id)
            assertEquals(user.userId,supabase.auth.sessionManager.loadSession()?.user?.id)
            supabase.auth.stopAutoRefreshForCurrentSession()
            val session = checkNotNull(supabase.auth.currentSessionOrNull()) { "Expected the test session." }
            // This client shares the session only in memory, never the app's
            // persisted storage. Its LOCAL logout revokes that session remotely.
            val remote = createSupabaseClient(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
                install(Auth) {
                    sessionManager=MemorySessionManager()
                    autoLoadFromStorage=false
                    autoSaveToStorage=false
                    alwaysAutoRefresh=false
                    enableLifecycleCallbacks=false
                }
            }
            try {
                remote.auth.awaitInitialization()
                remote.auth.importSession(session,autoRefresh=false)
                remote.auth.signOut(SignOutScope.LOCAL)
            } finally { remote.close() }
            // The app still has the saved session until it attempts a refresh.
            assertTrue("Remote client must not clear the app's local storage.",supabase.auth.sessionManager.loadSession()!=null)
            assertNull(repository.refreshSession())
            assertNull(repository.getCurrentUser())
            // Assert booleans rather than printing UserSession on failure.
            assertTrue("Revoked session must be removed from storage.",supabase.auth.sessionManager.loadSession()==null)
            try {
                ProfileRepository().getMyProfile()
                fail("Signed-out profile access must be rejected.")
            } catch (_: IllegalStateException) { /* The frontend must return to sign-in. */ }
        } catch (error: RestException) {
            val code = (error as? AuthRestException)?.errorCode?.value ?: "unclassified"
            throw AssertionError("Session check failed: HTTP ${error.statusCode}, $code. No request/token details logged.")
        } finally { repository.logout() }
    }
}
