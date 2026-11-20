package com.example.travelock.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.*
import com.example.travelock.ui.screens.main.VisitListTab
import com.example.travelock.ui.screens.main.yourVisitsSection
import com.example.travelock.ui.screens.profile.ProfileScreen
import com.example.travelock.ui.screens.profile.ProfileUiState
import com.example.travelock.ui.share.SharePostController
import com.example.travelock.ui.theme.ThemeMode
import com.example.travelock.ui.theme.TravelockTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real list/profile, destination sheet and composer; no Supabase writes or camera. */
class YourVisitsFlowTest {
    @get:Rule val compose = createComposeRule()
    private val planned = Destination("planned", "city", "Planned library", "A future visit", 0f, 60.0, 24.0, "", "QR")
    private val completed = planned.copy(destinationId = "completed", name = "Completed library")
    private val visit = VisitAttempt("visit", "user", "completed", Instant.EPOCH,
        completedAt = Instant.EPOCH, photoPath = "user/visit/photo.jpg")
    private var photoReads = 0
    private var posts = 0
    private var retries = 0
    private val sharing = SharePostController("user", "completed", { visit }, { null },
        { photoReads++; byteArrayOf(1) }, { null }, {}, {}, { _, _ -> posts++; error("No publish expected") })
    private val controller = VisitFlowController("user", { VisitProgress() }, {}, { completed },
        { setOf("planned") }, {}, { error("No scan expected") },
        { _, _, _ -> error("No capture expected") }, { _, _ -> error("No verification expected") }, {}, {})

    private fun show(profile: Boolean = false, largeText: Boolean = false, empty: Boolean = false,
        error: String? = null) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (largeText) Density(density.density * 1.15f, 1.5f) else density) {
                TravelockTheme(themeMode = if (largeText) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface {
                    VisitFlowHost(controller, shareFactory = { sharing }) { actions ->
                        if (profile) ProfileScreen(
                            uiState = ProfileUiState(isLoading = false, userId = "user", name = "Test traveler",
                                unlocks = listOf(UserUnlock("unlock", "user", "completed", Instant.EPOCH, completed))),
                            currentThemeMode = ThemeMode.LIGHT, onThemeModeChange = {}, onEditProfileClick = {},
                            onFriendClick = {}, onLogoutClick = {},
                            onOpenDestination = { actions.onOpenDestination(it, true) },
                        ) else {
                            var selected by rememberSaveable { mutableStateOf(VisitListTab.PLANNED) }
                            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                yourVisitsSection(selected, { selected = it },
                                    if (empty) emptyList() else listOf(planned),
                                    if (empty) emptyList() else listOf(completed), false, error,
                                    { retries++ }, actions.onOpenDestination)
                                item { Text("Explore more") }
                            }
                        }
                    }
                    }
                }
            }
        }
        compose.waitUntil { controller.state.value.initialized && !controller.state.value.busy }
    }

    @Test fun completedTabReopensSavedVisitAndOffersOptionalSharing() {
        show()
        compose.onNodeWithText("Planned").assertIsSelected()
        compose.onNodeWithText("Planned library").assertExists()
        compose.onNodeWithText("Completed library").assertDoesNotExist()
        compose.onNodeWithText("Completed").performClick().assertIsSelected()
        compose.onNodeWithText("Planned library").assertDoesNotExist()
        screenshot("completed-tab")
        compose.onNodeWithText("Completed library").performClick()
        compose.onNodeWithText("Destination unlocked!").assertExists()
        assertEquals(0, photoReads)
        compose.onNodeWithText("Share photo").performScrollTo().performClick()
        compose.waitUntil { sharing.state.value.ready }
        compose.onNodeWithText("Caption (optional)").performScrollTo().assertExists()
        assertEquals(1, photoReads); assertEquals(0, posts)
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Completed").assertIsSelected()
        compose.onNodeWithText("Planned").performClick()
        compose.onNodeWithText("Planned library").performClick()
        compose.onNodeWithText("Verify visit").performScrollTo().assertExists()
        compose.onNodeWithText("Destination unlocked!").assertDoesNotExist()
    }

    @Test fun profileUnlockOpensTheSameCompletedVisitSheet() {
        show(profile = true)
        compose.onNodeWithText("Completed library").performScrollTo()
        screenshot("profile-unlocked-link")
        compose.onNodeWithText("Completed library").performClick()
        compose.onNodeWithText("Destination unlocked!").assertExists()
        compose.onNodeWithText("Share photo").performScrollTo().performClick()
        compose.waitUntil { sharing.state.value.ready }
        assertEquals(1, photoReads); assertEquals(0, posts)
    }

    @Test fun emptyTabsRemainUsableWithLargeTextAndDarkTheme() {
        show(largeText = true, empty = true)
        compose.onNodeWithText("Enroll in a destination to plan your next visit.").assertExists()
        compose.onNodeWithText("Completed").performClick().assertIsSelected()
        compose.onNodeWithText("Your completed destinations will appear here after you verify a visit.").assertExists()
        screenshot("completed-empty-dark-large-text")
        compose.onNodeWithText("Planned").performClick().assertIsSelected()
    }

    @Test fun failedLoadingOffersRetryInsteadOfClaimingNoCompletedVisits() {
        show(error = "Could not load your completed destinations.")
        compose.onNodeWithText("Completed").performClick()
        compose.onNodeWithText("Your completed destinations will appear here after you verify a visit.").assertDoesNotExist()
        compose.onNodeWithText("Retry visits").performClick()
        assertEquals(1, retries)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "visit-history-review").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
