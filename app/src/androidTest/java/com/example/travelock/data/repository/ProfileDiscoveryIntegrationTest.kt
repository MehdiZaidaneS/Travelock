package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Profile
import com.example.travelock.data.model.ProfileSummary
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import java.util.Locale
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
class ProfileDiscoveryIntegrationTest {
    @Test
    fun searchesBasicProfilesWhileKeepingTableAccessPrivate() = runBlocking {
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
        val repository = ProfileRepository()

        suspend fun signIn(email: String, password: String): Profile {
            supabase.auth.signOut()
            supabase.auth.signInWith(Email) { this.email = email; this.password = password }
            val id = checkNotNull(supabase.auth.currentUserOrNull()).id
            // Keep existing details. A test account without a profile gets one once.
            return repository.getMyProfile() ?: repository.createMyProfile(
                username = "discovery_${id.replace("-", "")}",
                name = "Discovery $id",
            )
        }
        fun summary(profile: Profile) = ProfileSummary(
            profile.userId, profile.username, profile.name, profile.profileImage,
        )
        suspend fun verify(own: Profile, other: Profile) {
            assertEquals(summary(own), repository.getProfileSummary(own.userId))
            assertEquals(summary(other), repository.getProfileSummary(other.userId))
            val query = "  ${other.username.uppercase(Locale.ROOT)}  "
            val matches = repository.searchProfiles(query)
            assertTrue("Use test accounts with distinct searchable usernames.", matches.contains(summary(other)))
            assertTrue(matches.size <= 20)
            assertTrue(matches.none { it.userId == own.userId })
            assertTrue(matches.all {
                it.username.contains(other.username, ignoreCase = true) ||
                    it.name.contains(other.username, ignoreCase = true)
            })
            assertTrue(repository.searchProfiles("   ").isEmpty())
            assertTrue(repository.searchProfiles("missing-${UUID.randomUUID()}").isEmpty())
            assertNull(repository.getProfileSummary(UUID.randomUUID().toString()))
            // An explicit filter for B must not bypass the full table's own-only RLS.
            assertTrue(supabase.from("profiles").select {
                filter { eq("user_id", other.userId) }
            }.decodeList<Profile>().isEmpty())
            assertEquals(own, repository.getMyProfile())
        }
        suspend fun requiresSignIn(call: suspend () -> Unit) {
            try {
                call()
                fail("Signed-out repository calls must fail.")
            } catch (_: IllegalStateException) {
                // The repository checks the restored Auth session before querying.
            }
        }

        try {
            val b = signIn(emailB, passwordB)
            val a = signIn(emailA, passwordA)
            assertNotEquals("Use two different test accounts.", a.userId, b.userId)
            verify(a, b)
            signIn(emailB, passwordB)
            verify(b, a)

            supabase.auth.signOut()
            requiresSignIn { repository.searchProfiles("discovery") }
            requiresSignIn { repository.searchProfiles("") }
            requiresSignIn { repository.getProfileSummary(a.userId) }
            // Also check the server boundary directly, without repository guards.
            try {
                supabase.postgrest.rpc("search_profiles", buildJsonObject {
                    put("search_query", a.username)
                })
                fail("Guests must not search profiles through the RPC.")
            } catch (error: PostgrestRestException) {
                assertEquals("42501", error.code)
            }
            try {
                supabase.postgrest.rpc("get_profile_summary", buildJsonObject {
                    put("profile_user_id", a.userId)
                })
                fail("Guests must not read profile summaries through the RPC.")
            } catch (error: PostgrestRestException) {
                assertEquals("42501", error.code)
            }
        } finally {
            supabase.auth.signOut()
        }
    }
}
