package com.example.travelock.ui.visit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.travelock.data.model.VisitAttempt
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisitProgressTest {
    @get:Rule
    val compose = createComposeRule()
    private val visit = VisitAttempt("visit-1", "user-1", "destination-1", Instant.parse("2026-11-09T12:00:00Z"))

    @Test
    fun uploadedPhotoAndAttemptSurviveRestorationForRetry() {
        checkRestoration(VisitProgress(visit, "user-1/visit-1/photo.jpg"))
    }

    @Test
    fun completedProgressSurvivesWithoutReenablingCompletion() {
        checkRestoration(VisitProgress(visit, "user-1/visit-1/photo.jpg", completed = true))
    }

    @Test
    fun signingInAsAnotherUserDiscardsSavedVisit() {
        val restoration = StateRestorationTester(compose)
        var userId by mutableStateOf("user-1")
        lateinit var update: (VisitProgress) -> Unit
        var visible = VisitProgress()
        restoration.setContent {
            var progress by rememberVisitProgress(userId)
            update = { progress = it }
            visible = progress
        }
        compose.runOnIdle { update(VisitProgress(visit, "user-1/visit-1/photo.jpg")) }
        compose.waitForIdle()
        compose.runOnIdle { userId = "user-2" }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(VisitProgress(), visible) }
    }

    private fun checkRestoration(expected: VisitProgress) {
        val restoration = StateRestorationTester(compose)
        lateinit var update: (VisitProgress) -> Unit
        var visible = VisitProgress()
        restoration.setContent {
            var progress by rememberVisitProgress("user-1")
            update = { progress = it }
            visible = progress
        }
        compose.runOnIdle { update(expected) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(expected, visible) }
    }
}
