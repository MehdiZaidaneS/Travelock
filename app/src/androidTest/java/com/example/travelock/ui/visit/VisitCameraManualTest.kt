package com.example.travelock.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.VisitAttempt
import com.example.travelock.ui.theme.TravelockTheme
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class VisitCameraManualTest {
    @get:Rule
    val compose = createComposeRule()

    // Opt in with manualCameraTest=true. Normal automated runs skip this test.
    // No fake camera registry: the user must take and confirm a real camera photo.
    @Test
    fun capturesPhotoUsingRealCamera() {
        assumeTrue(
            "Set the manualCameraTest instrumentation argument to true to open this screen.",
            InstrumentationRegistry.getArguments().getString("manualCameraTest") == "true",
        )
        val visit = VisitAttempt(
            UUID.randomUUID().toString(), "manual-test-user", "manual-test-destination", Instant.now(),
        )
        val receivedBytes = AtomicInteger()
        val receivedVisitId = AtomicReference<String?>(null)
        val finished = AtomicBoolean(false)

        compose.setContent {
            TravelockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var status by remember { mutableStateOf("Ready to take a photo.") }
                    var photoReceived by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Visit camera test", style = MaterialTheme.typography.headlineSmall)
                        Text("Take a new photo with the camera and confirm it. No photo is uploaded and no destination is unlocked.")
                        VisitCameraButton(
                            visit = visit,
                            onPhotoCaptured = { capturedVisit, bytes ->
                                receivedVisitId.set(capturedVisit.visitId)
                                receivedBytes.set(bytes.size)
                                photoReceived = true
                                status = "Photo received successfully (${bytes.size} bytes)."
                            },
                            onError = { status = it },
                            onCancelled = { status = "Camera cancelled. You can try again." },
                        )
                        Text(status)
                        Button(onClick = { finished.set(true) }, enabled = photoReceived) {
                            Text("Finish test")
                        }
                    }
                }
            }
        }

        // The screen stays open while the user captures a photo and reads the result.
        compose.waitUntil(timeoutMillis = 10 * 60 * 1000L) { finished.get() }
        assertTrue("The camera must return a nonempty JPEG.", receivedBytes.get() > 0)
        assertEquals(visit.visitId, receivedVisitId.get())
    }
}
