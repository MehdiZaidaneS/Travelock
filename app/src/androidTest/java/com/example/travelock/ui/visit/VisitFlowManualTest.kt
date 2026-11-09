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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.data.repository.DestinationRepository
import com.example.travelock.data.repository.UserUnlockRepository
import com.example.travelock.data.visit.VisitCompletionCoordinator
import com.example.travelock.data.visit.VisitCompletionResult
import com.example.travelock.ui.theme.TravelockTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import java.util.concurrent.atomic.AtomicBoolean
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
    // QR scanner hardware is not exercised: the fixture supplies the scanned value.
    @Test
    fun completesVisitWithLivePhotoAndDeviceLocation() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Set manualVisitTest=true to run this interactive test.",
            arguments.getString("manualVisitTest") == "true")
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
            val visit = UserUnlockRepository().startVisit(destination.qrCode)
            val coordinator = VisitCompletionCoordinator(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
            val completed = AtomicReference<UserUnlock?>(null)
            val failure = AtomicReference<Throwable?>(null)
            val finished = AtomicBoolean(false)

            compose.setContent {
                TravelockTheme {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var locationAllowed by remember {
                        mutableStateOf(ContextCompat.checkSelfPermission(context,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    }
                    var busy by remember { mutableStateOf(false) }
                    var success by remember { mutableStateOf(false) }
                    var failed by remember { mutableStateOf(false) }
                    var status by remember { mutableStateOf("Allow precise location, then take a visit photo.") }
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
                            Text("A visit has started using its test QR value. Take a new photo. The server requires a location within 50 metres.")
                            Text("This test uploads and keeps a private photo in Supabase.")
                            if (!locationAllowed) {
                                Button(onClick = {
                                    requestLocation.launch(arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ))
                                }, enabled = !busy) { Text("Allow precise location") }
                            }
                            VisitCameraButton(
                                visit = visit,
                                enabled = locationAllowed && !busy && !success,
                                onPhotoCaptured = { capturedVisit, bytes ->
                                    busy = true
                                    failed = false
                                    failure.set(null)
                                    status = "Checking location and saving visit..."
                                    scope.launch {
                                        try {
                                            when (val result = coordinator.completeCapturedVisit(capturedVisit, bytes)) {
                                                is VisitCompletionResult.Completed -> {
                                                    completed.set(result.unlock)
                                                    success = true
                                                    status = "Visit completed successfully."
                                                }
                                                VisitCompletionResult.LocationPermissionRequired -> {
                                                    locationAllowed = false
                                                    status = "Allow precise location, then take a new photo."
                                                }
                                                VisitCompletionResult.LocationServicesDisabled ->
                                                    status = "Turn on device location services, then take a new photo."
                                                VisitCompletionResult.LocationUnavailable ->
                                                    status = "Location unavailable. Check the emulator location, then take a new photo."
                                            }
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            failure.set(error)
                                            failed = true
                                            status = "Visit failed: ${error.message.orEmpty()}"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                onError = { status = it },
                                onCancelled = { status = "Camera cancelled. You can try again." },
                            )
                            Text(status)
                            Button(onClick = { finished.set(true) }, enabled = (success || failed) && !busy) {
                                Text("Finish test")
                            }
                        }
                    }
                }
            }

            compose.waitUntil(timeoutMillis = 10 * 60 * 1000L) { finished.get() }
            failure.get()?.let { throw it }
            val unlock = checkNotNull(completed.get())
            assertEquals(user.id, unlock.userId)
            assertEquals(destinationId, unlock.destinationId)
            val savedVisit = supabase.from("visit_attempts").select {
                filter { eq("visit_id", visit.visitId) }
            }.decodeSingle<VisitAttempt>()
            assertNotNull(savedVisit.completedAt)
            assertTrue(savedVisit.photoPath.orEmpty().startsWith("${user.id}/${visit.visitId}/"))
        } finally {
            supabase.auth.signOut()
        }
    }
}
