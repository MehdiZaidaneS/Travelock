package com.example.travelock.data.mock

import com.example.travelock.data.model.Comment
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.FriendRequest
import com.example.travelock.data.model.Location
import com.example.travelock.data.model.Notification
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.User
import com.example.travelock.data.model.UserAchievement
import com.example.travelock.data.model.UserUnlock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive in-memory state holder for the mock GUI phase so that every user interaction
 * (logging in, enrolling in a Location, unlocking a Destination via QR or Photo,
 * posting to the feed, liking/commenting, accepting friend requests, or editing the profile)
 * immediately updates all observing screens across the 23-step flow.
 */
object MockSessionState {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _enrolledLocationIds = MutableStateFlow<Set<String>>(emptySet())
    val enrolledLocationIds: StateFlow<Set<String>> = _enrolledLocationIds.asStateFlow()

    private val _activeLocationId = MutableStateFlow<String?>(null)
    val activeLocationId: StateFlow<String?> = _activeLocationId.asStateFlow()

    private val _unlockedDestinations = MutableStateFlow<List<UserUnlock>>(MockAchievements.mehdiUnlocks)
    val unlockedDestinations: StateFlow<List<UserUnlock>> = _unlockedDestinations.asStateFlow()

    private val _achievements = MutableStateFlow<List<UserAchievement>>(MockAchievements.mehdiAchievements)
    val achievements: StateFlow<List<UserAchievement>> = _achievements.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(MockPosts.initialPosts)
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _friends = MutableStateFlow<List<User>>(listOf(MockUsers.amir, MockUsers.jesper))
    val friends: StateFlow<List<User>> = _friends.asStateFlow()

    private val _friendRequests = MutableStateFlow<List<FriendRequest>>(MockNotifications.initialFriendRequests)
    val friendRequests: StateFlow<List<FriendRequest>> = _friendRequests.asStateFlow()

    private val _notifications = MutableStateFlow<List<Notification>>(MockNotifications.initialNotifications)
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _selectedRouteDestinations = MutableStateFlow<List<Destination>>(emptyList())
    val selectedRouteDestinations: StateFlow<List<Destination>> = _selectedRouteDestinations.asStateFlow()

    fun setLoggedInUser(user: User?) {
        _currentUser.value = user
    }

    fun enrollInLocation(locationId: String) {
        _enrolledLocationIds.value = _enrolledLocationIds.value + locationId
        _activeLocationId.value = locationId
    }

    fun setActiveLocation(locationId: String?) {
        _activeLocationId.value = locationId
    }

    fun isDestinationUnlocked(destinationId: String): Boolean =
        _unlockedDestinations.value.any { it.destinationId == destinationId }

    /**
     * Unlocks a destination and checks whether all destinations in its parent [Location]
     * are now unlocked; if so, awards a new [UserAchievement] and adds a notification.
     * Returns the newly earned [UserAchievement] if the location was completed, or null.
     */
    fun unlockDestination(destination: Destination): UserAchievement? {
        val userId = _currentUser.value?.userId ?: MockUsers.currentUser.userId
        if (!isDestinationUnlocked(destination.destinationId)) {
            val newUnlock = UserUnlock(
                unlockId = "unlock-${UUID.randomUUID()}",
                userId = userId,
                destinationId = destination.destinationId,
                unlockedAt = Instant.now(),
                destination = destination
            )
            _unlockedDestinations.value = listOf(newUnlock) + _unlockedDestinations.value
        }

        // Check if parent Location is now 100% unlocked
        val parentLocation = MockLocations.allLocations.find { it.locationId == destination.locationId }
            ?: return null
        val allDestUnlocked = parentLocation.destinations.all { d ->
            _unlockedDestinations.value.any { it.destinationId == d.destinationId }
        }
        val alreadyHasAchievement = _achievements.value.any {
            it.location.locationId == parentLocation.locationId
        }

        if (allDestUnlocked && !alreadyHasAchievement) {
            val achievement = UserAchievement(
                achievementId = "ach-${UUID.randomUUID()}",
                userId = userId,
                location = parentLocation,
                level = _achievements.value.size + 1,
                unlockedAt = Instant.now()
            )
            _achievements.value = listOf(achievement) + _achievements.value

            // Also simulate friends liking the user's latest picture (Step 22 of Travelock.pdf user flow)
            val latestUserPost = _posts.value.firstOrNull { it.userId == userId }
            val newNotif = Notification(
                notificationId = "notif-${UUID.randomUUID()}",
                senderId = MockUsers.amir.userId,
                receiverId = userId,
                type = "like",
                message = "Amir Noori (@amir_n) and Jesper Holmström (@jesper_h) liked your picture in ${parentLocation.name}!",
                isRead = false,
                createdAt = Instant.now(),
                postId = latestUserPost?.postId
            )
            _notifications.value = listOf(newNotif) + _notifications.value
            return achievement
        }
        return null
    }

