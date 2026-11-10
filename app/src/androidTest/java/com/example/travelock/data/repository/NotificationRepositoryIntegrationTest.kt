package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.AuthUser
import com.example.travelock.data.model.Notification
import com.example.travelock.data.model.PostVisibility
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationRepositoryIntegrationTest {
    // Uses A's existing Public post and the unrelated confirmed registration account.
    // Keeps one comment/rejected request and marks A's inbox read. No photos/emails.
    @Test
    fun generatesPrivateNotificationsAndMaintainsReadAndRequestState() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val emailA = argument("supabaseTestEmail")
        val passwordA = argument("supabaseTestPassword")
        val emailC = argument("supabaseRegistrationEmail")
        val passwordC = argument("supabaseRegistrationPassword")
        val supabase = SupabaseProvider.client
        val auth = AuthRepository()
        val notifications = NotificationRepository()
        val interactions = PostInteractionRepository()
        val friendships = FriendshipRepository()
        var postId: String? = null
        var originalLike: Boolean? = null
        var requestId: String? = null
        var authorId: String? = null
        suspend fun signIn(email: String, password: String): AuthUser {
            auth.logout()
            return auth.loginWithEmail(email, password)
        }
        suspend fun denied(call: suspend () -> Unit) {
            try { call(); fail("Expected server permission rejection.") }
            catch (error: PostgrestRestException) { assertEquals("42501", error.code) }
        }
        try {
            val c = signIn(emailC, passwordC)
            val a = signIn(emailA, passwordA)
            authorId = a.userId
            assertNotEquals("Use two different accounts.", a.userId, c.userId)
            assertTrue("Use a registration account that is not A's accepted friend.",
                friendships.getMyFriendships().none { it.requesterId == c.userId || it.recipientId == c.userId })
            assertTrue("Resolve any reverse A-to-registration pending request first.",
                friendships.getOutgoingFriendRequests().none { it.recipientId == c.userId })
            // A failed earlier run may have left this test's incoming request pending.
            friendships.getIncomingFriendRequests().filter { it.requesterId == c.userId }.forEach {
                friendships.rejectFriendRequest(it.friendshipId)
            }
            val post = checkNotNull(PostRepository().getMyPosts().firstOrNull { it.visibility == PostVisibility.PUBLIC }) {
                "Run the earlier Public post test for A first."
            }
            postId = post.postId

            signIn(emailC, passwordC)
            originalLike = interactions.getSummary(post.postId).isLikedByMe
            interactions.unlikePost(post.postId)
            interactions.likePost(post.postId)
            interactions.likePost(post.postId)
            val comment = interactions.addComment(post.postId, "Notification test ${UUID.randomUUID()}")
            val request = friendships.sendFriendRequest(a.userId)
            requestId = request.friendshipId
            assertEquals(c.userId, request.requesterId)
            assertEquals(a.userId, request.recipientId)
            assertEquals(request, friendships.sendFriendRequest(a.userId))

            signIn(emailA, passwordA)
            val inbox = notifications.getNotifications(100)
            assertTrue(inbox.all { it.receiverId == a.userId })
            assertTrue(inbox.zipWithNext().all { (newer, older) ->
                newer.createdAt.isAfter(older.createdAt) ||
                    (newer.createdAt == older.createdAt && newer.notificationId > older.notificationId)
            })
            assertEquals(inbox.take(1), notifications.getNotifications(1))
            val like = inbox.single { it.type == "like" && it.postId == post.postId && it.senderId == c.userId }
            val commentNotice = inbox.single { it.commentId == comment.commentId }
            val requestNotice = inbox.single { it.friendshipId == request.friendshipId }
            assertTrue(!like.isRead && !commentNotice.isRead && !requestNotice.isRead)
            assertEquals("comment", commentNotice.type)
            assertEquals(post.postId, commentNotice.postId)
            assertEquals(c.userId, commentNotice.senderId)
            assertTrue(commentNotice.message.contains(comment.content.take(120)))
            assertEquals("friend_request", requestNotice.type)
            assertEquals(c.userId, requestNotice.senderId)
            assertNull(requestNotice.postId)
            assertNull(requestNotice.commentId)
            val unread = notifications.getUnreadCount()
            assertTrue(unread >= 3L)
            assertEquals(like.copy(isRead = true), notifications.markAsRead(like.notificationId))
            assertEquals(like.copy(isRead = true), notifications.markAsRead(like.notificationId))
            assertEquals(unread - 1L, notifications.getUnreadCount())
            notifications.markAllAsRead()
            assertEquals(0L, notifications.getUnreadCount())
            denied { supabase.from("notifications").update(buildJsonObject { put("message", "Spoofed") }) {
                filter { eq("notification_id", like.notificationId) }
            } }

            signIn(emailC, passwordC)
            assertTrue(notifications.getNotifications(100).all { it.receiverId == c.userId })
            assertNull(notifications.getNotification(like.notificationId))
            assertNull(notifications.markAsRead(like.notificationId))
            // Omit the repository's receiver filter to test the server boundary.
            assertTrue(supabase.from("notifications").select {
                filter { eq("notification_id", like.notificationId) }
            }.decodeList<Notification>().isEmpty())
            assertTrue(supabase.from("notifications").update(buildJsonObject { put("is_read", false) }) {
                filter { eq("notification_id", like.notificationId) }
                select()
            }.decodeList<Notification>().isEmpty())
            denied { supabase.from("notifications").insert(buildJsonObject {
                put("sender_id", c.userId); put("receiver_id", a.userId)
                put("type", "like"); put("message", "Fake"); put("post_id", post.postId)
            }) }
            interactions.unlikePost(post.postId)
            signIn(emailA, passwordA)
            assertNull(notifications.getNotification(like.notificationId))
            assertEquals(commentNotice.commentId, notifications.getNotification(commentNotice.notificationId)?.commentId)
            friendships.rejectFriendRequest(request.friendshipId)
            assertNull(notifications.getNotification(requestNotice.notificationId))
            auth.logout()
            denied { supabase.from("notifications").select() }
            denied { supabase.postgrest.rpc("get_unread_notification_count") }
        } finally {
            try {
                if (authorId != null && requestId != null) {
                    signIn(emailA, passwordA)
                    friendships.getIncomingFriendRequests().filter { it.friendshipId == requestId }.forEach {
                        friendships.rejectFriendRequest(it.friendshipId)
                    }
                }
                if (postId != null && originalLike != null) {
                    signIn(emailC, passwordC)
                    if (originalLike == true) interactions.likePost(checkNotNull(postId))
                    else interactions.unlikePost(checkNotNull(postId))
                }
            } finally { auth.logout() }
        }
    }
}
