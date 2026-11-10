package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Friendship
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendshipRepositoryIntegrationTest {
    // Uses two temporary accounts. Keeps an accepted friendship for later feed
    // tests. If they are already friends, reruns verify lists and idempotency.
    @Test
    fun sendsRejectsResendsAndAcceptsBetweenTwoAccounts() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(arguments.getString(name)) {
            "Add the local $name test argument."
        }
        val emailA = argument("supabaseTestEmail")
        val passwordA = argument("supabaseTestPassword")
        val emailB = argument("supabaseSecondTestEmail")
        val passwordB = argument("supabaseSecondTestPassword")
        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()
        val repository = FriendshipRepository()

        suspend fun signIn(email: String, password: String): String {
            supabase.auth.signOut()
            supabase.auth.signInWith(Email) { this.email = email; this.password = password }
            return checkNotNull(supabase.auth.currentUserOrNull()).id
        }
        suspend fun verifyAccepted(userId: String, otherId: String, expected: Friendship) {
            val accepted = repository.getMyFriendships()
            assertEquals(expected, accepted.single { it.friendshipId == expected.friendshipId })
            assertTrue(accepted.all { it.status == "accepted" && (it.requesterId == userId || it.recipientId == userId) })
            assertTrue(repository.getIncomingFriendRequests().none { it.friendshipId == expected.friendshipId })
            assertTrue(repository.getOutgoingFriendRequests().none { it.friendshipId == expected.friendshipId })
            assertEquals(expected, repository.sendFriendRequest(otherId))
            val visible = supabase.from("friendships").select().decodeList<Friendship>()
            assertTrue(visible.all { it.requesterId == userId || it.recipientId == userId })
            assertTrue(visible.contains(expected))
        }

        try {
            val userB = signIn(emailB, passwordB)
            val userA = signIn(emailA, passwordA)
            assertNotEquals("Use two different temporary accounts.", userA, userB)
            var first = repository.sendFriendRequest(userB)
            if (first.status == "accepted") {
                verifyAccepted(userA, userB, first)
                signIn(emailB, passwordB)
                verifyAccepted(userB, userA, first)
                return@runBlocking
            }
            // An earlier interrupted run may have left B's request to A pending.
            if (first.recipientId == userA) {
                assertEquals("pending", first.status)
                repository.rejectFriendRequest(first.friendshipId)
                first = repository.sendFriendRequest(userB)
            }
            assertEquals(userA, first.requesterId)
            assertEquals(userB, first.recipientId)
            assertEquals("pending", first.status)
            assertEquals(first, repository.sendFriendRequest(userB))
            assertTrue(repository.getOutgoingFriendRequests().contains(first))
            assertTrue(repository.getIncomingFriendRequests().none { it.friendshipId == first.friendshipId })
            assertTrue(repository.getMyFriendships().none { it.friendshipId == first.friendshipId })
            try {
                repository.acceptFriendRequest(first.friendshipId)
                fail("A sender must not accept their own request.")
            } catch (error: PostgrestRestException) {
                assertEquals("22023", error.code)
            }

            signIn(emailB, passwordB)
            assertEquals(first, repository.sendFriendRequest(userA))
            assertTrue(repository.getIncomingFriendRequests().contains(first))
            assertTrue(repository.getOutgoingFriendRequests().none { it.friendshipId == first.friendshipId })
            val rejected = repository.rejectFriendRequest(first.friendshipId)
            assertEquals("rejected", rejected.status)
            assertNotNull(rejected.respondedAt)
            assertEquals(rejected, repository.rejectFriendRequest(first.friendshipId))
            assertTrue(repository.getIncomingFriendRequests().none { it.friendshipId == first.friendshipId })

            signIn(emailA, passwordA)
            val resent = repository.sendFriendRequest(userB)
            assertNotEquals(first.friendshipId, resent.friendshipId)
            assertEquals("pending", resent.status)
            assertTrue(repository.getOutgoingFriendRequests().contains(resent))

            signIn(emailB, passwordB)
            val accepted = repository.acceptFriendRequest(resent.friendshipId)
            assertEquals("accepted", accepted.status)
            assertNotNull(accepted.respondedAt)
            assertEquals(accepted, repository.acceptFriendRequest(resent.friendshipId))
            verifyAccepted(userB, userA, accepted)
            signIn(emailA, passwordA)
            verifyAccepted(userA, userB, accepted)
        } finally {
            supabase.auth.signOut()
        }
    }
}