    fun addPost(destination: Destination, description: String, imageTag: String = destination.image): Post {
        val user = _currentUser.value ?: MockUsers.currentUser
        val newPost = Post(
            postId = "post-${UUID.randomUUID()}",
            userId = user.userId,
            image = imageTag,
            imageDescription = description.ifBlank { "Visited ${destination.name} in Helsinki!" },
            destinationId = destination.destinationId,
            createdAt = Instant.now(),
            likes = listOf(MockUsers.amir),
            comments = emptyList()
        )
        _posts.value = listOf(newPost) + _posts.value
        return newPost
    }

    fun toggleLike(postId: String) {
        val user = _currentUser.value ?: MockUsers.currentUser
        _posts.value = _posts.value.map { post ->
            if (post.postId != postId) return@map post
            val alreadyLiked = post.likes.any { it.userId == user.userId }
            val updatedLikes = if (alreadyLiked) {
                post.likes.filterNot { it.userId == user.userId }
            } else {
                post.likes + user
            }
            post.copy(likes = updatedLikes)
        }
    }

    fun addComment(postId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val user = _currentUser.value ?: MockUsers.currentUser
        val comment = Comment(
            commentId = "comment-${UUID.randomUUID()}",
            postId = postId,
            userId = user.userId,
            content = trimmed,
            createdAt = Instant.now()
        )
        _posts.value = _posts.value.map { post ->
            if (post.postId == postId) {
                post.copy(comments = post.comments + comment)
            } else {
                post
            }
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        val user = _currentUser.value ?: MockUsers.currentUser
        if (_friendRequests.value.any {
                (it.senderId == user.userId && it.receiverId == targetUserId) ||
                    (it.senderId == targetUserId && it.receiverId == user.userId)
            }
        ) return

        val req = FriendRequest(
            requestId = "freq-${UUID.randomUUID()}",
            senderId = user.userId,
            receiverId = targetUserId,
            status = "pending",
            createdAt = Instant.now()
        )
        _friendRequests.value = listOf(req) + _friendRequests.value
    }

    fun respondToFriendRequest(requestId: String, accept: Boolean) {
        val req = _friendRequests.value.find { it.requestId == requestId } ?: return
        val newStatus = if (accept) "accepted" else "declined"
        _friendRequests.value = _friendRequests.value.map {
            if (it.requestId == requestId) it.copy(status = newStatus) else it
        }
        if (accept) {
            val sender = MockUsers.findById(req.senderId)
            if (sender != null && _friends.value.none { it.userId == sender.userId }) {
                _friends.value = _friends.value + sender
            }
        }
    }

    fun markNotificationRead(notificationId: String) {
        _notifications.value = _notifications.value.map {
            if (it.notificationId == notificationId) it.copy(isRead = true) else it
        }
    }

    fun markAllNotificationsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun updateProfile(username: String, name: String, profileImage: String) {
        val current = _currentUser.value ?: MockUsers.currentUser
        _currentUser.value = current.copy(
            username = username.trim(),
            name = name.trim(),
            profileImage = profileImage
        )
    }

    fun toggleRouteDestination(destination: Destination) {
        val current = _selectedRouteDestinations.value
        _selectedRouteDestinations.value = if (current.any { it.destinationId == destination.destinationId }) {
            current.filterNot { it.destinationId == destination.destinationId }
        } else {
            current + destination
        }
    }

    fun clearRoute() {
        _selectedRouteDestinations.value = emptyList()
    }
}

