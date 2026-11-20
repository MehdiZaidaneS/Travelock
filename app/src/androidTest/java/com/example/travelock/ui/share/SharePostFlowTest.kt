package com.example.travelock.ui.share

import android.graphics.Bitmap
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.PostVisibility
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.ui.theme.ThemeMode
import com.example.travelock.ui.theme.TravelockTheme
import com.example.travelock.ui.visit.VisitFlowController
import com.example.travelock.ui.visit.VisitFlowHost
import com.example.travelock.ui.visit.VisitProgress
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

// Real production sheet/composer, fake repositories. No Supabase writes or camera.
class SharePostFlowTest {
    @get:Rule val compose = createComposeRule()
    private val destination = Destination("destination", "city", "Test Library", "A completed visit", 0f, 60.0, 24.0, "", "QR")
    private val visit = VisitAttempt("visit", "user", "destination", Instant.EPOCH,
        completedAt = Instant.EPOCH, photoPath = "user/visit/photo.jpg")
    private val photo = ByteArrayOutputStream().use { out ->
        val bitmap = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(0, 137, 123))
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); bitmap.recycle(); out.toByteArray()
    }
    private var posts = 0
    private var photoReads = 0
    private var savedRequest: PostPublishDraft? = null
    private var storedPost: Post? = null
    private var viewedPost: Post? = null
    private var fail = false
    private val sharing = SharePostController("user", "destination", { visit }, { storedPost },
        { photoReads++; photo }, { savedRequest }, { savedRequest = it }, { savedRequest = null },
        { request, _ ->
            posts++
            if (fail) throw IOException("Authorization=Bearer SYNTHETIC_SECRET")
            Post(request.postId, "user", request.imagePath, request.caption, "destination", Instant.EPOCH,
                visibility = request.visibility, visitId = "visit").also { storedPost = it }
        }, { "post" })
    private val visits = VisitFlowController("user", { VisitProgress() }, {}, { destination }, { emptySet() }, {},
        { error("No scan required") }, { _, _, _ -> error("No capture required") },
        { _, _ -> error("No verification required") }, { error("Must not cancel a completed visit") }, {})

    private fun show(theme: ThemeMode = ThemeMode.LIGHT, largeText: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (largeText) Density(density.density * 1.15f, 1.5f) else density) {
                TravelockTheme(themeMode = theme) {
                    VisitFlowHost(visits, shareFactory = { sharing }, onViewInFeed = { viewedPost = it }) { actions ->
                        Button(onClick = { actions.onOpenDestination(destination, true) }) { Text("View completed destination") }
                    }
                }
            }
        }
        compose.waitUntil { visits.state.value.initialized && !visits.state.value.busy }
        compose.onNodeWithText("View completed destination").performClick()
    }

    private fun preview() {
        compose.onNodeWithText("Share photo").performScrollTo().performClick()
        compose.waitUntil { sharing.state.value.ready && !sharing.state.value.busy }
    }

    @Test fun doneAndNotNowDoNotPublishOrCreateAnUploadRequest() {
        show()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(0, photoReads); assertEquals(0, posts)
        compose.onNodeWithText("View completed destination").performClick()
        preview()
        assertEquals(PostVisibility.FRIENDS, sharing.state.value.visibility)
        compose.onNodeWithText("Caption (optional)").performScrollTo().performTextInput("A memory")
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        compose.onNodeWithText("Destination unlocked!").assertExists()
        assertEquals(0, posts); assertNull(savedRequest)
    }

    @Test fun explicitPublicPostHasCaptionAndCanBeViewedInExistingFeed() {
        show(); preview()
        compose.onNodeWithText("Caption (optional)").performScrollTo().performTextInput("A great visit")
        compose.onNodeWithText("Public").performScrollTo().performClick()
        screenshot("public-preview")
        assertEquals(0, posts)
        compose.onNodeWithText("Post").performScrollTo().performClick()
        compose.waitUntil { sharing.state.value.posted != null && !sharing.state.value.busy }
        compose.onNodeWithText("Photo shared").assertExists()
        screenshot("posted")
        assertEquals(PostVisibility.PUBLIC, storedPost?.visibility)
        assertEquals("A great visit", storedPost?.imageDescription)
        compose.onNodeWithText("View in feed").performScrollTo().performClick()
        assertEquals(storedPost, viewedPost)
    }

    @Test fun retryKeepsPhotoAndAudienceAndNeverDisplaysRequestDetails() {
        show(); preview(); fail = true
        compose.onNodeWithText("Post").performScrollTo().performClick()
        compose.waitUntil { sharing.state.value.error != null && !sharing.state.value.busy }
        val request = checkNotNull(savedRequest)
        compose.onNodeWithText("SYNTHETIC_SECRET", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Authorization", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Retry post").performScrollTo().assertExists()
        screenshot("retry")
        fail = false
        compose.onNodeWithText("Retry post").performClick()
        compose.waitUntil { sharing.state.value.posted != null && !sharing.state.value.busy }
        assertEquals(request.postId, storedPost?.postId)
        assertEquals(PostVisibility.FRIENDS, storedPost?.visibility)
    }

    @Test fun darkLargeTextPreviewUsesSameComposerAndCanBeSkipped() {
        show(ThemeMode.DARK, largeText = true); preview()
        compose.onNodeWithText("Friends").performScrollTo().assertExists()
        compose.onNodeWithText("Post").performScrollTo().assertExists()
        screenshot("dark-large-text-preview")
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        assertEquals(0, posts)
    }

    @Test fun swipeKeepsPreviewAndBackRequiresExplicitLeave() {
        show(); preview()
        compose.onNodeWithText("Caption (optional)").performScrollTo().performTextInput("Keep this memory")
        compose.onNodeWithText("Public").performScrollTo().performClick()
        compose.onNodeWithTag("destination_sheet").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithText("Leave sharing?").assertDoesNotExist()
        assertNotNull(visits.state.value.destination)
        assertEquals("Keep this memory", sharing.state.value.caption)
        assertEquals(PostVisibility.PUBLIC, sharing.state.value.visibility)

        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Leave sharing?").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Keep this memory").performScrollTo().assertExists()
        assertEquals(PostVisibility.PUBLIC, sharing.state.value.visibility)
        compose.onNodeWithContentDescription("Close destination").performClick()
        compose.onNodeWithText("Leave sharing").performClick()
        compose.waitUntil { visits.state.value.destination == null }
        assertEquals(0, posts)
        assertNull(savedRequest)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "share-ui-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
