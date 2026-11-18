package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.FriendProfile
import com.example.travelock.data.model.Location
import com.example.travelock.data.model.Profile
import com.example.travelock.data.model.UserAchievement
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.ui.auth.ProfileAvatarPresets
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import java.time.Instant
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
    val friends: List<FriendProfile>,
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
    private val userAchievementRepository: UserAchievementRepository = UserAchievementRepository(),
) {
    private val supabase = SupabaseProvider.client

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
        val dbAchievementsDeferred = async {
            userAchievementRepository.getMyAchievements()
        }
        val friendsDeferred = async {
            loadFriends(authUser.userId)
        }

        val allLocations = locationsDeferred.await()
        val unlocks = unlocksDeferred.await()
        val dbAchievements = dbAchievementsDeferred.await()
        val friends = friendsDeferred.await()

        val unlockedDestIds = unlocks.map { it.destinationId }.toSet()
        val dbByLocationId = dbAchievements.associateBy { it.location.locationId }
        val mergedAchievements = buildList {
            addAll(dbAchievements)
            allLocations.forEach { loc ->
                if (loc.locationId !in dbByLocationId &&
                    loc.destinations.isNotEmpty() &&
                    loc.destinations.all { it.destinationId in unlockedDestIds }
                ) {
                    val latestUnlockTime = unlocks
                        .filter { u -> loc.destinations.any { it.destinationId == u.destinationId } }
                        .maxOfOrNull { it.unlockedAt } ?: Instant.now()
                    add(
                        UserAchievement(
                            achievementId = "derived-${loc.locationId}",
                            userId = authUser.userId,
                            location = loc,
                            level = 1,
                            unlockedAt = latestUnlockTime,
                        )
                    )
                }
            }
        }

        ProfileBundle(
            profile = profile,
            email = authUser.email.orEmpty(),
            unlocks = unlocks,
            achievements = mergedAchievements,
            allLocations = allLocations,
            friends = friends,
        )
    }

    private suspend fun loadFriends(currentUserId: String): List<FriendProfile> {
        val friendships = safeCall(emptyList()) { friendshipRepository.getMyFriendships() }
        val friendIds = friendships.map { f ->
            if (f.requesterId == currentUserId) f.recipientId else f.requesterId
        }.distinct()
        if (friendIds.isEmpty()) return emptyList()

        val fetchedProfiles = safeCall(emptyList()) {
            supabase.from("profiles")
                .select {
                    filter {
                        isIn("user_id", friendIds)
                    }
                }
                .decodeList<FriendProfile>()
        }.associateBy { it.userId }

        return friendIds.map { id ->
            fetchedProfiles[id] ?: FriendProfile(
                userId = id,
                username = "traveler_${id.take(6)}",
                name = "Travelock Traveler",
                profileImage = "",
            )
        }
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
