package com.example.travelock.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.travelock.data.mock.MockLocations
import com.example.travelock.data.mock.MockSessionState
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.ui.theme.ThemeMode
import com.example.travelock.data.mock.repository.AuthRepository
import com.example.travelock.ui.auth.LandingScreen
import com.example.travelock.ui.auth.LoginScreen
import com.example.travelock.ui.auth.RegisterScreen
import com.example.travelock.ui.components.BottomNavBar
import com.example.travelock.ui.components.BottomNavTab
import com.example.travelock.ui.components.TravelockTopAppBar
import com.example.travelock.ui.screens.feed.FeedScreen
import com.example.travelock.ui.screens.main.MainScreen
import com.example.travelock.ui.screens.map.MapScreen
import com.example.travelock.ui.screens.notifications.NotificationsScreen
import com.example.travelock.ui.screens.profile.EditProfileScreen
import com.example.travelock.ui.screens.profile.FriendProfileScreen
import com.example.travelock.ui.screens.profile.ProfileScreen
import com.example.travelock.ui.viewmodel.LocationViewModel
import kotlinx.coroutines.launch

/**
 * Root Navigation & Scaffold Shell for Travelock separating the Unauthenticated Flow
 * (Landing, Login, Register) from the Authenticated Flow (Scaffold with TopAppBar + BottomNavBar).
 */
