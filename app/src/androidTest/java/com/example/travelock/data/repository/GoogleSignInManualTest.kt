package com.example.travelock.data.repository

import android.content.MutableContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.auth.GoogleSignInNonce
import com.example.travelock.data.model.AuthUser
import com.example.travelock.data.model.Profile
import com.example.travelock.data.model.ProfileOnboardingState
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class GoogleSignInManualTest {
    @get:Rule val compose = createComposeRule()

    // Opt-in real account picker. Keeps the Supabase Auth account/Google consent;
    // creates no app profile and never accepts a pasted or logged Google token.
    @Test
    fun signsInTwiceWithTheSameGoogleAccountAndKeepsOnboardingSeparate() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Set manualGoogleSignInTest=true explicitly.",args.getString("manualGoogleSignInTest")=="true")
        val webClientId = requireNotNull(args.getString("googleWebClientId")) { "Add local googleWebClientId." }
        require(webClientId.endsWith(".apps.googleusercontent.com")) { "Use the Google Web OAuth client ID." }
        val expectedEmail = requireNotNull(args.getString("supabaseGoogleTestEmail")) { "Add local supabaseGoogleTestEmail." }
        val auth = AuthRepository()
        val profiles = ProfileRepository()
        val completed = AtomicInteger()
        val failure = AtomicReference<String?>(null)
        val firstUser = AtomicReference<AuthUser?>(null)
        val firstProfile = AtomicReference<Profile?>(null)
        runBlocking { auth.logout() }
        try {
            compose.setContent {
                val context = LocalContext.current
                val credentials = remember(context) { CredentialManager.create(context) }
                val scope = rememberCoroutineScope()
                var busy by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("Choose your test Google account. Repeat with the same account when asked.") }
                MaterialTheme {
                    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("Google sign-in backend test")
                        Text(message)
                        Button(enabled=!busy && completed.get()<2 && failure.get()==null,onClick={
                            busy=true
                            scope.launch {
                                var step="requesting a Google credential"
                                try {
                                    val nonce = GoogleSignInNonce.create()
                                    val option = GetSignInWithGoogleOption.Builder(webClientId)
                                        .setNonce(nonce.hashedNonceForGoogle).build()
                                    val response = credentials.getCredential(MutableContextWrapper(context),
                                        GetCredentialRequest.Builder().addCredentialOption(option).build())
                                    val credential = response.credential
                                    check(credential is CustomCredential && credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        "Google did not return an ID-token credential."
                                    }
                                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                    if (completed.get()==0) {
                                        step="checking server nonce rejection"
                                        try {
                                            auth.signInWithGoogle(idToken,GoogleSignInNonce.create())
                                            fail("A mismatched nonce was accepted. Keep Supabase nonce checking enabled.")
                                        } catch (error: RestException) {
                                            // SDK 3.2.6 may use a generic BadRequestRestException
                                            // for an OAuth nonce error without an Auth error code.
                                            val detail = "${error.error} ${error.description.orEmpty()}"
                                            assertTrue("Expected nonce rejection; check provider configuration.",
                                                error.statusCode in 400..499 &&
                                                    detail.contains("nonce",ignoreCase=true) &&
                                                    detail.contains("mismatch",ignoreCase=true))
                                            assertNull(auth.getCurrentUser())
                                        }
                                    }
                                    step="exchanging the Google token"
                                    val user = auth.signInWithGoogle(idToken,nonce)
                                    step="checking the selected Google account"
                                    check(user.email.equals(expectedEmail.trim(),ignoreCase=true)) { "Choose the configured test Google account." }
                                    assertEquals(user,auth.getCurrentUser())
                                    step="loading profile onboarding"
                                    val profile = profiles.getMyProfile()
                                    val state = ProfileOnboardingRepository().getState()
                                    if (profile==null) assertEquals(ProfileOnboardingState.NeedsProfile(user),state)
                                    else assertEquals(ProfileOnboardingState.Ready(user,profile),state)
                                    if (completed.get()==0) {
                                        firstUser.set(user); firstProfile.set(profile)
                                    } else {
                                        assertEquals(firstUser.get(),user)
                                        assertEquals(firstProfile.get(),profile)
                                    }
                                    step="checking the active-session guard"
                                    try {
                                        auth.signInWithGoogle("unused-test-token",GoogleSignInNonce.create())
                                        fail("Google sign-in replaced an active session.")
                                    } catch (_: IllegalStateException) { assertEquals(user,auth.getCurrentUser()) }
                                    step="signing out of Supabase"
                                    auth.logout()
                                    assertNull(auth.getCurrentUser())
                                    step="checking the consumed sign-in attempt"
                                    try {
                                        auth.signInWithGoogle(idToken,nonce)
                                        fail("A consumed sign-in attempt was accepted.")
                                    } catch (_: IllegalStateException) { assertNull(auth.getCurrentUser()) }
                                    step="clearing Google's credential state"
                                    credentials.clearCredentialState(ClearCredentialStateRequest())
                                    val count = completed.incrementAndGet()
                                    message = if (count==1) "First sign-in passed. Tap again and choose the same account."
                                        else "Both sign-ins passed. Test complete."
                                } catch (_: GetCredentialCancellationException) {
                                    message="Google sign-in cancelled. Tap again to retry."
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Throwable) {
                                    // Do not put tokens, credential Bundles or server response bodies in logs/UI.
                                    val safe = when (error) {
                                        is AuthRestException -> "Supabase Auth: ${error.errorCode?.value ?: "unrecognised error"} (HTTP ${error.statusCode})"
                                        is RestException -> {
                                            // Never use error.message: this SDK includes request headers there.
                                            val detail = "${error.error} ${error.description.orEmpty()}".lowercase()
                                            val reason = when {
                                                "nonce" in detail -> "nonce validation failed"
                                                "audience" in detail -> "Google client ID not allowed"
                                                "provider" in detail && ("disabled" in detail || "not enabled" in detail) -> "Google provider disabled"
                                                "expired" in detail -> "token expired"
                                                "signature" in detail -> "token signature rejected"
                                                else -> "request rejected; check Supabase Auth logs"
                                            }
                                            "Supabase: $reason (HTTP ${error.statusCode})"
                                        }
                                        is GetCredentialException -> "Google credential error: ${error.type}"
                                        is AssertionError -> error.message ?: "Assertion failed"
                                        else -> error.javaClass.simpleName
                                    }
                                    failure.set("$step: $safe"); message="$step: $safe"
                                } finally { busy=false }
                            }
                        }) { Text("Sign in with Google") }
                    }
                }
            }
            compose.waitUntil(timeoutMillis=300_000) { completed.get()==2 || failure.get()!=null }
            assertNull("Google sign-in check failed: ${failure.get()}",failure.get())
            assertEquals(2,completed.get())
        } finally { runBlocking { auth.logout() } }
    }
}
