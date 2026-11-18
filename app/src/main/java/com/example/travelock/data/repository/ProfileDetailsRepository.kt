package com.example.travelock.data.repository

import com.example.travelock.data.model.FriendListItem
import com.example.travelock.data.model.Location
import com.example.travelock.data.model.Profile
import com.example.travelock.data.model.UserAchievement
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.ui.auth.ProfileAvatarPresets
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DuplicateUsernameException(message: String = "That username is taken.") : Exception(message)

data class ProfileBundle(
    val profile: Profile,
    val email: String,
    val unlocks: List<UserUnlock>,
    val achievements: List<UserAchievement>,
    val allLocations: List<Location>,
    val friends: List<FriendListItem>,
)

/**
 * Composes existing live Supabase repositories for ProfileScreen and EditProfileScreen
 * without modifying any existing backend repository classes.
 */
class ProfileDetailsRepository(
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val profilePhotoRepository: ProfilePhotoRepository = ProfilePhotoRepository(),
    private val userUnlockRepository: UserUnlockRepository = UserUnlockRepository(),
    private val friendshipRepository: FriendshipRepository = FriendshipRepository(),
    private val locationRepository: LocationRepository = LocationRepository(),
    private val destinationRepository: DestinationRepository = DestinationRepository(),
    private val achievementRepository: AchievementRepository = AchievementRepository(),
) {
    private data class PendingPhoto(val userId: String, val bytes: ByteArray, val path: String)
    private var pendingPhoto: PendingPhoto? = null

    suspend fun loadMyProfileBundle(): ProfileBundle = coroutineScope {
        val authUser = authRepository.getCurrentUser()
            ?: error("You must sign in before loading your profile.")
        val profile = profileRepository.getMyProfile()
            ?: error("Profile not found for the signed-in account.")

        val locationsDeferred = async {
            val baseLocations = safeCall(emptyList()) { locationRepository.getLocations() }
            baseLocations.map { loc ->
                async {
                    val dests = safeCall(emptyList()) {
                        destinationRepository.getDestinationsByLocation(loc.locationId)
                    }
                    loc.copy(destinations = dests)
                }
            }.awaitAll()
        }
        val unlocksDeferred = async {
            safeCall(emptyList()) { userUnlockRepository.getMyUnlocksWithDestinations() }
        }
        val achievementsDeferred = async {
            safeCall(Unit) { achievementRepository.refreshMyAchievements() }
            safeCall(emptyList()) { achievementRepository.getMyAchievements() }
        }
        val friendsDeferred = async {
            safeCall(emptyList()) { friendshipRepository.getMyFriends() }
        }

        ProfileBundle(
            profile = profile,
            email = authUser.email.orEmpty(),
            unlocks = unlocksDeferred.await(),
            achievements = achievementsDeferred.await(),
            allLocations = locationsDeferred.await(),
            friends = friendsDeferred.await(),
        )
    }

    /**
     * Updates username, name, and profile_image on Supabase.
     * Leaves old uploaded photos in the profile-photos bucket per project requirements.
     */
    suspend fun updateMyProfile(
        username: String,
        fullName: String,
        selectedImage: String?,
        currentProfileImage: String,
        loadPhotoBytes: suspend () -> ByteArray?,
    ): Profile {
        val trimmedUsername = username.trim()
        val trimmedName = fullName.trim()
        require(trimmedUsername.length in 3..30) { "Username must be between 3 and 30 characters." }
        require(trimmedName.isNotBlank()) { "Full name cannot be blank." }

        val authUser = authRepository.getCurrentUser()
            ?: error("Your session expired. Please sign in again.")

        val finalImagePath = when {
            selectedImage.isNullOrBlank() -> ""
            ProfileAvatarPresets.find(selectedImage) != null -> selectedImage
            selectedImage == currentProfileImage -> currentProfileImage
            else -> {
                val jpegBytes = requireNotNull(loadPhotoBytes()) { "Could not read the selected photo." }
                val previous = pendingPhoto
                if (previous != null && previous.userId == authUser.userId && previous.bytes.contentEquals(jpegBytes)) {
                    previous.path
                } else {
                    profilePhotoRepository.uploadProfilePhoto(jpegBytes).also { uploadedPath ->
                        pendingPhoto = PendingPhoto(authUser.userId, jpegBytes.copyOf(), uploadedPath)
                    }
                }
            }
        }

        return try {
            profileRepository.updateMyProfile(
                username = trimmedUsername,
                name = trimmedName,
                profileImage = finalImagePath,
            ).also {
                pendingPhoto = null
            }
        } catch (error: PostgrestRestException) {
            if (error.code == "23505" ||
                error.message?.contains("profiles_username_key", ignoreCase = true) == true ||
                error.message?.contains("23505") == true
            ) {
                throw DuplicateUsernameException("That username is taken.")
            }
            throw error
        }
    }

    private suspend fun <T> safeCall(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        fallback
    }
}
