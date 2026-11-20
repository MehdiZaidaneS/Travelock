package com.example.travelock.ui.visit

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.ui.theme.TravelockTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class VisitLocationAccessTest {
    @get:Rule val compose = createComposeRule()

    @Test fun alreadyGrantedPermissionShowsStatusInsteadOfADeadRequestButton() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        compose.setContent { TravelockTheme { Column { VisitLocationAccess(enabled = true) } } }
        compose.onNodeWithText("Precise location allowed").assertExists()
        compose.onNodeWithText("Allow precise location").assertDoesNotExist()
        compose.onNodeWithText("Open app settings").assertDoesNotExist()
    }

    @Test fun deniedPermissionProvidesSettingsAndUpdatesWhenGranted() {
        val access = mutableStateOf(VisitLocationAccessState(false, true))
        var settingsOpened = 0
        var requests = 0
        compose.setContent {
            TravelockTheme { Column {
                VisitLocationAccessContent(access.value, enabled = true, denied = true,
                    requestPermission = { requests++ },
                    openAppSettings = { settingsOpened++; access.value = access.value.copy(preciseAllowed = true) },
                    openLocationSettings = { error("Location services are already enabled") })
            } }
        }
        compose.onNodeWithText("Allow precise location").performClick()
        assertEquals(1, requests)
        compose.onNodeWithText("Open app settings").performClick()
        assertEquals(1, settingsOpened)
        compose.onNodeWithText("Precise location allowed").assertExists()
        compose.onNodeWithText("Allow precise location").assertDoesNotExist()
    }

    @Test fun disabledLocationServicesHaveTheirOwnSettingsAction() {
        val access = mutableStateOf(VisitLocationAccessState(true, false))
        var settingsOpened = 0
        compose.setContent {
            TravelockTheme { Column {
                VisitLocationAccessContent(access.value, enabled = true, denied = false,
                    requestPermission = { error("Permission already granted") },
                    openAppSettings = { error("Permission already granted") },
                    openLocationSettings = { settingsOpened++; access.value = access.value.copy(servicesEnabled = true) })
            } }
        }
        compose.onNodeWithText("Turn on location services").performClick()
        assertEquals(1, settingsOpened)
        compose.onNodeWithText("Turn on location services").assertDoesNotExist()
        compose.onNodeWithText("Precise location allowed").assertExists()
    }
}
