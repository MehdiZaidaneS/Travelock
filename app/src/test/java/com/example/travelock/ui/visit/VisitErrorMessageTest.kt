package com.example.travelock.ui.visit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VisitErrorMessageTest {
    // Synthetic sentinel only. Never paste real request headers or credentials
    // into fixtures, screenshots, logs or source.
    private val requestDetails = "\nCode: 22023\nURL: https://example.invalid/rpc\nHeaders: [Authorization=Bearer SYNTHETIC_SECRET]"

    @Test fun knownDistanceRejectionDisplaysOnlyAnApplicationOwnedMessage() {
        val result = visitErrorMessage(IllegalStateException("You must be within 50 metres of the destination.$requestDetails"))
        assertEquals("You must be within 50 metres of the destination. Move closer and retry verification.", result)
        assertFalse(result.contains("SYNTHETIC_SECRET"))
        assertFalse(result.contains("Headers"))
        assertFalse(result.contains("URL"))
    }

    @Test fun unknownAndSingleLineErrorsNeverEchoRequestDetails() {
        listOf("Unexpected failure$requestDetails", "Authorization=Bearer SYNTHETIC_SECRET", "", null).forEach {
            assertEquals("Could not finish this visit action. Check your connection and try again.",
                visitErrorMessage(IllegalStateException(it)))
        }
    }

    @Test fun cleanupFailureDoesNotExposeItsNestedRequest() {
        val result = visitErrorMessage(VisitPhotoCleanupException(IllegalStateException(requestDetails)))
        assertEquals("Visit cancelled, but photo cleanup failed. Cancel again to retry cleanup.", result)
    }
}