@Composable
fun TravelockNavGraph(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    locationViewModel: LocationViewModel,
    authRepository: AuthRepository = remember { AuthRepository() }
) {
    val scope = rememberCoroutineScope()
    val loggedInUser by authRepository.currentUserFlow.collectAsState()
    val activeLocationId by MockSessionState.activeLocationId.collectAsState()
    val notifications by MockSessionState.notifications.collectAsState()

    var authScreen by remember { mutableStateOf("landing") }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var authInfoMessage by remember { mutableStateOf<String?>(null) }

    var currentMainRoute by remember { mutableStateOf(BottomNavTab.HOME.route) }
    var selectedFriendId by remember { mutableStateOf<String?>(null) }

    // ── Unauthenticated Flow (T-04, T-05) ──────────────────────────────────────
    if (loggedInUser == null) {
        when (authScreen) {
            "landing" -> LandingScreen(
                onNavigateToLogin = {
                    authErrorMessage = null
                    authInfoMessage = null
                    authScreen = "login"
                },
                onNavigateToRegister = {
                    authErrorMessage = null
                    authInfoMessage = null
                    authScreen = "register"
                }
            )

            "login" -> LoginScreen(
                onLoginClick = { email, password ->
                    scope.launch {
                        authRepository.loginWithEmail(email, password)
                            .onSuccess {
                                authErrorMessage = null
                                currentMainRoute = BottomNavTab.HOME.route
                            }
                            .onFailure { authErrorMessage = it.message }
                    }
                },
                onGoogleSignInClick = {
                    scope.launch {
                        authRepository.loginWithGoogle()
                            .onSuccess {
                                authErrorMessage = null
                                currentMainRoute = BottomNavTab.HOME.route
                            }
                    }
                },
                onResetPasswordClick = { email ->
                    scope.launch {
                        authRepository.resetPassword(email)
                            .onSuccess {
                                authErrorMessage = null
                                authInfoMessage = "Password reset link sent to $email"
                            }
                            .onFailure { authErrorMessage = it.message }
                    }
                },
                onNavigateToRegister = {
                    authErrorMessage = null
                    authInfoMessage = null
                    authScreen = "register"
                },
                onNavigateBack = {
                    authErrorMessage = null
                    authInfoMessage = null
                    authScreen = "landing"
                },
                errorMessage = authErrorMessage,
                infoMessage = authInfoMessage
            )

            "register" -> RegisterScreen(
                onRegisterFullClick = { email, password, confirmPassword, username, fullName ->
                    if (password != confirmPassword) {
                        authErrorMessage = "Passwords do not match."
                    } else {
                        scope.launch {
                            authRepository.registerWithEmail(email, password, username, fullName)
                                .onSuccess {
                                    authErrorMessage = null
                                    currentMainRoute = BottomNavTab.HOME.route
                                }
                                .onFailure { authErrorMessage = it.message }
                        }
                    }
                },
                onGoogleSignInClick = {
                    scope.launch {
                        authRepository.loginWithGoogle()
                            .onSuccess {
                                authErrorMessage = null
                                currentMainRoute = BottomNavTab.HOME.route
                            }
                    }
                },
                onNavigateToLogin = {
                    authErrorMessage = null
                    authInfoMessage = null
                    authScreen = "login"
                },
                onNavigateBack = {
                    authErrorMessage = null
                    authInfoMessage = null
                    authScreen = "landing"
                },
                errorMessage = authErrorMessage
            )
        }
        return
    }

    val context = LocalContext.current

    val currentLocation by
    locationViewModel.currentLocation.collectAsState()

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                locationViewModel.refreshLocation()
            }
        }

    LaunchedEffect(Unit) {

        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {

            locationViewModel.refreshLocation()

        } else {

            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ── Authenticated Flow (Scaffold + TopBar + BottomNavBar T-01) ─────────────
    val activeLocationName = remember(activeLocationId) {
        MockLocations.allLocations.find { it.locationId == activeLocationId }?.name
    }
    val unreadCount = remember(notifications) {
        notifications.count { !it.isRead }
    }
    val isSubScreen = currentMainRoute in listOf("notifications", "edit_profile", "friend_profile")

    Scaffold(
        topBar = {
            TravelockTopAppBar(
                title = when (currentMainRoute) {
                    BottomNavTab.HOME.route -> "Travelock"
                    BottomNavTab.FEED.route -> "Friends Feed"
                    BottomNavTab.MAP.route -> "Helsinki Map"
                    BottomNavTab.PROFILE.route -> "My Profile"
                    "notifications" -> "Notifications"
                    "edit_profile" -> "Edit Profile"
                    "friend_profile" -> "Traveler Profile"
                    else -> "Travelock"
                },
                activeLocationName = activeLocationName,
                unreadNotificationCount = unreadCount,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                onNotificationsClick = { currentMainRoute = "notifications" },
                showBackButton = isSubScreen,
                onBackClick = { currentMainRoute = BottomNavTab.HOME.route }
            )
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = currentMainRoute,
                onTabSelected = { tab -> currentMainRoute = tab.route }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentMainRoute) {
                BottomNavTab.HOME.route -> MainScreen(
                    currentLocation = currentLocation
                )

                BottomNavTab.FEED.route -> FeedScreen(
                    currentUserId = loggedInUser?.userId ?: MockUsers.currentUser.userId,
                    onOpenUserProfile = { userId ->
                        if (userId == (loggedInUser?.userId ?: MockUsers.currentUser.userId)) {
                            currentMainRoute = BottomNavTab.PROFILE.route
                        } else {
                            selectedFriendId = userId
                            currentMainRoute = "friend_profile"
                        }
                    }
                )

                BottomNavTab.MAP.route -> MapScreen()

                BottomNavTab.PROFILE.route -> ProfileScreen(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    onEditProfileClick = { currentMainRoute = "edit_profile" },
                    onFriendClick = { friendId ->
                        selectedFriendId = friendId
                        currentMainRoute = "friend_profile"
                    },
                    onLogoutClick = {
                        scope.launch {
                            authRepository.logout()
                            authScreen = "landing"
                        }
                    }
                )

                "edit_profile" -> EditProfileScreen(
                    onSaved = { currentMainRoute = BottomNavTab.PROFILE.route },
                    onCancel = { currentMainRoute = BottomNavTab.PROFILE.route }
                )

                "friend_profile" -> FriendProfileScreen(
                    friendUserId = selectedFriendId ?: MockUsers.amir.userId
                )

                "notifications" -> NotificationsScreen(
                    onNavigateToFeed = { currentMainRoute = BottomNavTab.FEED.route },
                    onNavigateToUserProfile = { userId ->
                        selectedFriendId = userId
                        currentMainRoute = "friend_profile"
                    }
                )
            }
        }
    }
}

