package com.example.travelock.data.qr

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface QrScanResult {
    data class Code(val text: String) : QrScanResult
    data object Cancelled : QrScanResult
    data object InvalidCode : QrScanResult
    data object Unavailable : QrScanResult
}

fun interface QrCodeScanner {
    suspend fun scan(): QrScanResult
}

class GoogleQrCodeScanner(context: Context) : QrCodeScanner {
    private val client = GmsBarcodeScanning.getClient(
        context,
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build(),
    )

    override suspend fun scan(): QrScanResult = suspendCancellableCoroutine { continuation ->
        if (!continuation.isActive) return@suspendCancellableCoroutine
        // The external scanner cannot be cancelled through this SDK. Ignore its
        // result if the calling screen was removed or its coroutine was cancelled.
        try {
            client.startScan()
                .addOnSuccessListener { barcode ->
                    val text = barcode.rawValue
                    val result = if (barcode.format == Barcode.FORMAT_QR_CODE && !text.isNullOrBlank()) {
                        // Keep the exact value for server-side QR validation.
                        QrScanResult.Code(text)
                    } else {
                        QrScanResult.InvalidCode
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnCanceledListener {
                    if (continuation.isActive) continuation.resume(QrScanResult.Cancelled)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(QrScanResult.Unavailable)
                }
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(QrScanResult.Unavailable)
        }
    }
}
