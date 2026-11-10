package com.example.travelock.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.data.repository.UserRepository
import com.example.travelock.ui.components.UserAvatarCircle
import kotlinx.coroutines.launch

/**
 * PBI-29: Edit Personal Profile Screen.
 */
@Composable
fun EditProfileScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    userRepository: UserRepository = remember { UserRepository() }
) {
    val scope = rememberCoroutineScope()
    val currentUser by userRepository.currentUserFlow.collectAsState()
    val user = currentUser ?: MockUsers.currentUser

    var username by remember(user.username) { mutableStateOf(user.username) }
    var fullName by remember(user.name) { mutableStateOf(user.name) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Edit Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        UserAvatarCircle(
            name = fullName.ifBlank { user.name },
            username = username,
            size = 84.dp
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    scope.launch {
                        userRepository.updateMyProfile(username, fullName, user.profileImage)
                            .onSuccess { onSaved() }
                            .onFailure { errorMessage = it.message }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Changes")
            }
        }
    }
}

