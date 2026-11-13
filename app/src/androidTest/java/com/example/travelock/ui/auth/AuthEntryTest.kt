package com.example.travelock.ui.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.AuthUser
import com.example.travelock.data.model.Profile
import com.example.travelock.data.model.ProfileOnboardingState
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class AuthEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun registrationOnlyAsksForCredentialsThenShowsConfirmation() {
        compose.setContent { MaterialTheme {
            RegisterScreen(onRegisterClick = { _, _, _ -> })
        } }
        compose.onNodeWithText("Email", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Password", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Confirm Password", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Username").assertDoesNotExist()
        compose.onNodeWithText("Full Name").assertDoesNotExist()
        saveScreenshot("register")
    }

    @Test fun confirmationExplainsHowToContinue() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val flow = remember { AuthFlowController(scope, FakeGateway()) }
            MaterialTheme { AuthEntry(flow, AuthFlowState(page = AuthPage.CONFIRM_EMAIL, pendingEmail = "test@example.com")) }
        }
        compose.onNodeWithText("Check your email").assertExists()
        compose.onNodeWithText("Continue to sign in").assertExists()
        compose.onNodeWithText("Username").assertDoesNotExist()
        saveScreenshot("confirm-email")
    }

    @Test fun passwordFeedbackIdentifiesMissingRulesAndAcceptsStrongPassword() {
        compose.setContent { MaterialTheme { RegisterScreen() } }
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("password")
        compose.onNodeWithText("• An uppercase letter (A–Z)").assertExists()
        compose.onNodeWithText("• A digit (0–9)").assertExists()
        compose.onNodeWithText("• A symbol, such as !, @ or #").assertExists()
        saveScreenshot("password-validation")
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextReplacement("Abcdef1!")
        compose.onNodeWithText("Password requirements met.").assertExists()
    }

    @Test fun selectedPhotoIsConvertedToJpegBeforeProfileCreation() {
        val gateway = FakeGateway()
        showSetupWithPickerResult(gateway, createPickerImage())
        compose.onNodeWithText("Upload photo").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Selected profile photo").assertExists()
        compose.waitUntil(10_000) {
            val preview = compose.onNodeWithContentDescription("Selected profile photo").captureToImage().asAndroidBitmap()
            preview.getPixel(preview.width / 2, preview.height / 2) == android.graphics.Color.BLUE
        }
        saveScreenshot("selected-photo")
        compose.onNodeWithText("Username").performScrollTo().performTextInput("traveler")
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Traveler")
        compose.onNodeWithText("Save profile and continue").performScrollTo().performClick()
        compose.waitUntil(10_000) { gateway.receivedPhoto != null }
        compose.onNodeWithText("App content").assertExists()
        val bytes = checkNotNull(gateway.receivedPhoto)
        assertEquals(0xff, bytes[0].toInt() and 0xff)
        assertEquals(0xd8, bytes[1].toInt() and 0xff)
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
        assertTrue(maxOf(dimensions.outWidth, dimensions.outHeight) <= 1024)
    }

    @Test fun selectedPhotoCanBeRemovedAndSetupCompletedWithoutIt() {
        val gateway = FakeGateway()
        showSetupWithPickerResult(gateway, createPickerImage())
        compose.onNodeWithText("Upload photo").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Selected profile photo").assertExists()
        compose.onNodeWithText("Remove photo").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Selected profile photo").assertDoesNotExist()
        compose.onNodeWithText("Username").performScrollTo().performTextInput("traveler")
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Traveler")
        compose.onNodeWithText("Save profile and continue").performScrollTo().performClick()
        compose.onNodeWithText("App content").assertExists()
        assertNull(gateway.receivedPhoto)
    }

    @Test fun presetSelectionPersistsAndSavesWithoutPhotoBytes() {
        val gateway = FakeGateway()
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            val scope = rememberCoroutineScope()
            val flow = remember { AuthFlowController(scope, gateway).apply { restore() } }
            val state by flow.state.collectAsState()
            MaterialTheme {
                if (state.page == AuthPage.READY) Text("App content") else AuthEntry(flow, state)
            }
        }
        compose.onNodeWithContentDescription("Moods avatar 3").performScrollTo().performClick().assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Moods avatar 3").assertIsSelected()
        compose.onNodeWithText("Create your profile").performScrollTo()
        saveScreenshot("moods-picker")
        compose.onNodeWithText("Username").performScrollTo().performTextInput("traveler")
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Traveler")
        compose.onNodeWithText("Save profile and continue").performScrollTo().performClick()
        compose.onNodeWithText("App content").assertExists()
        assertEquals("preset:moods_03", gateway.receivedPreset)
        assertNull(gateway.receivedPhoto)
    }

    @Test fun uploadedPhotoReplacesPresetAndPresetCanReplacePhoto() {
        val gateway = FakeGateway()
        showSetupWithPickerResult(gateway, createPickerImage())
        compose.onNodeWithContentDescription("Moods avatar 1").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Upload photo").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Selected profile photo").assertExists()
        compose.onNodeWithContentDescription("Moods avatar 1").assertIsNotSelected()
        compose.onNodeWithContentDescription("Moods avatar 2").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Selected profile photo").assertDoesNotExist()
        compose.onNodeWithText("Remove photo").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Moods avatar 2").assertIsNotSelected()
        compose.onNodeWithText("Skip photo").assertExists()
    }

    private fun createPickerImage(): Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "visit-photos").apply { mkdirs() }
        val file = File(directory, "auth-picker-test.png")
        val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        return FileProvider.getUriForFile(context, "${context.packageName}.visitphotos", file)
    }

    private fun showSetupWithPickerResult(gateway: FakeGateway, uri: Uri) {
        val registry = object : ActivityResultRegistry() {
            @Suppress("UNCHECKED_CAST")
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, uri as O)
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                val scope = rememberCoroutineScope()
                val flow = remember { AuthFlowController(scope, gateway).apply { restore() } }
                val state by flow.state.collectAsState()
                MaterialTheme {
                    if (state.page == AuthPage.READY) Text("App content") else AuthEntry(flow, state)
                }
            }
        }
    }

    @Test fun firstProfileMustBeSavedBeforeEnteringApp() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val flow = remember { AuthFlowController(scope, FakeGateway()).apply { restore() } }
            val state by flow.state.collectAsState()
            MaterialTheme {
                if (state.page == AuthPage.READY) Text("App content") else AuthEntry(flow, state)
            }
        }
        compose.onNodeWithText("Create your profile").assertExists()
        compose.onNodeWithText("App content").assertDoesNotExist()
        saveScreenshot("create-profile")
        compose.onNodeWithText("Save profile and continue").performScrollTo().performClick()
        compose.onNodeWithText("Enter a username and full name.").assertExists()
        compose.onNodeWithText("Username").performScrollTo().performTextInput("newtraveler")
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("New Traveler")
        compose.onNodeWithText("Save profile and continue").performScrollTo().performClick()
        compose.onNodeWithText("App content").assertExists()
    }

    private fun saveScreenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(directory, "auth-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class FakeGateway : AuthFlowGateway {
        @Volatile var receivedPhoto: ByteArray? = null
        var receivedPreset: String? = null
        private val user = AuthUser("ui-test-account", "test@example.com")
        private var current: ProfileOnboardingState = ProfileOnboardingState.NeedsProfile(user)
        override suspend fun state() = current
        override suspend fun register(email: String, password: String) { }
        override suspend fun login(email: String, password: String) { }
        override suspend fun completeProfile(username: String, name: String, jpegBytes: ByteArray?, presetAvatar: String?) {
            receivedPhoto = jpegBytes
            receivedPreset = presetAvatar
            current = ProfileOnboardingState.Ready(user, Profile(user.userId, username, name, createdAt = Instant.EPOCH))
        }
        override suspend fun logout() { current = ProfileOnboardingState.SignedOut }
        override suspend fun sendReset(email: String) { }
    }
}
