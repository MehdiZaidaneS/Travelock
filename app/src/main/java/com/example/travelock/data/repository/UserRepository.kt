package com.example.travelock.data.repository

import com.example.travelock.data.mock.MockSessionState
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.data.model.User
import com.example.travelock.data.model.UserAchievement
import com.example.travelock.data.model.UserUnlock
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles User Profile, Achievements, Unlocks, and Friend Profile lookups (PBI-18, PBI-26 to PBI-29).
 * Preserves [ProfileRepository] and [UserUnlockRepository] intact and documents the exact live calls.
 */
class UserRepository {

    val currentUserFlow: StateFlow<User?> = MockSessionState.currentUser
    val achievementsFlow: StateFlow<List<UserAchievement>> = MockSessionState.achievements
    val unlocksFlow: StateFlow<List<UserUnlock>> = MockSessionState.unlockedDestinations
    val friendsFlow: StateFlow<List<User>> = MockSessionState.friends

    /**
     * PBI-26: View personal profile.
     */
    suspend fun getMyProfile(): User {
        // TODO: SUPABASE - Replace mock call with ProfileRepository + UserUnlockRepository:
        // val profile = ProfileRepository().getMyProfile()
        // val unlocks = UserUnlockRepository().getMyUnlocksWithDestinations()
        // val friendships = FriendshipRepository().getMyFriendships()

        return MockSessionState.currentUser.value ?: MockUsers.currentUser
    }

    /**
     * PBI-29: Edit profile information.
     */
    suspend fun updateMyProfile(
        username: String,
        name: String,
        profileImage: String
    ): Result<User> {
        if (username.isBlank() || name.isBlank()) {
            return Result.failure(IllegalArgumentException("Username and name cannot be blank."))
        }

        // TODO: SUPABASE - Replace mock profile update with ProfileRepository:
        // val updatedProfile = ProfileRepository().updateMyProfile(
        //     username = username.trim(),
        //     name = name.trim(),
        //     profileImage = profileImage
        // )

        MockSessionState.updateProfile(username, name, profileImage)
        return Result.success(MockSessionState.currentUser.value ?: MockUsers.currentUser)
    }

    /**
     * PBI-27: View achievements.
     */
    suspend fun getMyAchievements(): List<UserAchievement> {
        // TODO: SUPABASE - Replace mock achievements query with:
        // supabase.from("user_achievements")
        //     .select(Columns.raw("*, location:locations(*)"))
        //     .eq("user_id", currentUserId)
        //     .decodeList<UserAchievement>()

        return MockSessionState.achievements.value
    }

    /**
     * PBI-26: View unlocked destinations.
     */
    suspend fun getMyUnlocks(): List<UserUnlock> {
        // TODO: SUPABASE - Replace mock unlocks query with UserUnlockRepository:
        // return UserUnlockRepository().getMyUnlocksWithDestinations()

        return MockSessionState.unlockedDestinations.value
    }

    /**
     * PBI-18: View a friend's profile (achievements & unlocked locations).
     */
    suspend fun getUserById(userId: String): User? {
        // TODO: SUPABASE - Replace mock lookup with:
        // supabase.from("profiles").select { filter { eq("user_id", userId) } }.decodeSingleOrNull<Profile>()

        if (userId == (MockSessionState.currentUser.value?.userId ?: MockUsers.currentUser.userId)) {
            return MockSessionState.currentUser.value ?: MockUsers.currentUser
        }
        return MockUsers.findById(userId)
    }
}

