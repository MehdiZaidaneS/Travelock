package com.example.travelock.ui.visit

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.IntentCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.ui.theme.TravelockTheme
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisitCameraButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val visit = VisitAttempt(
        UUID.randomUUID().toString(), "test-user", "test-destination", Instant.now(),
    )

    @Test
    fun cameraOutputReturnsJpegForTheSameVisitAndDeletesTemporaryFile() {
        val captured = AtomicReference<Pair<VisitAttempt, ByteArray>?>(null)
        val error = AtomicReference<String?>(null)
        val camera = CameraRegistry()
        show(camera, onPhoto = { v, bytes -> captured.set(v to bytes) }, onError = error::set)

        compose.onNodeWithText("Take visit photo").performClick()
        compose.waitUntil { captured.get() != null || error.get() != null }

        assertNull(error.get())
        assertEquals(visit, captured.get()!!.first)
        assertTrue(captured.get()!!.second.isNotEmpty())
        assertEquals(1, camera.launches)
        assertTemporaryFileDeleted(camera)
    }

    @Test
    fun cancelledCameraDoesNotReturnOrUploadAnImage() {
        val captured = AtomicInteger()
        val cancelled = AtomicInteger()
        val error = AtomicReference<String?>(null)
        val camera = CameraRegistry(resultCode = Activity.RESULT_CANCELED)
        show(camera, onPhoto = { _, _ -> captured.incrementAndGet() }, onError = error::set,
            onCancelled = { cancelled.incrementAndGet() })

        compose.onNodeWithText("Take visit photo").performClick()
        compose.waitUntil { cancelled.get() == 1 }
        assertEquals(0, captured.get())
        assertNull(error.get())
        assertTemporaryFileDeleted(camera)
    }

    @Test
    fun invalidCameraOutputIsRejected() {
        val captured = AtomicInteger()
        val error = AtomicReference<String?>(null)
        val camera = CameraRegistry(validJpeg = false)
        show(camera, onPhoto = { _, _ -> captured.incrementAndGet() }, onError = error::set)

        compose.onNodeWithText("Take visit photo").performClick()
        compose.waitUntil { error.get() != null }
        assertEquals(0, captured.get())
        assertTemporaryFileDeleted(camera)
    }

    @Test
    fun cancelledVisitCannotLaunchCamera() {
        val camera = CameraRegistry()
        show(camera, currentVisit = visit.copy(cancelledAt = Instant.now()))
        compose.onNodeWithText("Take visit photo").assertIsNotEnabled()
        assertEquals(0, camera.launches)
    }

    @Test
    fun completedVisitCannotLaunchCamera() {
        val camera = CameraRegistry()
        show(camera, currentVisit = visit.copy(completedAt = Instant.now(), photoPath = "saved.jpg"))
        compose.onNodeWithText("Take visit photo").assertIsNotEnabled()
        assertEquals(0, camera.launches)
    }

    @Test
    fun resultCannotBeAttachedToADifferentVisit() {
        val current = mutableStateOf(visit)
        val captured = AtomicInteger()
        val error = AtomicReference<String?>(null)
        val camera = CameraRegistry(autoRespond = false)
        val owner = registryOwner(camera)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TravelockTheme {
                    VisitCameraButton(current.value, { _, _ -> captured.incrementAndGet() }, error::set)
                }
            }
        }
        compose.onNodeWithText("Take visit photo").performClick()
        compose.runOnIdle { current.value = visit.copy(visitId = UUID.randomUUID().toString()) }
        compose.waitForIdle()
        compose.runOnIdle { camera.respond() }
        compose.waitUntil { error.get() != null }
        assertEquals(0, captured.get())
        assertTemporaryFileDeleted(camera)
    }

    @Test
    fun pendingCameraResultSurvivesSavedStateRestoration() {
        val captured = AtomicReference<Pair<VisitAttempt, ByteArray>?>(null)
        val error = AtomicReference<String?>(null)
        val camera = CameraRegistry(autoRespond = false)
        val owner = registryOwner(camera)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TravelockTheme { VisitCameraButton(visit, { v, bytes -> captured.set(v to bytes) }, error::set) }
            }
        }
        compose.onNodeWithText("Take visit photo").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { camera.respond() }
        compose.waitUntil { captured.get() != null || error.get() != null }
        assertNull(error.get())
        assertEquals(visit.visitId, captured.get()!!.first.visitId)
        assertTemporaryFileDeleted(camera)
    }

    private fun show(
        camera: CameraRegistry,
        currentVisit: VisitAttempt = visit,
        onPhoto: (VisitAttempt, ByteArray) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {},
        onCancelled: () -> Unit = {},
    ) {
        val owner = registryOwner(camera)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TravelockTheme { VisitCameraButton(currentVisit, onPhoto, onError, onCancelled = onCancelled) }
            }
        }
    }

    private fun registryOwner(registry: ActivityResultRegistry) = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = registry
    }

    private fun assertTemporaryFileDeleted(camera: CameraRegistry) {
        val filename = camera.outputUri!!.lastPathSegment!!
        assertTrue(!File(context.cacheDir, "visit-photos/$filename").exists())
    }

    // Replaces the external camera only for tests. It verifies the capture intent
    // and writes test bytes through the same FileProvider URI a camera would use.
    private inner class CameraRegistry(
        private val resultCode: Int = Activity.RESULT_OK,
        private val validJpeg: Boolean = true,
        private val autoRespond: Boolean = true,
    ) : ActivityResultRegistry() {
        var launches = 0
        var outputUri: Uri? = null
        private var pendingRequest = 0

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            val intent = contract.createIntent(context, input)
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
            assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            outputUri = IntentCompat.getParcelableExtra(intent, MediaStore.EXTRA_OUTPUT, Uri::class.java)
            assertEquals(outputUri, intent.clipData!!.getItemAt(0).uri)
            pendingRequest = requestCode
            launches++
            if (autoRespond) respond()
        }

        fun respond() {
            if (resultCode == Activity.RESULT_OK) {
                context.contentResolver.openOutputStream(outputUri!!)!!.use { output ->
                    if (validJpeg) {
                        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                        try {
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                        } finally {
                            bitmap.recycle()
                        }
                    } else {
                        output.write("not an image".toByteArray())
                    }
                }
            }
            dispatchResult(pendingRequest, resultCode, null)
        }
    }
}
