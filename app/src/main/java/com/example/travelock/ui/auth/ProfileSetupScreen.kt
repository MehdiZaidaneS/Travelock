package com.example.travelock.ui.auth

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role

@Composable
fun ProfileSetupScreen(
    accountId: String,
    email: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onSave: (String, String, String?) -> Unit,
    onSignOut: () -> Unit,
) {
    var username by rememberSaveable(accountId) { mutableStateOf("") }
    var fullName by rememberSaveable(accountId) { mutableStateOf("") }
    var selectedImage by rememberSaveable(accountId) { mutableStateOf<String?>(null) }
    val selectedAvatar = ProfileAvatarPresets.find(selectedImage)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Cancelling the picker preserves an earlier selection.
        if (uri != null) selectedImage = uri.toString()
    }
    // Leaving setup must not reveal the authenticated app shell.
    BackHandler { }
    AuthStepLayout {
        Text("Create your profile", style = MaterialTheme.typography.headlineLarge)
        Text("Your account is ready${email?.let { " ($it)" }.orEmpty()}. Choose how you appear in Travelock.")
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (selectedAvatar != null) {
            Image(painterResource(selectedAvatar.imageRes), contentDescription = "Selected avatar",
                modifier = Modifier.size(96.dp).clip(CircleShape))
        } else if (selectedImage != null) {
            AsyncImage(model = Uri.parse(selectedImage), contentDescription = "Selected profile photo",
                contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(CircleShape))
        }
        Text("Profile photo (optional)", style = MaterialTheme.typography.bodyMedium)
        Text("Choose an avatar", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileAvatarPresets.all.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { avatar ->
                        val isSelected = selectedImage == avatar.id
                        Box(Modifier.size(64.dp).clip(CircleShape)
                            .border(if (isSelected) 3.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape)
                            .selectable(isSelected, enabled = !isLoading, role = Role.RadioButton,
                                onClick = { selectedImage = avatar.id })
                            .padding(4.dp)) {
                            Image(painterResource(avatar.imageRes), contentDescription = avatar.label,
                                modifier = Modifier.fillMaxSize().clip(CircleShape))
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !isLoading, onClick = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (selectedImage == null || selectedAvatar != null) "Upload photo" else "Change photo") }
            TextButton(enabled = !isLoading,
                onClick = { selectedImage = null }) { Text(if (selectedImage == null) "Skip photo" else "Remove photo") }
        }
        if (selectedImage == null) Text("No photo selected. Your initials will be used.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = username, onValueChange = { username = it },
            label = { Text("Username") }, singleLine = true, enabled = !isLoading, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = fullName, onValueChange = { fullName = it },
            label = { Text("Full name") }, singleLine = true, enabled = !isLoading, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSave(username, fullName, selectedImage) }, enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()) { Text(if (isLoading) "Saving…" else "Save profile and continue") }
        TextButton(onClick = onSignOut, enabled = !isLoading) { Text("Sign out") }
    }
}

@Composable
internal fun AuthStepLayout(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
