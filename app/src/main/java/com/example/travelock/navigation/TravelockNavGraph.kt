package com.example.travelock.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.travelock.data.mock.MockLocations
import com.example.travelock.data.mock.MockSessionState
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.ui.theme.ThemeMode
import com.example.travelock.ui.auth.AuthEntry
import com.example.travelock.ui.auth.AuthPage
import com.example.travelock.ui.viewmodel.AuthViewModel
import com.example.travelock.data.model.ProfileOnboardingState
import com.example.travelock.data.model.User
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.travelock.ui.viewmodel.ProfileViewModel
import com.example.travelock.ui.visit.VisitFlowHost
import com.example.travelock.ui.visit.rememberVisitFlowController
import com.example.travelock.ui.share.rememberSharePostFactory
import com.example.travelock.data.model.Post
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
    authViewModel: AuthViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel()
) {
    val authState by authViewModel.flow.state.collectAsState()
    val ready = authState.onboarding as? ProfileOnboardingState.Ready
    val loggedInUser = ready?.user
    val displayUser = ready?.let {
        User(it.user.userId, it.profile.username, it.profile.name, it.user.email.orEmpty(),
            it.profile.profileImage, it.profile.createdAt)
    }
    val activeLocationId by MockSessionState.activeLocationId.collectAsState()
    val notifications by MockSessionState.notifications.collectAsState()

    var currentMainRoute by remember { mutableStateOf(BottomNavTab.HOME.route) }
    var selectedFriendId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loggedInUser?.userId, displayUser) {
        profileViewModel.syncWithAuthUser(displayUser)
    }

    if (authState.page != AuthPage.READY) {
        AuthEntry(authViewModel.flow, authState)
        return
    }

    LaunchedEffect(loggedInUser?.userId) {
        currentMainRoute = BottomNavTab.HOME.route
        selectedFriendId = null
    }

    val scope = rememberCoroutineScope()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val editProfileUiState by profileViewModel.editState.collectAsState()

    val context = LocalContext.current
    val visitController = rememberVisitFlowController(checkNotNull(loggedInUser).userId)
    val mapStateHolder = key(loggedInUser.userId) { rememberSaveableStateHolder() }
    val sharePostFactory = rememberSharePostFactory(loggedInUser.userId)
    var feedPostToView by remember(loggedInUser.userId) { mutableStateOf<Post?>(null) }
    val googleSignIn = remember(context) { com.example.travelock.ui.auth.GoogleAccountSignIn(context) }
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(authState.error) {
        authState.error?.let { snackbarHost.showSnackbar(it) }
    }

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
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TravelockTopAppBar(
                title = when (currentMainRoute) {
                    BottomNavTab.HOME.route -> "Travelock"
                    BottomNavTab.FEED.route -> "Friends Feed"
                    BottomNavTab.MAP.route -> "Map"
                    BottomNavTab.PROFILE.route -> "My Profile"
                    "notifications" -> "Notifications"
                    "edit_profile" -> "Edit Profile"
                    "friend_profile" -> "Traveler Profile"
                    else -> "Travelock"
                },
                activeLocationName = activeLocationName.takeUnless { currentMainRoute == BottomNavTab.MAP.route },
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
                onTabSelected = { tab ->
                    currentMainRoute = tab.route
                    if (tab == BottomNavTab.PROFILE) {
                        profileViewModel.loadProfile(forceRefresh = true)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            VisitFlowHost(visitController, shareFactory = sharePostFactory,
                onViewInFeed = { post -> feedPostToView = post; currentMainRoute = BottomNavTab.FEED.route }) { visitActions ->
                when (currentMainRoute) {
                    BottomNavTab.HOME.route -> MainScreen(
                        currentLocation = currentLocation,
                        visitActions = visitActions,
                    )

                    BottomNavTab.FEED.route -> FeedScreen(
                        sharedPost = feedPostToView,
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

                    BottomNavTab.MAP.route -> mapStateHolder.SaveableStateProvider(BottomNavTab.MAP.route) {
                        MapScreen(userId = loggedInUser.userId, visitActions = visitActions)
                    }

                    BottomNavTab.PROFILE.route -> ProfileScreen(
                        uiState = profileUiState,
                        currentThemeMode = currentThemeMode,
                        onThemeModeChange = onThemeModeChange,
                        onEditProfileClick = {
                            profileViewModel.startEditing()
                            currentMainRoute = "edit_profile"
                        },
                        onFriendClick = { friendId ->
                            selectedFriendId = friendId
                            currentMainRoute = "friend_profile"
                        },
                        onLogoutClick = {
                            profileViewModel.syncWithAuthUser(null)
                            authViewModel.flow.logout { googleSignIn.clearCredentialState() }
                        },
                        onRefresh = { profileViewModel.loadProfile(forceRefresh = true) },
                        onOpenDestination = { destination -> visitActions.onOpenDestination(destination, true) },
                        allowProfileEditing = true
                    )

                    "edit_profile" -> EditProfileScreen(
                        editState = editProfileUiState,
                        currentUser = profileUiState.toDisplayUser(),
                        onUsernameChange = profileViewModel::onUsernameChange,
                        onFullNameChange = profileViewModel::onFullNameChange,
                        onImageChange = profileViewModel::onImageChange,
                        onSave = {
                            profileViewModel.saveProfile(context) {
                                currentMainRoute = BottomNavTab.PROFILE.route
                                scope.launch {
                                    snackbarHost.showSnackbar("Profile updated")
                                }
                            }
                        },
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
}

