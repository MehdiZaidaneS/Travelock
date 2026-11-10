package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.FriendListItem
import com.example.travelock.data.model.Friendship
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FriendshipRepository {
    private val supabase = SupabaseProvider.client

    suspend fun getMyFriends(): List<FriendListItem> {
        signedInUserId()
        return supabase.postgrest.rpc("get_my_friends").decodeList<FriendListItem>()
    }

    // True when removed; false if this accepted relationship no longer exists.
    // Pass the friendship ID from the list, rather than the other user's ID.
    suspend fun removeFriend(friendshipId: String): Boolean {
        require(friendshipId.isNotBlank()) { "Friendship ID cannot be blank." }
        signedInUserId()
        return supabase.postgrest.rpc("remove_friend", buildJsonObject {
            put("relationship_id", friendshipId)
        }).decodeAs<Boolean>()
    }

    suspend fun sendFriendRequest(targetUserId: String): Friendship {
        require(targetUserId.isNotBlank()) { "Target user ID cannot be blank." }
        val userId = signedInUserId()
        require(targetUserId != userId) { "You cannot send a friend request to yourself." }
        // The server supplies the sender and handles duplicate/reversed requests.
        return supabase.postgrest.rpc("send_friend_request", buildJsonObject {
            put("target_user_id", targetUserId)
        }).decodeAs<Friendship>()
    }

    suspend fun acceptFriendRequest(requestId: String): Friendship = respond(requestId, true)

    suspend fun rejectFriendRequest(requestId: String): Friendship = respond(requestId, false)

    suspend fun getIncomingFriendRequests(): List<Friendship> = pendingRequests("recipient_id")

    suspend fun getOutgoingFriendRequests(): List<Friendship> = pendingRequests("requester_id")

    suspend fun getMyFriendships(): List<Friendship> {
        val userId = signedInUserId()
        return supabase.from("friendships").select {
            filter {
                eq("status", "accepted")
                or {
                    eq("requester_id", userId)
                    eq("recipient_id", userId)
                }
            }
            order("created_at", Order.DESCENDING)
            order("friendship_id", Order.DESCENDING)
        }.decodeList<Friendship>()
    }

    private suspend fun pendingRequests(userColumn: String): List<Friendship> {
        val userId = signedInUserId()
        return supabase.from("friendships").select {
            filter {
                eq(userColumn, userId)
                eq("status", "pending")
            }
            order("created_at", Order.DESCENDING)
            order("friendship_id", Order.DESCENDING)
        }.decodeList<Friendship>()
    }

    private suspend fun respond(requestId: String, accept: Boolean): Friendship {
        require(requestId.isNotBlank()) { "Request ID cannot be blank." }
        signedInUserId()
        // Only the recipient may answer. Keep that check on the server as well.
        return supabase.postgrest.rpc("respond_to_friend_request", buildJsonObject {
            put("request_id", requestId)
            put("accept_request", accept)
        }).decodeAs<Friendship>()
    }

    private suspend fun signedInUserId(): String {
        supabase.auth.awaitInitialization()
        return checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before managing friend requests."
        }.id
    }
}
