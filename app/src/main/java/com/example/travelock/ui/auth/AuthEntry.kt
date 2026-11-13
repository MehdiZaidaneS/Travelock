package com.example.travelock.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.travelock.data.model.ProfileOnboardingState

@Composable
fun AuthEntry(flow: AuthFlowController, state: AuthFlowState) {
    val context = LocalContext.current
    val google = remember(context) { GoogleAccountSignIn(context) }
    val signOut: () -> Unit = { flow.logout { google.clearCredentialState() } }
    BackHandler(enabled = state.page in setOf(AuthPage.LOGIN, AuthPage.REGISTER, AuthPage.CONFIRM_EMAIL)) {
        flow.navigate(AuthPage.LANDING)
    }
    when (state.page) {
        AuthPage.LANDING -> LandingScreen(
            onNavigateToLogin = { flow.navigate(AuthPage.LOGIN) },
            onNavigateToRegister = { flow.navigate(AuthPage.REGISTER) })
        AuthPage.LOGIN -> LoginScreen(
            onLoginClick = flow::login,
            onGoogleSignInClick = { flow.google { google.signIn() } },
            onResetPasswordClick = flow::resetPassword,
            onNavigateToRegister = { flow.navigate(AuthPage.REGISTER) },
            onNavigateBack = { flow.navigate(AuthPage.LANDING) },
            errorMessage = state.error, infoMessage = state.info, isLoading = state.busy)
        AuthPage.REGISTER -> RegisterScreen(
            onRegisterClick = flow::register,
            onGoogleSignInClick = { flow.google { google.signIn() } },
            onNavigateToLogin = { flow.navigate(AuthPage.LOGIN) },
            onNavigateBack = { flow.navigate(AuthPage.LANDING) },
            errorMessage = state.error, isLoading = state.busy,
            passwordError = state.passwordError, onPasswordEdited = flow::clearPasswordError)
        AuthPage.CONFIRM_EMAIL -> AuthStepLayout {
            Text("Check your email", style = MaterialTheme.typography.headlineLarge)
            Text("If registration can proceed for ${state.pendingEmail}, you will receive a confirmation email. Open its link to confirm your address, then return here and sign in to create your profile.")
            Text("Check your spam folder too. You can close the app and sign in after confirming.")
            Button(onClick = { flow.navigate(AuthPage.LOGIN) }) { Text("Continue to sign in") }
            TextButton(onClick = { flow.navigate(AuthPage.REGISTER) }) { Text("Use a different email") }
        }
        AuthPage.PROFILE -> {
            val user = (state.onboarding as ProfileOnboardingState.NeedsProfile).user
            ProfileSetupScreen(user.userId, user.email, state.busy, state.error,
                onSave = { username, name, photo ->
                    val preset = ProfileAvatarPresets.find(photo)
                    flow.completeProfile(username, name, presetAvatar = preset?.id) {
                        photo?.let { ProfileImageSelection.readJpeg(context.applicationContext, android.net.Uri.parse(it)) }
                    }
                }, onSignOut = signOut)
        }
        AuthPage.CHECKING, AuthPage.RETRY -> AuthStepLayout {
            if (state.busy || state.page == AuthPage.CHECKING) {
                CircularProgressIndicator()
                Text("Loading your account…")
            } else {
                Text(state.error ?: "Unable to load your account.", color = MaterialTheme.colorScheme.error)
                Button(onClick = flow::restore) { Text("Retry") }
                TextButton(onClick = signOut) { Text("Sign out") }
            }
        }
        AuthPage.READY -> Unit
    }
}
