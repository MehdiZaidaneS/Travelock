package com.example.travelock.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelock.data.model.Notification
import com.example.travelock.data.model.Post
import com.example.travelock.data.repository.NotificationDetailsRepository
import com.example.travelock.ui.screens.notifications.NotificationListItem
import com.example.travelock.ui.screens.notifications.NotificationsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for the Notifications inbox and top-bar unread badge (T-12, T-13, T-14).
 * Scoped at TravelockNavGraph so the top-bar badge and inbox remain synchronized.
 */
class NotificationViewModel(
    private val repository: NotificationDetailsRepository = NotificationDetailsRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    fun syncWithAuthUser(userId: String?) {
        if (userId.isNullOrBlank()) {
            repository.clearCache()
            _uiState.value = NotificationsUiState()
            return
        }
        if (userId != _uiState.value.userId) {
            repository.clearCache()
            _uiState.value = NotificationsUiState(
                userId = userId,
                isLoading = true,
            )
            loadNotifications(forceRefresh = false)
        }
    }

    fun refreshUnreadCount() {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            try {
                val count = repository.getUnreadCount()
                if (_uiState.value.userId == currentUserId) {
                    _uiState.update { it.copy(unreadCount = count) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep existing badge count on transient network failure.
            }
        }
    }

    fun loadNotifications(forceRefresh: Boolean = false) {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return

        val showInitialLoading = _uiState.value.items.isEmpty() && !forceRefresh
        _uiState.update {
            it.copy(
                isLoading = showInitialLoading,
                isRefreshing = !showInitialLoading,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val bundle = repository.loadInbox()
                if (_uiState.value.userId != currentUserId) return@launch

                _uiState.update { state ->
                    val listItems = bundle.notifications.map { notification ->
                        NotificationListItem(
                            notification = notification,
                            sender = bundle.senders[notification.senderId],
                            isActionInFlight = notification.notificationId in state.inFlightIds,
                        )
                    }
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        items = listItems,
                        unreadCount = bundle.unreadCount,
                        errorMessage = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (_uiState.value.userId != currentUserId) return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "Unable to load notifications. Check your connection and try again.",
                    )
                }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank() || notificationId.isBlank()) return

        val targetItem = _uiState.value.items.find { it.notification.notificationId == notificationId }
        if (targetItem != null && targetItem.notification.isRead) return

        // Optimistic update for responsive UI.
        _uiState.update { state ->
            val wasUnread = state.items.any { it.notification.notificationId == notificationId && !it.notification.isRead }
            state.copy(
                items = state.items.map { item ->
                    if (item.notification.notificationId == notificationId) {
                        item.copy(notification = item.notification.copy(isRead = true))
                    } else {
                        item
                    }
                },
                unreadCount = if (wasUnread) (state.unreadCount - 1).coerceAtLeast(0) else state.unreadCount,
            )
        }

        viewModelScope.launch {
            try {
                val updated = repository.markAsRead(notificationId)
                if (_uiState.value.userId != currentUserId) return@launch
                if (updated == null) {
                    // Notification was already removed (e.g. deleted post or handled request).
                    loadNotifications(forceRefresh = true)
                } else {
                    refreshUnreadCount()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (_uiState.value.userId != currentUserId) return@launch
                loadNotifications(forceRefresh = true)
            }
        }
    }

    fun markAllAsRead() {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return
        if (_uiState.value.isMarkingAllRead || !_uiState.value.hasUnread) return

        _uiState.update { state ->
            state.copy(
                isMarkingAllRead = true,
                items = state.items.map { item ->
                    item.copy(notification = item.notification.copy(isRead = true))
                },
                unreadCount = 0,
            )
        }

        viewModelScope.launch {
            try {
                repository.markAllAsRead()
                if (_uiState.value.userId != currentUserId) return@launch
                _uiState.update { it.copy(isMarkingAllRead = false) }
                loadNotifications(forceRefresh = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (_uiState.value.userId != currentUserId) return@launch
                _uiState.update {
                    it.copy(
                        isMarkingAllRead = false,
                        snackbarMessage = "Could not mark all notifications as read.",
                    )
                }
                loadNotifications(forceRefresh = true)
            }
        }
    }

    fun respondToFriendRequest(
        notificationId: String,
        friendshipId: String,
        accept: Boolean,
    ) {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank() || friendshipId.isBlank()) return

        // Anti-duplicate guard: ignore repeated taps while an action is in-flight.
        if (notificationId in _uiState.value.inFlightIds) return

        _uiState.update { state ->
            val updatedInFlight = state.inFlightIds + notificationId
            state.copy(
                inFlightIds = updatedInFlight,
                items = state.items.map { item ->
                    if (item.notification.notificationId == notificationId) {
                        item.copy(isActionInFlight = true)
                    } else {
                        item
                    }
                },
            )
        }

        viewModelScope.launch {
            try {
                if (accept) {
                    repository.acceptFriendRequest(friendshipId)
                } else {
                    repository.declineFriendRequest(friendshipId)
                }
                if (_uiState.value.userId != currentUserId) return@launch

                _uiState.update { state ->
                    state.copy(
                        snackbarMessage = if (accept) "Friend request accepted" else "Friend request declined",
                    )
                }
                // DB trigger deletes the pending friend-request notification; re-fetch to sync inbox & badge.
                loadNotifications(forceRefresh = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_uiState.value.userId != currentUserId) return@launch
                if (isAlreadyResolvedError(error)) {
                    _uiState.update {
                        it.copy(snackbarMessage = "This friend request was already handled.")
                    }
                    loadNotifications(forceRefresh = true)
                } else {
                    _uiState.update {
                        it.copy(snackbarMessage = "Could not update friend request. Please try again.")
                    }
                }
            } finally {
                if (_uiState.value.userId == currentUserId) {
                    _uiState.update { state ->
                        val updatedInFlight = state.inFlightIds - notificationId
                        state.copy(
                            inFlightIds = updatedInFlight,
                            items = state.items.map { item ->
                                if (item.notification.notificationId == notificationId) {
                                    item.copy(isActionInFlight = false)
                                } else {
                                    item
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    fun openPostNotification(
        notification: Notification,
        onPostLoaded: (Post) -> Unit,
    ) {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return

        markAsRead(notification.notificationId)

        val postId = notification.postId
        if (postId.isNullOrBlank()) {
            _uiState.update { it.copy(snackbarMessage = "This post is no longer available.") }
            return
        }

        viewModelScope.launch {
            val post = repository.loadPostForNotification(postId)
            if (_uiState.value.userId != currentUserId) return@launch
            if (post != null) {
                onPostLoaded(post)
            } else {
                _uiState.update { it.copy(snackbarMessage = "This post is no longer available.") }
                loadNotifications(forceRefresh = true)
            }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun isAlreadyResolvedError(error: Exception): Boolean {
        val message = error.message.orEmpty().lowercase()
        return "already" in message ||
            "not found" in message ||
            "no longer pending" in message ||
            "22023" in message ||
            "responded" in message
    }
}
