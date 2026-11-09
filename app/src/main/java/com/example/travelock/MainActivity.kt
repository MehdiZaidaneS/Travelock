package com.example.travelock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.travelock.ui.auth.LandingScreen
import com.example.travelock.ui.auth.LoginScreen
import com.example.travelock.ui.auth.RegisterScreen
import com.example.travelock.ui.theme.TravelockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TravelockTheme {
                // State to keep track of which screen is currently displayed
                var currentScreen by remember { mutableStateOf("landing") }

                // Switch between screens based on user interaction
                when (currentScreen) {
                    "landing" -> LandingScreen(
                        onNavigateToLogin = { currentScreen = "login" },
                        onNavigateToRegister = { currentScreen = "register" }
                    )
                    "login" -> LoginScreen(
                        onLoginClick = { email, password ->
                            // TODO: Add Supabase login authentication logic later in Sprint 2/3
                        },
                        onNavigateBack = { currentScreen = "landing" }
                    )
                    "register" -> RegisterScreen(
                        onRegisterClick = { email, password ->
                            // TODO: Add Supabase registration logic later in Sprint 2/3
                        },
                        onNavigateBack = { currentScreen = "landing" }
                    )
                }
            }
        }
    }
}