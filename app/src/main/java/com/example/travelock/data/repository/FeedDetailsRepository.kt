package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.ProfileSummary
import com.example.travelock.ui.screens.feed.FeedCommentItem
import com.example.travelock.ui.screens.feed.FeedFilter
import com.example.travelock.ui.screens.feed.FeedPostItem
import com.example.travelock.ui.screens.feed.RelationshipStatus
import com.example.travelock.ui.screens.feed.UserSearchResultItem
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Implements FeedDetailsRepository to compose Supabase repositories
 * (PostRepository, PostInteractionRepository, FriendshipRepository, ProfileRepository, DestinationRepository)
 * with in-memory caching and authenticated storage downloads.
 */

class FeedDetailsRepository {
    private val postRepository = PostRepository()
    private val postInteractionRepository = PostInteractionRepository()
    private val friendshipRepository = FriendshipRepository()
    private val profileRepository = ProfileRepository()
    private val destinationRepository = DestinationRepository()

    private val profileCache = mutableMapOf<String, ProfileSummary?>()
    private val destinationCache = mutableMapOf<String, Destination?>()

    suspend fun loadFeed(filter: FeedFilter, ownPostToView: String? = null): List<FeedPostItem> = coroutineScope {
        val recent = if (filter == FeedFilter.FRIENDS) {
            (postRepository.getFriendsFeed() + postRepository.getMyPosts(maxResults = 20))
                .filter { it.image.isNotBlank() }
                .distinctBy { it.postId }
                .sortedWith(compareByDescending<Post> { it.createdAt }.thenByDescending { it.postId })
                .take(20)
        } else {
            postRepository.getPublicFeed()
                .filter { it.image.isNotBlank() }
        }

        val focused = ownPostToView?.let { postRepository.getMyPost(it) }?.takeIf {
            filter == FeedFilter.FRIENDS || it.visibility == com.example.travelock.data.model.PostVisibility.PUBLIC
        }
        val rawPosts = if (focused == null) recent else (listOf(focused) + recent.filter { it.postId != focused.postId }).take(20)

        rawPosts.map { post ->
            async {
                val author = profileCache.getOrPut(post.userId) {
                    profileRepository.getProfileSummary(post.userId)
                }
                val destination = destinationCache.getOrPut(post.destinationId) {
                    destinationRepository.getDestination(post.destinationId)
                }
                val interaction = postInteractionRepository.getSummary(post.postId)
                val comments = postInteractionRepository.getComments(post.postId)

                val commentItems = comments.map { comment ->
                    val commentAuthor = profileCache.getOrPut(comment.userId) {
                        profileRepository.getProfileSummary(comment.userId)
                    }
                    FeedCommentItem(comment = comment, author = commentAuthor)
                }

                val achievementBadge = when {
                    post.visitId != null && destination != null -> "Unlocked: ${destination.name}"
                    destination != null -> "Achievement: ${destination.name}"
                    else -> null
                }

                FeedPostItem(
                    post = post,
                    author = author,
                    destination = destination,
                    interaction = interaction,
                    comments = commentItems,
                    achievementBadge = achievementBadge
                )
            }
        }.map { it.await() }
    }

    suspend fun downloadPostImage(imagePath: String): ByteArray? {
        return try {
            if (imagePath.isBlank() || imagePath.startsWith("helsinki_")) return null
            SupabaseProvider.client.storage.from("post-photos").downloadAuthenticated(imagePath)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchUsersWithStatus(query: String): List<UserSearchResultItem> {
        val profiles = profileRepository.searchProfiles(query)
        val myFriends = friendshipRepository.getMyFriends()
        val outgoing = friendshipRepository.getOutgoingFriendRequests()
        val incoming = friendshipRepository.getIncomingFriendRequests()

        val friendIds = myFriends.map { it.userId }.toSet()
        val outgoingMap = outgoing.associate { it.recipientId to it.friendshipId }
        val incomingMap = incoming.associate { it.requesterId to it.friendshipId }

        return profiles.map { profile ->
            val status = when {
                friendIds.contains(profile.userId) -> RelationshipStatus.FRIENDS
                outgoingMap.containsKey(profile.userId) -> RelationshipStatus.OUTGOING_PENDING
                incomingMap.containsKey(profile.userId) -> RelationshipStatus.INCOMING_PENDING
                else -> RelationshipStatus.NONE
            }
            UserSearchResultItem(
                profile = profile,
                relationshipStatus = status,
                incomingRequestId = incomingMap[profile.userId]
            )
        }
    }
}