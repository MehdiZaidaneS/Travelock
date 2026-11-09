package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserUnlock
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserUnlockRepositoryIntegrationTest {

    @Test
    fun readsOwnUnlocksAndHidesOtherUsersUnlocks() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val testEmail = requireNotNull(arguments.getString("supabaseTestEmail")) {
            "Add the supabaseTestEmail test argument."
        }
        val testPassword = requireNotNull(arguments.getString("supabaseTestPassword")) {
            "Add the supabaseTestPassword test argument."
        }
        val destinationId = requireNotNull(arguments.getString("supabaseTestDestinationId")) {
            "Add the seeded destination UUID as supabaseTestDestinationId."
        }

        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()

        try {
            supabase.auth.signInWith(Email) {
                email = testEmail
                password = testPassword
            }

            val user = checkNotNull(supabase.auth.currentUserOrNull())
            val ownUnlocks = UserUnlockRepository().getMyUnlocks()

            // A seeded row must be present so an empty response cannot pass the test.
            assertEquals(
                "Seed one unlock for this account and destination before running the test.",
                1,
                ownUnlocks.count { it.destinationId == destinationId },
            )
            assertTrue(ownUnlocks.all { it.userId == user.id })

            // Omit the user filter: the database must still hide the other account's row.
            // Seed the same destination for both test accounts to exercise this check.
            val visibleUnlocks = supabase.from("user_unlocks")
                .select {
                    filter { eq("destination_id", destinationId) }
                }
                .decodeList<UserUnlock>()

            assertEquals(1, visibleUnlocks.size)
            assertEquals(user.id, visibleUnlocks.single().userId)
            assertEquals(
                ownUnlocks.single { it.destinationId == destinationId },
                visibleUnlocks.single(),
            )
        } finally {
            supabase.auth.signOut()
        }
    }
}
