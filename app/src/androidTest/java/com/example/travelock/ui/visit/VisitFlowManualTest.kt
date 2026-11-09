package com.example.travelock.ui.visit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.location.DeviceLocationProvider
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.data.repository.DestinationRepository
import com.example.travelock.data.repository.UserUnlockRepository
import com.example.travelock.data.repository.VisitPhotoRepository
import com.example.travelock.data.visit.VisitCompletionCoordinator
import com.example.travelock.data.visit.VisitCompletionResult
import com.example.travelock.ui.theme.TravelockTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class VisitFlowManualTest {
    @get:Rule
    val compose = createComposeRule()

    // Opt-in only: this test signs into a temporary account, creates a real visit
    // and keeps the uploaded camera photo/completed attempt in the shared project.
    // Uses the real QR scanner and camera; emulator location can be supplied separately.
    @Test
    fun completesVisitWithLivePhotoAndDeviceLocation() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Set manualVisitTest=true to run this interactive test.",
            arguments.getString("manualVisitTest") == "true")
        val testRetry = arguments.getString("manualVisitRetryTest") == "true"
        val testRestore = arguments.getString("manualVisitRestoreTest") == "true"
        val testReopen = arguments.getString("manualVisitReopenTest") == "true"
        require(!testReopen || testRetry) { "Reopen test also needs manualVisitRetryTest=true." }
        require(!testRestore || testRetry) { "Restoration test also needs manualVisitRetryTest=true." }
        val email = requireNotNull(arguments.getString("supabaseTestEmail")) {
            "Add the local supabaseTestEmail instrumentation argument."
        }
        val password = requireNotNull(arguments.getString("supabaseTestPassword")) {
            "Add the local supabaseTestPassword instrumentation argument."
        }
        val destinationId = requireNotNull(arguments.getString("supabaseTestDestinationId")) {
            "Add an existing test destination UUID as supabaseTestDestinationId."
        }
        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()
        try {
            supabase.auth.signInWith(Email) { this.email = email; this.password = password }
            val user = checkNotNull(supabase.auth.currentUserOrNull())
            val destination = checkNotNull(DestinationRepository().getDestination(destinationId)) {
                "The test destination no longer exists."
            }
            val repository = UserUnlockRepository()
            val progressStore = VisitProgressStore(InstrumentationRegistry.getInstrumentation().targetContext)
            val initialProgress = progressStore.load(user.id)
            require(initialProgress.visit == null || initialProgress.visit.destinationId == destinationId) {
                "A saved visit belongs to a different destination. Use its test destination first."
            }
            val resuming = initialProgress.uploadedPhotoPath != null
            val startedVisit = AtomicReference(initialProgress.visit)
            val uploads = AtomicInteger()
            val uploadedPath = AtomicReference(initialProgress.uploadedPhotoPath)
            val expectedRejection = AtomicBoolean(false)
            var firstCompletion = !resuming
            val coordinator = VisitCompletionCoordinator(
                getLocation = DeviceLocationProvider(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                )::getCurrentLocation,
                uploadPhoto = { visit, bytes ->
                    VisitPhotoRepository().uploadPhoto(visit, bytes).also {
                        uploads.incrementAndGet()
                        uploadedPath.set(it)
                    }
                },
                finishVisit = { id, path, latitude, longitude ->
                    if (testRetry && firstCompletion) {
                        firstCompletion = false
                        val offset = Math.toDegrees(51.0 / 6_371_008.8)
                        val outsideLatitude = destination.latitude +
                            if (destination.latitude >= 0) -offset else offset
                        try {
                            repository.completeVisit(id, path, outsideLatitude, destination.longitude)
                            error("The server accepted completion outside 50 metres.")
                        } catch (error: PostgrestRestException) {
                            if (error.code == "22023" && error.message.orEmpty().contains("within 50 metres")) {
                                expectedRejection.set(true)
                            }
                            throw error
                        }
                    } else repository.completeVisit(id, path, latitude, longitude)
                },
            )
            val completed = AtomicReference<UserUnlock?>(null)
            val failure = AtomicReference<Throwable?>(null)
            val finished = AtomicBoolean(false)
            val deferred = AtomicBoolean(false)
            val readyToRestore = AtomicBoolean(false)
            val restoration = StateRestorationTester(compose)

            restoration.setContent {
                TravelockTheme {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var locationAllowed by remember {
                        mutableStateOf(ContextCompat.checkSelfPermission(context,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    }
                    var busy by remember { mutableStateOf(false) }
                    var progress by rememberVisitProgress(user.id, initialProgress)
                    val success = progress.completed
                    val visit = progress.visit
                    val retryPath = progress.uploadedPhotoPath
                    var failed by rememberSaveable { mutableStateOf(false) }
                    var status by rememberSaveable { mutableStateOf(
                        if (resuming) "Saved visit loaded. Retry completion with the uploaded photo."
                        else if (initialProgress.visit != null) "Saved visit loaded. Take a visit photo."
                        else "Scan the destination QR to start your visit."
                    ) }
                    suspend fun submit(capturedVisit: VisitAttempt, bytes: ByteArray? = null) {
                        try {
                            val result = if (bytes != null) {
                                coordinator.completeCapturedVisit(capturedVisit, bytes) {
                                    progress = progress.copy(uploadedPhotoPath = it)
                                    progressStore.save(user.id, progress)
                                }
                            } else {
                                coordinator.completeUploadedVisit(capturedVisit, checkNotNull(retryPath))
                            }
                            when (result) {
                                is VisitCompletionResult.Completed -> {
                                    completed.set(result.unlock)
                                    progress = progress.copy(completed = true)
                                    progressStore.save(user.id, progress)
                                    status = "Visit completed successfully."
                                }
                                VisitCompletionResult.LocationPermissionRequired -> {
                                    locationAllowed = false
                                    status = "Allow precise location, then try again."
                                }
                                VisitCompletionResult.LocationServicesDisabled ->
                                    status = "Turn on device location services, then try again."
                                VisitCompletionResult.LocationUnavailable ->
                                    status = "Location unavailable. Check the emulator location, then try again."
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            failure.set(error)
                            failed = true
                            status = if (testRetry && expectedRejection.get()) {
                                "Expected 51-metre rejection. Tap Retry completion to use the current device location."
                            } else "Visit failed: ${error.message.orEmpty()}"
                        } finally {
                            busy = false
                            if (testRestore && expectedRejection.get() && !progress.completed) {
                                readyToRestore.set(true)
                            }
                        }
                    }
                    val requestLocation = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { permissions ->
                        locationAllowed = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                        status = if (locationAllowed) "Location allowed. Take a visit photo."
                        else "Precise location is required for this manual test."
                    }

                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("Complete visit test", style = MaterialTheme.typography.headlineSmall)
                            Text("Destination: ${destination.name}")
                            Text("Scan the destination QR, then take a new photo. The server requires a location within 50 metres.")
                            Text("This test uploads and keeps a private photo in Supabase.")
                            if (testRetry) Text("Retry test: the first completion uses a test coordinate 51 metres away. The retry uses your current device location.")
                            VisitQrScanButton(
                                enabled = visit == null && !busy && !success,
                                onQrScanned = { scannedCode ->
                                    if (scannedCode != destination.qrCode) {
                                        status = "Different destination scanned. Scan the test destination QR."
                                    } else {
                                        busy = true
                                        failed = false
                                        failure.set(null)
                                        status = "Starting visit..."
                                        scope.launch {
                                            try {
                                                val started = repository.startVisit(scannedCode)
                                                check(started.destinationId == destinationId)
                                                startedVisit.set(started)
                                                progress = VisitProgress(visit = started)
                                                progressStore.save(user.id, progress)
                                                status = "QR accepted. Allow precise location, then take a visit photo."
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                failure.set(error)
                                                failed = true
                                                status = "Could not start visit: ${error.message.orEmpty()}"
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                },
                                onError = { status = it },
                                onCancelled = { status = "Scanner cancelled. You can try again." },
                            )
                            if (visit != null && !locationAllowed) {
                                Button(onClick = {
                                    requestLocation.launch(arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ))
                                }, enabled = !busy) { Text("Allow precise location") }
                            }
                            visit?.let { activeVisit -> VisitCameraButton(
                                visit = activeVisit,
                                enabled = locationAllowed && !busy && !success && retryPath == null,
                                onPhotoCaptured = { capturedVisit, bytes ->
                                    busy = true
                                    failed = false
                                    failure.set(null)
                                    status = "Checking location and saving visit..."
                                    scope.launch { submit(capturedVisit, bytes) }
                                },
                                onError = { status = it },
                                onCancelled = { status = "Camera cancelled. You can try again." },
                            ) }
                            if (retryPath != null && !success) {
                                Button(onClick = {
                                    val activeVisit = checkNotNull(visit)
                                    busy = true
                                    failed = false
                                    failure.set(null)
                                    status = "Retrying completion with the uploaded photo..."
                                    scope.launch { submit(activeVisit) }
                                }, enabled = locationAllowed && !busy) { Text("Retry completion") }
                            }
                            Text(status)
                            if (testReopen && !resuming && expectedRejection.get() && retryPath != null) {
                                Button(onClick = {
                                    deferred.set(true)
                                    finished.set(true)
                                }, enabled = !busy) { Text("Save and finish later") }
                            }
                            Button(onClick = { finished.set(true) }, enabled = (success || failed) && !busy) {
                                Text("Finish test")
                            }
                        }
                    }
                }
            }

            if (testRestore && !resuming) {
                compose.waitUntil(timeoutMillis = 10 * 60 * 1000L) { readyToRestore.get() }
                restoration.emulateSavedInstanceStateRestore()
                compose.waitForIdle()
            }
            compose.waitUntil(timeoutMillis = 10 * 60 * 1000L) { finished.get() }
            if (deferred.get()) {
                val saved = progressStore.load(user.id)
                assertEquals(startedVisit.get(), saved.visit)
                assertEquals(uploadedPath.get(), saved.uploadedPhotoPath)
                assertEquals(1, uploads.get())
                assertTrue(expectedRejection.get())
                return@runBlocking
            }
            failure.get()?.let { throw it }
            val unlock = checkNotNull(completed.get())
            val visit = checkNotNull(startedVisit.get())
            assertEquals(user.id, unlock.userId)
            assertEquals(destinationId, unlock.destinationId)
            assertEquals(user.id, visit.userId)
            val savedVisit = supabase.from("visit_attempts").select {
                filter { eq("visit_id", visit.visitId) }
            }.decodeSingle<VisitAttempt>()
            assertNotNull(savedVisit.completedAt)
            assertTrue(savedVisit.photoPath.orEmpty().startsWith("${user.id}/${visit.visitId}/"))
            assertEquals(uploadedPath.get(), savedVisit.photoPath)
            assertEquals("Completion retry must reuse the first uploaded photo.", if (resuming) 0 else 1, uploads.get())
            if (testRetry && !resuming) assertTrue("The initial 51-metre rejection must be exercised.", expectedRejection.get())
            assertEquals(VisitProgress(), progressStore.load(user.id))
        } finally {
            supabase.auth.signOut()
        }
    }
}
