package com.example.travelock.data.repository

import android.content.Context
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthSessionPersistenceManualTest {
    // Each stage runs in a new target-app process. The first deliberately leaves
    // its session saved. No passwords/tokens are copied into test checkpoints.
    @Test
    fun restoresSavedSessionsAndKeepsLogoutAcrossProcessRestarts() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val stage = args.getString("authSessionStage")
        assumeTrue("Choose signIn, restoreAndLogout or verifyLogout explicitly.",
            stage in listOf("signIn","restoreAndLogout","verifyLogout"))
        val checkpoint = instrumentation.targetContext.getSharedPreferences("session_restart_test",Context.MODE_PRIVATE)
        val repository = AuthRepository()
        val supabase = SupabaseProvider.client
        fun save(next: String, userId: String) {
            check(checkpoint.edit().putString("stage",next).putString("user_id",userId)
                .putInt("pid",Process.myPid()).commit()) { "Could not save the test checkpoint." }
        }
        fun checkRestart(expectedStage: String): String {
            assertEquals("Run the stages in the documented order.",expectedStage,checkpoint.getString("stage",null))
            assertTrue("Stop and reopen the app process; do not reuse the same running test.",
                checkpoint.getInt("pid",-1)!=Process.myPid())
            return checkNotNull(checkpoint.getString("user_id",null)) { "Missing earlier stage checkpoint." }
        }
        try {
            when (stage) {
                "signIn" -> {
                    repository.logout()
                    val email = requireNotNull(args.getString("supabaseTestEmail")) { "Add local supabaseTestEmail." }
                    val password = requireNotNull(args.getString("supabaseTestPassword")) { "Add local supabaseTestPassword." }
                    val user = repository.loginWithEmail(email,password)
                    assertEquals(user.userId,supabase.auth.sessionManager.loadSession()?.user?.id)
                    save("signed_in",user.userId)
                    // Do not sign out here: the next process must restore it.
                }
                "restoreAndLogout" -> {
                    val userId = checkRestart("signed_in")
                    val restored = checkNotNull(repository.getCurrentUser()) { "Saved session did not restore." }
                    assertEquals(userId,restored.userId)
                    assertEquals(userId,supabase.auth.retrieveUserForCurrentSession().id)
                    assertEquals(restored,repository.refreshSession())
                    repository.logout()
                    assertNull(repository.getCurrentUser())
                    assertTrue("Logout must remove the saved session.",supabase.auth.sessionManager.loadSession()==null)
                    save("logged_out",userId)
                }
                "verifyLogout" -> {
                    val userId = checkRestart("logged_out")
                    assertNull(repository.getCurrentUser())
                    assertTrue("Signed-out state must survive a new process.",supabase.auth.sessionManager.loadSession()==null)
                    assertNull(repository.refreshSession())
                    save("verified",userId)
                }
            }
        } catch (error: RestException) {
            val code = (error as? AuthRestException)?.errorCode?.value ?: "unclassified"
            throw AssertionError("Session stage $stage failed: HTTP ${error.statusCode}, $code. No request/token details logged.")
        }
    }
}
