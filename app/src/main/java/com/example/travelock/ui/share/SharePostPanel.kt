package com.example.travelock.ui.share

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import com.example.travelock.data.repository.PostPhotoRepository
import com.example.travelock.data.repository.PostRepository
import com.example.travelock.data.repository.UserUnlockRepository
import com.example.travelock.data.repository.VisitPhotoRepository
import com.example.travelock.ui.visit.visitButtonColors
import com.example.travelock.ui.visit.VisitErrorNotice
import kotlinx.coroutines.launch

@Composable
fun rememberSharePostFactory(userId: String): (Destination) -> SharePostController {
    val context = LocalContext.current.applicationContext
    return remember(userId, context) {
        val posts = PostRepository()
        val photos = PostPhotoRepository()
        val visits = UserUnlockRepository()
        val visitPhotos = VisitPhotoRepository()
        val drafts = PostDraftStore(context)
        val publisher = PostPublishingCoordinator(posts::getMyPost, photos::uploadVisitPhoto) { draft, path ->
            posts.createPost(draft.postId, draft.visitId, path, draft.caption, draft.visibility)
        }
        val factory: (Destination) -> SharePostController = { destination ->
            SharePostController(
                userId, destination.destinationId,
                loadVisit = { visits.getLatestCompletedVisit(destination.destinationId) },
                existingPost = posts::getMyLatestPostForVisit,
                loadPhoto = visitPhotos::downloadCompletedPhoto,
                loadDraft = { drafts.load(userId, it) },
                saveDraft = drafts::save, clearDraft = { drafts.clear(userId, it) },
                publish = publisher::publish,
            )
        }
        factory
    }
}

@Composable
internal fun SharePostPanel(
    controller: SharePostController,
    destination: Destination,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onViewInFeed: (Post) -> Unit,
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(controller) { controller.prepare() }
    val posted = state.posted
    if (posted != null) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text(if (state.alreadyShared) "Already shared" else "Photo shared", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(if (posted.visibility == PostVisibility.FRIENDS) "Visible to your friends."
                    else "Visible to signed-in Travelock users.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = { onViewInFeed(posted) }, colors = visitButtonColors(), enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("View in feed") }
        TextButton(onClick = onDone, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    } else {
        Text("Sharing is optional. Your completed visit stays saved even if you don’t post.",
            style = MaterialTheme.typography.bodyMedium)
        state.photo?.let { bytes ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                AsyncImage(model = bytes, contentDescription = "Visit photo from ${destination.name}",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            }
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (state.request != null) "Publishing your post…" else "Loading your visit photo…",
                style = MaterialTheme.typography.labelMedium)
        }
        if (state.ready) {
            val editable = !state.busy && state.request == null
            OutlinedTextField(value = state.caption, onValueChange = controller::setCaption,
                label = { Text("Caption (optional)") }, minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth(), enabled = editable)
            Text("Who can see this post?", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                AudienceOption("Friends", "Only you and your friends", state.visibility == PostVisibility.FRIENDS, editable) {
                    controller.setVisibility(PostVisibility.FRIENDS)
                    focus.clearFocus(); keyboard?.hide()
                }
                AudienceOption("Public", "All signed-in Travelock users", state.visibility == PostVisibility.PUBLIC, editable) {
                    controller.setVisibility(PostVisibility.PUBLIC)
                    focus.clearFocus(); keyboard?.hide()
                }
            }
            if (state.request != null) Text("This post is awaiting confirmation. Retry uses the same photo, caption and audience.",
                style = MaterialTheme.typography.bodySmall)
            state.error?.let { VisitErrorNotice(it) }
            Button(onClick = { focus.clearFocus(); keyboard?.hide(); scope.launch { controller.post() } }, enabled = !state.busy,
                colors = visitButtonColors(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (state.request == null) "Post" else "Retry post")
            }
        } else if (!state.busy) {
            state.error?.let { VisitErrorNotice(it) }
            Button(onClick = { scope.launch { controller.prepare() } }, colors = visitButtonColors(),
                modifier = Modifier.fillMaxWidth()) { Text("Retry loading photo") }
        }
        TextButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.request == null) "Not now" else "Close preview")
        }
    }
}

@Composable
private fun AudienceOption(label: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
