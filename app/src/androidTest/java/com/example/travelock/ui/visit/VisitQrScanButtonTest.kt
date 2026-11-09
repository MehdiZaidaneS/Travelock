package com.example.travelock.ui.visit

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.R
import com.example.travelock.data.qr.QrCodeScanner
import com.example.travelock.data.qr.QrScanResult
import com.example.travelock.ui.theme.TravelockTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisitQrScanButtonTest {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scanLabel get() = context.getString(R.string.visit_qr_scan)
    private val scanningLabel get() = context.getString(R.string.visit_qr_scanning)

    @Test
    fun returnsTheExactQrTextForServerValidation() {
        val code = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        val value = "  TEST-OODI-001  "
        show(QrCodeScanner { QrScanResult.Code(value) }, onQr = code::set, onError = error::set)
        compose.onNodeWithText(scanLabel).performClick()
        compose.waitUntil { code.get() != null }
        assertEquals(value, code.get())
        assertNull(error.get())
    }

    @Test
    fun disablesTheButtonWhileScanningAndEnablesItAfterward() {
        val result = CompletableDeferred<QrScanResult>()
        val calls = AtomicInteger()
        val code = AtomicReference<String?>(null)
        show(QrCodeScanner { calls.incrementAndGet(); result.await() }, onQr = code::set)
        compose.onNodeWithText(scanLabel).performClick()
        compose.onNodeWithText(scanningLabel).assertIsNotEnabled().performClick()
        assertEquals(1, calls.get())
        compose.runOnIdle { result.complete(QrScanResult.Code("TEST-OODI-001")) }
        compose.waitUntil { code.get() != null }
        compose.onNodeWithText(scanLabel).assertIsEnabled()
    }

    @Test
    fun cancellationDoesNotReturnACodeOrAnError() {
        val code = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        val cancelled = AtomicBoolean(false)
        show(QrCodeScanner { QrScanResult.Cancelled }, code::set, error::set,
            onCancelled = { cancelled.set(true) })
        compose.onNodeWithText(scanLabel).performClick()
        compose.waitUntil { cancelled.get() }
        assertNull(code.get())
        assertNull(error.get())
    }

    @Test
    fun invalidQrResultShowsAnError() {
        val error = AtomicReference<String?>(null)
        val code = AtomicReference<String?>(null)
        show(QrCodeScanner { QrScanResult.InvalidCode }, code::set, error::set)
        compose.onNodeWithText(scanLabel).performClick()
        compose.waitUntil { error.get() != null }
        assertEquals(context.getString(R.string.visit_qr_invalid), error.get())
        assertNull(code.get())
    }

    @Test
    fun blankQrTextIsRejected() {
        val error = AtomicReference<String?>(null)
        val code = AtomicReference<String?>(null)
        show(QrCodeScanner { QrScanResult.Code("   ") }, code::set, error::set)
        compose.onNodeWithText(scanLabel).performClick()
        compose.waitUntil { error.get() != null }
        assertNull(code.get())
    }

    @Test
    fun unavailableScannerShowsAnErrorWithoutStartingAVisit() {
        val error = AtomicReference<String?>(null)
        val code = AtomicReference<String?>(null)
        show(QrCodeScanner { QrScanResult.Unavailable }, code::set, error::set)
        compose.onNodeWithText(scanLabel).performClick()
        compose.waitUntil { error.get() != null }
        assertEquals(context.getString(R.string.visit_qr_unavailable), error.get())
        assertNull(code.get())
    }

    @Test
    fun scannerExceptionDoesNotLeaveButtonStuck() {
        val error = AtomicReference<String?>(null)
        show(QrCodeScanner { throw IllegalStateException("Scanner unavailable") }, onError = error::set)
        compose.onNodeWithText(scanLabel).performClick()
        compose.waitUntil { error.get() != null }
        compose.onNodeWithText(scanLabel).assertIsEnabled()
    }

    @Test
    fun disabledButtonDoesNotStartScanning() {
        val calls = AtomicInteger()
        show(QrCodeScanner { calls.incrementAndGet(); QrScanResult.Cancelled }, enabled = false)
        compose.onNodeWithText(scanLabel).assertIsNotEnabled().performClick()
        assertEquals(0, calls.get())
    }

    @Test
    fun removingTheComponentCancelsWaitingAndDiscardsALateResult() {
        val showButton = mutableStateOf(true)
        val deferred = CompletableDeferred<QrScanResult>()
        val cancelled = AtomicBoolean(false)
        val code = AtomicReference<String?>(null)
        val scanner = QrCodeScanner {
            try { deferred.await() } finally { cancelled.set(true) }
        }
        compose.setContent {
            TravelockTheme {
                if (showButton.value) VisitQrScanButton(code::set, {}, scanner = scanner)
            }
        }
        compose.onNodeWithText(scanLabel).performClick()
        compose.onNodeWithText(scanningLabel).assertIsNotEnabled()
        compose.runOnIdle { showButton.value = false }
        compose.waitUntil { cancelled.get() }
        compose.runOnIdle { deferred.complete(QrScanResult.Code("TEST-OODI-001")) }
        compose.waitForIdle()
        assertTrue(cancelled.get())
        assertNull(code.get())
    }

    private fun show(
        scanner: QrCodeScanner,
        onQr: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        enabled: Boolean = true,
        onCancelled: () -> Unit = {},
    ) {
        compose.setContent {
            TravelockTheme {
                VisitQrScanButton(onQr, onError, enabled = enabled, onCancelled = onCancelled, scanner = scanner)
            }
        }
    }
}
