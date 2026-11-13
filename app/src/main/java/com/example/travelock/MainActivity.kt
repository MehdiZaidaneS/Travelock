
package com.example.travelock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.travelock.navigation.TravelockNavGraph
import com.example.travelock.ui.theme.TravelockTheme
import com.example.travelock.ui.viewmodel.ThemeViewModel
import com.example.travelock.ui.viewmodel.LocationViewModel
import com.example.travelock.util.DataStoreManager

class MainActivity : ComponentActivity() {

    // Theme ViewModel
    private val themeViewModel: ThemeViewModel by viewModels {
        val app = application as? TravelockApplication
        val dataStoreManager =
            app?.dataStoreManager ?: DataStoreManager(applicationContext)

        ThemeViewModel.Factory(dataStoreManager)
    }

    // Location ViewModel
    private val locationViewModel: LocationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            val themeMode by
            themeViewModel.themeMode.collectAsState()

            TravelockTheme(
                themeMode = themeMode,
                onThemeModeChange = {
                    themeViewModel.setThemeMode(it)
                }
            ) {
                TravelockNavGraph(
                    currentThemeMode = themeMode,
                    onThemeModeChange = {
                        themeViewModel.setThemeMode(it)
                    },
                    locationViewModel = locationViewModel
                )
            }
        }
    }
}
