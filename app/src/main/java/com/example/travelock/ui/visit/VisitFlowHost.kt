package com.example.travelock.ui.visit

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Post
import com.example.travelock.data.qr.QrCodeScanner
import com.example.travelock.data.repository.DestinationRepository
import com.example.travelock.data.repository.UserEnrolmentRepository
import com.example.travelock.data.repository.UserUnlockRepository
import com.example.travelock.data.visit.VisitCompletionCoordinator
import com.example.travelock.ui.components.ExtendedDestinationCard
import com.example.travelock.ui.share.SharePostController
import com.example.travelock.ui.share.SharePostPanel
import kotlinx.coroutines.launch

// These existing paired theme roles retain readable contrast in both themes.
@Composable
internal fun visitButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
fun rememberVisitFlowController(userId: String): VisitFlowController {
    val context = LocalContext.current.applicationContext
    return remember(userId, context) {
        val store = VisitProgressStore(context)
        val unlocks = UserUnlockRepository()
        val enrolments = UserEnrolmentRepository()
        val completion = VisitCompletionCoordinator(context)
        val cancellation = VisitCancellationCoordinator(context)
        VisitFlowController(
            userId = userId, load = { store.load(userId) }, save = { store.save(userId, it) },
            getDestination = DestinationRepository()::getDestination,
            getEnrolledIds = { enrolments.getMyEnrolments().map { it.destinationId }.toSet() },
            enrol = { enrolments.enrolInDestination(it) }, unenrol = enrolments::unenrolFromDestination,
            start = unlocks::startVisit, capture = completion::completeCapturedVisit,
            retry = completion::completeUploadedVisit, cancel = { cancellation.cancel(it) },
        )
    }
}

@Composable
fun VisitFlowHost(
    controller: VisitFlowController,
    scanner: QrCodeScanner = rememberQrCodeScanner(),
    shareFactory: ((Destination) -> SharePostController)? = null,
    onViewInFeed: (Post) -> Unit = {},
    content: @Composable (VisitFlowActions) -> Unit,
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller) { controller.initialize() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!state.initialized) {
                state.error?.let { VisitErrorNotice(it) }
                if (state.busy) CircularProgressIndicator(Modifier.padding(16.dp))
                else Button(onClick = { scope.launch { controller.initialize() } }) { Text("Retry loading visits") }
            } else {
                if (state.destination == null) state.error?.let { VisitErrorNotice(it) }
                if (state.progress.visit != null && !state.progress.completed) {
                    OutlinedButton(enabled = !state.busy, modifier = Modifier.padding(horizontal = 20.dp),
                        onClick = { scope.launch { controller.resume() } }) { Text("Resume visit") }
                }
                content(VisitFlowActions(
                    onOpenDestination = { destination, unlocked -> scope.launch { controller.open(destination, unlocked) } },
                    revision = state.unlockRevision + state.enrolmentRevision,
                ))
            }
        }
        state.destination?.let { destination ->
            val shareController = remember(shareFactory, destination.destinationId) { shareFactory?.invoke(destination) }
            val shareState = shareController?.state?.collectAsState()?.value
            var sharing by remember(destination.destinationId) { mutableStateOf(false) }
            var confirmLeaveSharing by remember(destination.destinationId) { mutableStateOf(false) }
            val requestClose: () -> Unit = {
                if (!state.busy && shareState?.busy != true) {
                    if (sharing && shareState?.posted == null) confirmLeaveSharing = true
                    else controller.close()
                }
            }
            ExtendedDestinationCard(destination, onBack = requestClose,
                busy = state.busy || shareState?.busy == true, verifying = state.verifying, sharing = sharing) {
                if (sharing && shareController != null) {
                    SharePostPanel(shareController, destination, onBack = { sharing = false }, onDone = controller::close,
                        onViewInFeed = { post -> controller.close(); onViewInFeed(post) })
                } else {
                    VisitDestinationPanel(state, controller, scanner,
                        onShare = if (shareController == null) null else { { sharing = true } })
                }
            }
            if (confirmLeaveSharing) {
                AlertDialog(
                    onDismissRequest = { confirmLeaveSharing = false },
                    title = { Text("Leave sharing?") },
                    text = { Text(if (shareState?.request != null)
                        "Your post is awaiting confirmation. You can reopen this visit to check or retry the same post. Your completed visit stays saved."
                        else "This closes the preview without posting. Unposted caption and audience changes will be discarded. Your completed visit stays saved.") },
                    confirmButton = { TextButton(onClick = { confirmLeaveSharing = false; controller.close() }) { Text("Leave sharing") } },
                    dismissButton = { TextButton(onClick = { confirmLeaveSharing = false }) { Text("Keep editing") } },
                )
            }
        }
    }
}

