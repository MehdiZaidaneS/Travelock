package com.example.travelock.ui.visit

import android.graphics.Bitmap
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.R
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.data.qr.QrCodeScanner
import com.example.travelock.data.qr.QrScanResult
import com.example.travelock.data.visit.VisitCompletionResult
import com.example.travelock.ui.theme.TravelockTheme
import com.example.travelock.ui.theme.ThemeMode
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// No Supabase, account, camera or location requests. Exercises production screens
// with controlled backend/scanner results on the connected device.
class VisitFlowHostTest {
    @get:Rule val compose = createComposeRule()
    private val destination = Destination("destination", "city", "Test Library", "", 0f, 60.0, 24.0, "", "QR")
    private val visit = VisitAttempt("visit", "user", "destination", Instant.EPOCH)
    private val unlock = UserUnlock("unlock", "user", "destination", Instant.EPOCH)
    private val path = "user/visit/photo.jpg"
    private var saved = VisitProgress()
    private var enrollments = 0
    private var starts = 0
    private var captures = 0
    private var retryFailure: Exception? = null
    private var removals = 0
    private var cancellations = 0
    private val controller = VisitFlowController(
        "user", load = { saved }, save = { saved = it }, getDestination = { destination },
        getEnrolledIds = { if (saved.visit != null) setOf(destination.destinationId) else emptySet() }, enrol = { enrollments++ },
        start = { starts++; visit },
        capture = { _, _, uploaded -> captures++; uploaded(path); VisitCompletionResult.Completed(unlock) },
        retry = { _, _ -> retryFailure?.let { throw it }; VisitCompletionResult.Completed(unlock) }, cancel = { cancellations++; saved = VisitProgress() },
        unenrol = { removals++ },
    )

    private fun show(theme: ThemeMode = ThemeMode.LIGHT, largeText: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides
                if (largeText) Density(density.density * 1.15f, 1.5f) else density) {
            TravelockTheme(themeMode = theme) {
                VisitFlowHost(controller, QrCodeScanner { QrScanResult.Code("QR") }) { actions ->
                    Text("Revision ${actions.revision}")
                    Button(onClick = { actions.onOpenDestination(destination, false) }) { Text("View destination") }
                }
            }
            }
        }
        compose.waitUntil { controller.state.value.initialized && !controller.state.value.busy }
    }

    @Test fun unenrollNeedsConfirmationAndReturnsToTheEnrollAction() {
        show()
        compose.onNodeWithText("View destination").performClick()
        compose.onNodeWithText("Enroll").performScrollTo().performClick()
        compose.waitUntil { enrollments == 1 && !controller.state.value.busy }
        compose.onNodeWithText("Unenroll").performScrollTo().performClick()
        compose.onNodeWithText("Keep visit").performClick()
        assertEquals(0, removals)
        compose.onNodeWithText("Unenroll").performScrollTo().performClick()
        compose.onNodeWithText("Confirm unenroll").performClick()
        compose.waitUntil { removals == 1 && !controller.state.value.busy }
        compose.onNodeWithText("Enroll").performScrollTo().assertExists()
        assertEquals(0, cancellations)
        screenshot("unenrolled-sheet")
    }

    @Test fun unenrollingPendingVerificationCancelsAndRemovesItsPlan() {
        saved = VisitProgress(visit, path)
        show()
        compose.onNodeWithText("Unenroll").performScrollTo().performClick()
        compose.onNodeWithText("Confirm unenroll").performClick()
        compose.waitUntil { removals == 1 && !controller.state.value.busy }
        assertEquals(1, cancellations)
        assertEquals(null, saved.visit)
        compose.onNodeWithText("Enroll").performScrollTo().assertExists()
    }

    @Test fun darkSheetKeepsDestinationContextAndSupportsLargeText() {
        show(ThemeMode.DARK, largeText = true)
        compose.onNodeWithText("View destination").performClick()
        compose.onNodeWithText("Enroll").performScrollTo().performClick()
        compose.waitUntil { enrollments == 1 && !controller.state.value.busy }
        compose.onNodeWithText("Verify visit").performScrollTo().performClick()
        compose.onNodeWithText(destination.name).assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.visit_qr_scan)).performScrollTo().assertExists()
        screenshot("verify-sheet-dark-large-text")
    }
    @Test fun enrollThenScanThenPhotoAndSuccessfulUnlock() {
        show()
        compose.onNodeWithText("View destination").performClick()
        compose.onNodeWithText("Enroll").performScrollTo().performClick()
        compose.waitUntil { enrollments == 1 && !controller.state.value.busy }
        compose.onNodeWithText("Enrolled").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.visit_qr_scan)).assertDoesNotExist()
        assertEquals(0, starts)
        screenshot("enrolled")
        compose.onNodeWithText("Verify visit").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.visit_qr_scan)).performScrollTo().performClick()
        compose.waitUntil { starts == 1 && !controller.state.value.busy }
        compose.onNodeWithText(context.getString(R.string.visit_photo_take)).assertExists()
        screenshot("photo-step")
        compose.onNodeWithText("Continue later").performScrollTo().performClick()
        compose.onNodeWithText("Resume visit").performClick()
        compose.waitUntil { controller.state.value.destination != null && !controller.state.value.busy }
        compose.runOnIdle { runBlocking { controller.complete(visit, byteArrayOf(1)) } }
        compose.onNodeWithText("Destination unlocked!").assertExists()
        screenshot("completed")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Revision 2").assertExists()
        assertEquals(1, captures)
    }

    @Test fun reopeningUploadedVisitOffersVerificationWithoutAnotherCapture() {
        saved = VisitProgress(visit, path)
        show()
        compose.onNodeWithText("Verify and unlock").performScrollTo().performClick()
        compose.waitUntil { controller.state.value.progress.completed }
        compose.onNodeWithText("Destination unlocked!").assertExists()
        assertEquals(0, captures)
        assertEquals(0, starts)
    }

    @Test fun distanceRejectionHidesRequestDetailsAndLeavesPhotoRetryAccessible() {
        saved = VisitProgress(visit, path)
        retryFailure = IllegalStateException("You must be within 50 metres of the destination.\nHeaders: Authorization=Bearer SYNTHETIC_SECRET\nURL: https://example.invalid")
        show()
        compose.onNodeWithText("Verify and unlock").performScrollTo().performClick()
        compose.waitUntil { controller.state.value.error != null && !controller.state.value.busy }
        compose.onNodeWithText("You must be within 50 metres of the destination. Move closer and retry verification.").assertExists()
        compose.onNodeWithText("SYNTHETIC_SECRET", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Authorization", substring = true).assertDoesNotExist()
        compose.onNodeWithText("https://", substring = true).assertDoesNotExist()
        screenshot("distance-rejection-safe")
        retryFailure = null
        compose.onNodeWithText("Verify and unlock").performScrollTo().performClick()
        compose.waitUntil { controller.state.value.progress.completed }
        assertEquals(0, captures)
        assertEquals(path, saved.uploadedPhotoPath)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Platform dialog dismissal and ripples finish outside Compose's clock.
        android.os.SystemClock.sleep(400)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "visit-ui-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
