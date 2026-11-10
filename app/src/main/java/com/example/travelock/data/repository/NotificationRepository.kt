package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Notification
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotificationRepository {
    private val supabase = SupabaseProvider.client

    suspend fun getNotifications(limit: Int = 50): List<Notification> {
        require(limit in 1..100) { "Notification limit must be between 1 and 100." }
        val userId = signedInUserId()
        return supabase.from("notifications").select {
            filter { eq("receiver_id", userId) }
            order("created_at", Order.DESCENDING)
            order("notification_id", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<Notification>()
    }

    // A removed notification or unavailable target is a normal null result.
    suspend fun getNotification(notificationId: String): Notification? {
        validateId(notificationId)
        val userId = signedInUserId()
        return supabase.from("notifications").select {
            filter { eq("receiver_id", userId); eq("notification_id", notificationId) }
        }.decodeSingleOrNull<Notification>()
    }

    suspend fun getUnreadCount(): Long {
        signedInUserId()
        return supabase.postgrest.rpc("get_unread_notification_count").decodeAs<Long>()
    }

    suspend fun markAsRead(notificationId: String): Notification? {
        validateId(notificationId)
        val userId = signedInUserId()
        return supabase.from("notifications").update(buildJsonObject { put("is_read", true) }) {
            filter { eq("receiver_id", userId); eq("notification_id", notificationId) }
            select()
        }.decodeSingleOrNull<Notification>()
    }

    suspend fun markAllAsRead() {
        val userId = signedInUserId()
        supabase.from("notifications").update(buildJsonObject { put("is_read", true) }) {
            filter { eq("receiver_id", userId); eq("is_read", false) }
        }
    }

    private fun validateId(id: String) {
        require(UUID.fromString(id).toString() == id) { "Invalid notification ID." }
    }

    private suspend fun signedInUserId(): String {
        supabase.auth.awaitInitialization()
        return checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before loading notifications." }.id
    }
}
