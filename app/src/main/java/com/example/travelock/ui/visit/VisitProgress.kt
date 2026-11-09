package com.example.travelock.ui.visit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.travelock.data.model.VisitAttempt
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Save only the attempt and uploaded object path. Camera bytes stay out of the Bundle.
@Serializable
data class VisitProgress(
    val visit: VisitAttempt? = null,
    val uploadedPhotoPath: String? = null,
    val completed: Boolean = false,
)

@Composable
fun rememberVisitProgress(
    userId: String,
    initialProgress: VisitProgress = VisitProgress(),
): MutableState<VisitProgress> {
    val saver = Saver<VisitProgress, String>(
        save = { Json.encodeToString(it) },
        restore = { saved ->
            runCatching { Json.decodeFromString<VisitProgress>(saved) }.getOrNull()
                ?.takeIf { it.visit == null || it.visit.userId == userId }
        },
    )
    return rememberSaveable(userId, stateSaver = saver) {
        mutableStateOf(initialProgress.takeIf { it.visit == null || it.visit.userId == userId } ?: VisitProgress())
    }
}
