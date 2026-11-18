package com.example.travelock.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.travelock.data.model.User
import com.example.travelock.ui.auth.ProfileAvatarPresets
import com.example.travelock.ui.components.ProfilePhotoAvatar

/**
 * PBI-29: Edit Personal Profile Screen backed by live Supabase persistence.
 * Allows editing username, full name, and profile image (preset avatar or uploaded photo).
 * Email is strictly read-only.
 */
@Composable
fun EditProfileScreen(
    editState: EditProfileUiState,
    currentUser: User,
    onUsernameChange: (String) -> Unit,
    onFullNameChange: (String) -> Unit,
    onImageChange: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

    val selectedAvatar = ProfileAvatarPresets.find(editState.selectedImage)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImageChange(uri.toString())
        }
    }

    val handleCancel = {
        if (editState.hasChanges && !editState.isSaving) {
            showDiscardDialog = true
        } else {
            onCancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Edit Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Current / Pending Avatar Preview
        val selectedImg = editState.selectedImage
        when {
            selectedAvatar != null -> {
                Image(
                    painter = painterResource(selectedAvatar.imageRes),
                    contentDescription = "Selected avatar",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )
            }
            selectedImg != null && selectedImg != currentUser.profileImage -> {
                AsyncImage(
                    model = Uri.parse(selectedImg),
                    contentDescription = "Selected profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )
            }
            else -> {
                val previewUser = currentUser.copy(
                    username = editState.username,
                    name = editState.fullName.ifBlank { currentUser.name },
                    profileImage = selectedImg ?: ""
                )
                ProfilePhotoAvatar(user = previewUser, size = 96.dp)
            }
        }

        Text(
            text = "Choose an avatar",
            style = MaterialTheme.typography.titleMedium
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileAvatarPresets.all.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { avatar ->
                        val isSelected = editState.selectedImage == avatar.id
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                                .selectable(
                                    selected = isSelected,
                                    enabled = !editState.isSaving,
                                    role = Role.RadioButton,
                                    onClick = { onImageChange(avatar.id) }
                                )
                                .padding(4.dp)
                        ) {
                            Image(
                                painter = painterResource(avatar.imageRes),
                                contentDescription = avatar.label,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !editState.isSaving,
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Text(
                    if (editState.selectedImage == null || selectedAvatar != null) {
                        "Upload photo"
                    } else {
                        "Change photo"
                    }
                )
            }

            if (editState.selectedImage != null) {
                TextButton(
                    enabled = !editState.isSaving,
                    onClick = { onImageChange(null) }
                ) {
                    Text("Remove photo")
                }
            }
        }

        if (!editState.generalError.isNullOrBlank()) {
            Text(
                text = editState.generalError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = editState.email,
            onValueChange = {},
            label = { Text("Email (read-only)") },
            enabled = false,
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = editState.username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            enabled = !editState.isSaving,
            isError = editState.usernameError != null,
            supportingText = {
                Text(
                    editState.usernameError
                        ?: "3–30 characters (case-sensitive)"
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = editState.fullName,
            onValueChange = onFullNameChange,
            label = { Text("Full Name") },
            enabled = !editState.isSaving,
            isError = editState.fullNameError != null,
            supportingText = {
                editState.fullNameError?.let { Text(it) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = handleCancel,
                enabled = !editState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onSave,
                enabled = !editState.isSaving && editState.hasChanges,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (editState.isSaving) "Saving…" else "Save Changes")
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved profile edits. Are you sure you want to leave?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onCancel()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
    }
}
