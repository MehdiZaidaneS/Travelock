package com.example.travelock.ui.auth

import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.travelock.BuildConfig
import com.example.travelock.data.auth.GoogleSignInNonce
import com.example.travelock.data.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

class GoogleAccountSignIn(private val context: Context) {
    private val credentials = CredentialManager.create(context)

    suspend fun signIn() {
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            "Configure GOOGLE_WEB_CLIENT_ID in local.properties and rebuild."
        }
        val nonce = GoogleSignInNonce.create()
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce.hashedNonceForGoogle).build()
        val response = try {
            credentials.getCredential(MutableContextWrapper(context),
                GetCredentialRequest.Builder().addCredentialOption(option).build())
        } catch (_: GetCredentialCancellationException) {
            throw CancellationException("Google sign-in cancelled")
        }
        val credential = response.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        AuthRepository().signInWithGoogle(token, nonce)
    }

    suspend fun clearCredentialState() = credentials.clearCredentialState(ClearCredentialStateRequest())
}
