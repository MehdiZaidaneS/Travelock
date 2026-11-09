package com.example.travelock.ui.visit

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.travelock.R
import com.example.travelock.data.camera.VisitPhotoFiles
import com.example.travelock.data.model.VisitAttempt
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VisitCameraButton(
    visit: VisitAttempt,
    onPhotoCaptured: (VisitAttempt, ByteArray) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCancelled: () -> Unit = {},
) {
    val context = LocalContext.current
    val files = remember(context) { VisitPhotoFiles(context) }
    val scope = rememberCoroutineScope()
    val currentVisit by rememberUpdatedState(visit)
    val currentOnPhoto by rememberUpdatedState(onPhotoCaptured)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    val photoFailedMessage by rememberUpdatedState(stringResource(R.string.visit_photo_failed))
    val visitChangedMessage by rememberUpdatedState(stringResource(R.string.visit_photo_visit_changed))
    val cameraUnavailableMessage by rememberUpdatedState(stringResource(R.string.visit_camera_unavailable))

    // Keep only the filename/visit ID across recreation, never the image bytes.
    var pendingFile by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingVisitId by rememberSaveable { mutableStateOf<String?>(null) }
    var preparingPhoto by remember { mutableStateOf(false) }
    val contract = remember { VisitTakePicture() }
    val launcher = rememberLauncherForActivityResult(contract) { saved ->
        val file = pendingFile
        val requestedVisitId = pendingVisitId
        pendingFile = null
        pendingVisitId = null
        if (file == null) {
            currentOnError(photoFailedMessage)
        } else if (!saved) {
            files.delete(file)
            currentOnCancelled()
        } else if (requestedVisitId != currentVisit.visitId || currentVisit.completedAt != null || currentVisit.cancelledAt != null) {
            files.delete(file)
            currentOnError(visitChangedMessage)
        } else {
            val capturedVisit = currentVisit
            preparingPhoto = true
            scope.launch {
                val bytes = try {
                    withContext(Dispatchers.IO) { files.readJpeg(file) }
                } catch (_: IOException) {
                    currentOnError(photoFailedMessage)
                    null
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { files.delete(file) }
                    preparingPhoto = false
                }
                if (bytes != null) {
                    if (currentVisit.visitId == capturedVisit.visitId && currentVisit.completedAt == null && currentVisit.cancelledAt == null) {
                        currentOnPhoto(capturedVisit, bytes)
                    } else {
                        currentOnError(visitChangedMessage)
                    }
                }
            }
        }
    }

    Button(
        onClick = {
            try {
                val file = files.create()
                pendingFile = file
                pendingVisitId = visit.visitId
                // This contract opens camera capture only. No photo picker is used.
                launcher.launch(files.uri(file))
            } catch (_: ActivityNotFoundException) {
                pendingFile?.let(files::delete)
                pendingFile = null
                pendingVisitId = null
                currentOnError(cameraUnavailableMessage)
            } catch (_: IOException) {
                pendingFile?.let(files::delete)
                pendingFile = null
                pendingVisitId = null
                currentOnError(photoFailedMessage)
            } catch (_: SecurityException) {
                pendingFile?.let(files::delete)
                pendingFile = null
                pendingVisitId = null
                currentOnError(cameraUnavailableMessage)
            }
        },
        modifier = modifier,
        enabled = enabled && visit.completedAt == null && visit.cancelledAt == null && pendingFile == null && !preparingPhoto,
    ) {
        Text(stringResource(if (preparingPhoto) R.string.visit_photo_preparing else R.string.visit_photo_take))
    }
}

private class VisitTakePicture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newRawUri("visit-photo", input)
        }
}
