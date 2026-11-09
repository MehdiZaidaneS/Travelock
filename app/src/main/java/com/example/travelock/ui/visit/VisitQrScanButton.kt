package com.example.travelock.ui.visit

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.travelock.R
import com.example.travelock.data.qr.GoogleQrCodeScanner
import com.example.travelock.data.qr.QrCodeScanner
import com.example.travelock.data.qr.QrScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun VisitQrScanButton(
    onQrScanned: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCancelled: () -> Unit = {},
    scanner: QrCodeScanner = rememberQrCodeScanner(),
) {
    val scope = rememberCoroutineScope()
    val currentOnQr by rememberUpdatedState(onQrScanned)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    val invalidMessage by rememberUpdatedState(stringResource(R.string.visit_qr_invalid))
    val unavailableMessage by rememberUpdatedState(stringResource(R.string.visit_qr_unavailable))
    var scanning by remember { mutableStateOf(false) }

    Button(
        onClick = {
            scanning = true
            scope.launch {
                val result = try {
                    scanner.scan()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    QrScanResult.Unavailable
                } finally {
                    scanning = false
                }
                when (result) {
                    is QrScanResult.Code -> {
                        if (result.text.isBlank()) currentOnError(invalidMessage)
                        else currentOnQr(result.text)
                    }
                    QrScanResult.Cancelled -> currentOnCancelled()
                    QrScanResult.InvalidCode -> currentOnError(invalidMessage)
                    QrScanResult.Unavailable -> currentOnError(unavailableMessage)
                }
            }
        },
        modifier = modifier,
        enabled = enabled && !scanning,
    ) {
        Text(stringResource(if (scanning) R.string.visit_qr_scanning else R.string.visit_qr_scan))
    }
}

@Composable
private fun rememberQrCodeScanner(): QrCodeScanner {
    val context = LocalContext.current
    return remember(context) { GoogleQrCodeScanner(context) }
}
