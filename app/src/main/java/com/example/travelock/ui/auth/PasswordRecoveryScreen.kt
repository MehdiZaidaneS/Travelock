package com.example.travelock.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.travelock.data.auth.RegistrationPasswordPolicy

@Composable
fun PasswordRecoveryScreen(
    state: AuthFlowState,
    onSubmit: (String, String, String, String) -> Unit,
    onResend: (String) -> Unit,
    onBack: () -> Unit,
    onPasswordEdited: () -> Unit,
) {
    val email = state.pendingEmail
    // Recovery codes grant account access. Keep codes/passwords out of saved state.
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val missing = RegistrationPasswordPolicy.missingRequirements(password)
    AuthStepLayout {
        Text("Reset your password", style = MaterialTheme.typography.headlineLarge)
        state.info?.let { Text(it) }
        Text("Enter the recovery code sent to $email and choose a new password.")
        Text("An expired or used code needs a new request. A failed password update may also consume the code.")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(code, { code = it }, label = { Text("Recovery code") },
            enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        OutlinedTextField(password, { password = it; onPasswordEdited() }, label = { Text("New password") },
            enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = state.passwordError != null || (password.isNotEmpty() && missing.isNotEmpty()),
            supportingText = {
                Text(state.passwordError ?: if (missing.isEmpty()) "Password meets the requirements."
                    else missing.joinToString(". ") + ".")
            })
        OutlinedTextField(confirmation, { confirmation = it; onPasswordEdited() },
            label = { Text("Confirm new password") }, enabled = !state.busy, singleLine = true,
            modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = confirmation.isNotEmpty() && password != confirmation,
            supportingText = { if (confirmation.isNotEmpty() && password != confirmation) Text("Passwords do not match.") })
        Button(onClick = { onSubmit(email, code, password, confirmation) },
            enabled = !state.busy && email.isNotBlank() && code.isNotBlank() && code.trim().all { it in '0'..'9' } &&
                password.isNotEmpty() && missing.isEmpty() && password == confirmation) {
            Text(if (state.busy) "Please wait…" else "Update password")
        }
        TextButton(onClick = { code = ""; onResend(email) }, enabled = !state.busy) { Text("Send a new code") }
        TextButton(onClick = onBack, enabled = !state.busy) { Text("Back to sign in") }
    }
}

@Composable
fun PasswordResetEmailScreen(state: AuthFlowState, onSend: (String) -> Unit, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(state.pendingEmail) }
    AuthStepLayout {
        Text("Forgot password", style = MaterialTheme.typography.headlineLarge)
        Text("Enter your account email. We will send you a recovery code.")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(email, { email = it }, label = { Text("Email") },
            enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Button(onClick = { onSend(email) }, enabled = !state.busy && email.isNotBlank()) {
            Text(if (state.busy) "Please wait…" else "Send recovery code")
        }
        TextButton(onClick = onBack, enabled = !state.busy) { Text("Back to sign in") }
    }
}
