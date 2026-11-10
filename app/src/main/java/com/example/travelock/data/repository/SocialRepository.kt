package com.example.travelock.data.repository

import com.example.travelock.data.mock.MockSessionState
import com.example.travelock.data.mock.MockUsers
import com.example.travelock.data.model.Destination
import com.example.travelock.data.model.FriendRequest
import com.example.travelock.data.model.Post
import com.example.travelock.data.model.User
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles Social Features & Feed (PBI-16 through PBI-19, PBI-28, PBI-34).
 * Preserves [PostRepository], [PostPhotoRepository], and [FriendshipRepository] intact
 * while providing reactive mock data and explicit `// TODO: SUPABASE` integration comments.
 */
class SocialRepository {

    val postsFlow: StateFlow<List<Post>> = MockSessionState.posts
    val friendsFlow: StateFlow<List<User>> = MockSessionState.friends
    val friendRequestsFlow: StateFlow<List<FriendRequest>> = MockSessionState.friendRequests

    /**
     * PBI-16: See a feed of recent pictures from friends in chronological order (newest first).
     */
    suspend fun getFeedPosts(): List<Post> {
        // TODO: SUPABASE - Replace mock feed query with:
        // supabase.from("posts")
        //     .select(Columns.raw("*, profiles!user_id(*), destinations(*), comments(*, profiles!user_id(*))"))
        //     .order("created_at", Order.DESCENDING)
        //     .decodeList<Post>()

        return MockSessionState.posts.value.sortedByDescending { it.createdAt }
    }

    /**
     * PBI-19: Like or unlike a friend's post.
     */
    suspend fun toggleLike(postId: String) {
        // TODO: SUPABASE - Replace mock like toggle with:
        // supabase.from("post_likes").insert(...) or .delete()

        MockSessionState.toggleLike(postId)
    }

    /**
     * PBI-19: Write and submit a non-empty comment on a post.
     */
    suspend fun addComment(postId: String, content: String): Result<Unit> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Comment cannot be empty."))
        }

        // TODO: SUPABASE - Replace mock comment creation with:
        // supabase.from("comments").insert(buildJsonObject {
        //     put("post_id", postId)
        //     put("user_id", currentUserId)
        //     put("content", content.trim())
        // })

        MockSessionState.addComment(postId, content)
        return Result.success(Unit)
    }

    /**
     * PBI-15: Create a post associated with a destination.
     */
    suspend fun createPost(destination: Destination, description: String): Post {
        // TODO: SUPABASE - Replace mock post creation with PostPhotoRepository + PostRepository:
        // val postId = UUID.randomUUID().toString()
        // val imagePath = PostPhotoRepository().uploadPostPhoto(postId, jpegBytes)
        // return PostRepository().createPost(postId, destination.destinationId, imagePath, description)

        return MockSessionState.addPost(destination, description)
    }

    /**
     * PBI-17: Search for other users by username or name.
     */
    suspend fun searchUsers(query: String): List<User> {
        val trimmed = query.trim()
        val currentUserId = MockSessionState.currentUser.value?.userId ?: MockUsers.currentUser.userId

        // TODO: SUPABASE - Replace mock user search with:
        // supabase.from("profiles").select {
        //     filter {
        //         or {
        //             ilike("username", "%$trimmed%")
        //             ilike("name", "%$trimmed%")
        //         }
        //     }
        // }.decodeList<Profile>()

        return MockUsers.allUsers
            .filter { it.userId != currentUserId }
            .filter {
                trimmed.isEmpty() ||
                    it.username.contains(trimmed, ignoreCase = true) ||
                    it.name.contains(trimmed, ignoreCase = true)
            }
    }

    /**
     * PBI-17: Send a friend request to another user (prevents duplicates).
     */
    suspend fun sendFriendRequest(targetUserId: String) {
        // TODO: SUPABASE - Replace mock call with FriendshipRepository:
        // FriendshipRepository().sendFriendRequest(targetUserId)

        MockSessionState.sendFriendRequest(targetUserId)
    }

    /**
     * PBI-34: Accept an incoming friend request.
     */
    suspend fun acceptFriendRequest(requestId: String) {
        // TODO: SUPABASE - Replace mock call with FriendshipRepository:
        // FriendshipRepository().acceptFriendRequest(requestId)

        MockSessionState.respondToFriendRequest(requestId, accept = true)
    }

    /**
     * PBI-34: Decline an incoming friend request.
     */
    suspend fun declineFriendRequest(requestId: String) {
        // TODO: SUPABASE - Replace mock call with FriendshipRepository:
        // FriendshipRepository().rejectFriendRequest(requestId)

        MockSessionState.respondToFriendRequest(requestId, accept = false)
    }
}