data class VisitFlowActions(val onOpenDestination: (Destination, Boolean) -> Unit, val revision: Int)

@Composable
internal fun VisitErrorNotice(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VisitDestinationPanel(state: VisitFlowState, controller: VisitFlowController, scanner: QrCodeScanner, onShare: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val destination = checkNotNull(state.destination)
    val progress = state.progress
    val visit = progress.visit?.takeIf { it.destinationId == destination.destinationId }
    val enrolled = destination.destinationId in state.enrolledIds
    var confirmation by rememberSaveable(destination.destinationId) { mutableStateOf<String?>(null) }
    val active = visit != null && !progress.completed
    if (state.destinationUnlocked) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Destination unlocked!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Your visit to ${destination.name} has been verified and saved.", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        state.error?.let { VisitErrorNotice(it) }
        if (onShare != null) {
            Button(onClick = onShare, enabled = !state.busy, colors = visitButtonColors(), modifier = Modifier.fillMaxWidth()) { Text("Share photo") }
            TextButton(onClick = controller::close, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        } else {
            Button(onClick = controller::close, enabled = !state.busy, colors = visitButtonColors(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (enrolled) "Enrolled" else "Plan your visit", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text("50 m", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.verifying) {
            VerificationSteps(qrDone = visit != null, photoDone = progress.uploadedPhotoPath != null && visit != null)
        } else {
            Text("Scan the destination QR, then take a fresh photo within 50 metres to unlock it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        state.error?.let { VisitErrorNotice(it) }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Saving your visit…", style = MaterialTheme.typography.labelMedium)
        }
        when {
            !state.verifying -> {
                Button(enabled = !state.busy, colors = visitButtonColors(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                    scope.launch { if (enrolled) controller.beginVerification(destination) else controller.enrol(destination) }
                }) { Text(if (enrolled) "Verify visit" else "Enroll") }
            }
            visit == null -> {
                Text("Scan the QR code at ${destination.name}.", style = MaterialTheme.typography.bodyMedium)
                VisitQrScanButton(onQrScanned = { scope.launch { controller.scan(it) } }, onError = controller::showError,
                    enabled = !state.busy, scanner = scanner, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
            else -> {
                VisitLocationAccess(enabled = !state.busy)
                if (progress.uploadedPhotoPath != null) {
                    Text("Photo saved. Retry using your current location.", style = MaterialTheme.typography.bodyMedium)
                    Button(enabled = !state.busy, colors = visitButtonColors(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = { scope.launch { controller.retry() } }) { Text("Verify and unlock") }
                } else {
                    Text("Take a fresh photo of the destination. We’ll check your location and save the visit.",
                        style = MaterialTheme.typography.bodyMedium)
                    VisitCameraButton(visit, onPhotoCaptured = { captured, bytes -> scope.launch { controller.complete(captured, bytes) } },
                        onError = controller::showError, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                }
            }
        }
        if (active) {
            TextButton(enabled = !state.busy, onClick = controller::close, modifier = Modifier.fillMaxWidth()) { Text("Continue later") }
            TextButton(enabled = !state.busy, onClick = { confirmation = "cancel" }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel verification")
            }
        }
        if (enrolled) {
            TextButton(enabled = !state.busy, onClick = { confirmation = "unenroll" }, modifier = Modifier.fillMaxWidth()) { Text("Unenroll") }
        }
    }
    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (action == "cancel") "Cancel verification?" else "Unenroll from this visit?") },
            text = { Text(when {
                action == "cancel" -> "Your unfinished verification and its uploaded photo will be removed. You’ll stay enrolled."
                active -> "Your unfinished verification and its uploaded photo will be removed, then this destination will leave Your visits."
                else -> "This removes the destination from Your visits. You can enroll again later. Completed unlocks and achievements stay saved."
            }) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    scope.launch { if (action == "cancel") controller.cancel() else controller.unenrol() }
                }) { Text(if (action == "cancel") "Cancel verification" else "Confirm unenroll") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Keep visit") } },
        )
    }
}

@Composable
private fun VerificationSteps(qrDone: Boolean, photoDone: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("QR" to qrDone, "Photo" to photoDone, "Unlock" to false).forEachIndexed { index, (label, done) ->
            val current = index == when { !qrDone -> 0; !photoDone -> 1; else -> 2 }
            Surface(modifier = Modifier.weight(1f).semantics { stateDescription = if (done) "Completed" else if (current) "Current step" else "Upcoming" },
                shape = MaterialTheme.shapes.medium,
                color = if (done || current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (done) Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    else Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
