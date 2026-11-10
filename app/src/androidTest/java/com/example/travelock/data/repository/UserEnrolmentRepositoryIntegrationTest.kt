package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserEnrolment
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserEnrolmentRepositoryIntegrationTest {
    // Uses local test arguments and leaves this account's enrolment saved.
    // Repeated runs reuse the same row; this test does not award an unlock.
    @Test
    fun enrolsOnceAndReadsTheSavedEnrolment() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = requireNotNull(arguments.getString("supabaseTestEmail")) {
            "Add the local supabaseTestEmail test argument."
        }
        val password = requireNotNull(arguments.getString("supabaseTestPassword")) {
            "Add the local supabaseTestPassword test argument."
        }
        val destinationId = requireNotNull(arguments.getString("supabaseTestDestinationId")) {
            "Add an existing destination UUID as supabaseTestDestinationId."
        }
        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()
        try {
            supabase.auth.signInWith(Email) { this.email = email; this.password = password }
            val user = checkNotNull(supabase.auth.currentUserOrNull())
            val repository = UserEnrolmentRepository()
            val unlocksBefore = UserUnlockRepository().getMyUnlocks().toSet()
            val before = repository.getMyEnrolments()

            val first = repository.enrolInDestination(destinationId)
            assertEquals(user.id, first.userId)
            assertEquals(destinationId, first.destinationId)
            val existing = before.singleOrNull { it.destinationId == destinationId }
            if (existing != null) assertEquals(existing, first)

            val repeated = repository.enrolInDestination(destinationId)
            assertEquals("Repeated enrolment must keep the original ID and timestamp.", first, repeated)
            val fetched = repository.getMyEnrolments()
            assertTrue(fetched.all { it.userId == user.id })
            assertEquals(first, fetched.single { it.destinationId == destinationId })
            assertEquals(before.toSet() + first, fetched.toSet())

            val detailed = repository.getMyEnrolmentsWithDestinations()
            assertEquals(fetched.toSet(), detailed.map { it.copy(destination = null) }.toSet())
            assertTrue(detailed.all { it.userId == user.id })
            val fixture = checkNotNull(DestinationRepository().getDestination(destinationId))
            assertEquals(fixture, detailed.single { it.destinationId == destinationId }.destination)
            assertTrue(detailed.all { it.destination?.destinationId == it.destinationId })

            // Query without a user filter, as in the earlier SQL RLS check.
            val visible = supabase.from("user_enrolments").select {
                filter { eq("destination_id", destinationId) }
            }.decodeList<UserEnrolment>()
            assertEquals(listOf(first), visible)

            // Bypass the repository's duplicate handling to verify the constraint.
            try {
                supabase.from("user_enrolments").insert(buildJsonObject {
                    put("user_id", user.id)
                    put("destination_id", destinationId)
                })
                fail("The database must reject a duplicate enrolment.")
            } catch (error: PostgrestRestException) {
                assertEquals("23505", error.code)
            }
            assertEquals(fetched.toSet(), repository.getMyEnrolments().toSet())
            assertEquals("Planning a visit must not change unlocks.",
                unlocksBefore, UserUnlockRepository().getMyUnlocks().toSet())
        } finally {
            supabase.auth.signOut()
        }
    }
}
