package com.example.travelock.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.example.travelock.data.model.User
import com.example.travelock.data.repository.ProfilePhotoRepository
import kotlinx.coroutines.CancellationException
import com.example.travelock.ui.auth.ProfileAvatarPresets
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@Composable
fun ProfilePhotoAvatar(user: User, size: Dp) {
    val preset = ProfileAvatarPresets.find(user.profileImage)
    if (preset != null) {
        Image(painterResource(preset.imageRes), contentDescription = "Your profile avatar",
            modifier = Modifier.size(size).clip(CircleShape))
        return
    }
    val photo by produceState<ByteArray?>(null, user.userId, user.profileImage) {
        value = null
        if (user.profileImage.isNotBlank()) {
            value = try { ProfilePhotoRepository().downloadProfilePhoto(user.profileImage) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
        }
    }
    if (photo == null) UserAvatarCircle(user.name, user.username, size)
    else AsyncImage(model = photo, contentDescription = "Your profile photo",
        contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
}
