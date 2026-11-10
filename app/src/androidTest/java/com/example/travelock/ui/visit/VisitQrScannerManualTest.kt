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
import com.example.travelock.ui.theme.TravelockTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class VisitQrScannerManualTest {
    @get:Rule
    val compose = createComposeRule()

    // Opt in with manualQrTest=true. Uses the real Google scanner, no fake results.
    // This screen does not sign in, start a visit, upload a photo or award an unlock.
    @Test
    fun scansTheOodiQrCodeWithTheCamera() {
        assumeTrue(
            "Set manualQrTest=true to open this interactive scanner test.",
            InstrumentationRegistry.getArguments().getString("manualQrTest") == "true",
        )
        val expected = "TEST-OODI-001"
        val scanned = AtomicReference<String?>(null)
        val finished = AtomicBoolean(false)

        compose.setContent {
            TravelockTheme {
                var status by remember { mutableStateOf("Ready to scan the test QR image.") }
                var matched by remember { mutableStateOf(false) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("QR scanner test", style = MaterialTheme.typography.headlineSmall)
                        Text("Scan the QR image containing $expected. Nothing is uploaded or unlocked.")
                        VisitQrScanButton(
                            onQrScanned = { text ->
                                scanned.set(text)
                                matched = text == expected
                                status = if (matched) "Correct QR received: $text"
                                else "Different code received: $text. Please scan the Oodi test image."
                            },
                            onError = { status = it },
                            onCancelled = { status = "Scanner cancelled. You can try again." },
                        )
                        Text(status)
                        Button(onClick = { finished.set(true) }, enabled = matched) {
                            Text("Finish test")
                        }
                    }
                }
            }
        }

        compose.waitUntil(timeoutMillis = 10 * 60 * 1000L) { finished.get() }
        assertEquals(expected, scanned.get())
    }
}
