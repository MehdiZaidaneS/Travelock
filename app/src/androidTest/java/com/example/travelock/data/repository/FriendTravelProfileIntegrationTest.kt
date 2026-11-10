package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendTravelProfileIntegrationTest {
    // Read-only: A/B must be accepted friends; registration account must be unrelated.
    @Test
    fun acceptedFriendsSeeTravelSummariesWhileOthersAreDenied() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val auth = AuthRepository()
        val profiles = ProfileRepository()
        val supabase = SupabaseProvider.client
        suspend fun login(prefix: String) = auth.loginWithEmail(
            argument("${prefix}Email"), argument("${prefix}Password"),
        )
        suspend fun denied(target: String) {
            try {
                supabase.postgrest.rpc("get_friend_travel_profile", buildJsonObject {
                    put("profile_user_id", target)
                })
                fail("Expected travel history permission rejection.")
            } catch (error: PostgrestRestException) {
                assertEquals(error.message, "42501", error.code)
            }
        }
        try {
            auth.logout()
            val a = login("supabaseTest")
            val profileA = checkNotNull(profiles.getProfileSummary(a.userId))
            val achievementsA = AchievementRepository().getMyAchievements()
            val unlocksA = UserUnlockRepository().getMyUnlocksWithDestinations()
            assertTrue("Use A's existing verified visit fixture.", unlocksA.isNotEmpty())
            assertTrue("Apply achievement migration/backfill first.", achievementsA.isNotEmpty())
            auth.logout()
            val b = login("supabaseSecondTest")
            val profileB = checkNotNull(profiles.getProfileSummary(b.userId))
            val achievementsB = AchievementRepository().getMyAchievements()
            val unlocksB = UserUnlockRepository().getMyUnlocksWithDestinations()
            val viewedA = checkNotNull(profiles.getFriendTravelProfile(a.userId))
            assertEquals(profileA, viewedA.profile)
            assertEquals(achievementsA, viewedA.achievements)
            assertEquals(unlocksA.sortedBy { it.unlockId }, viewedA.unlocks.sortedBy { it.unlockId })
            // The summary RPC must not widen direct access to private tables.
            for (table in listOf("user_achievements", "user_unlocks", "visit_attempts")) {
                val rows = supabase.from(table).select {
                    filter { eq("user_id", a.userId) }
                }.decodeList<kotlinx.serialization.json.JsonObject>()
                assertTrue("Direct $table access leaked A's rows.", rows.isEmpty())
            }
            auth.logout()
            login("supabaseTest")
            val viewedB = checkNotNull(profiles.getFriendTravelProfile(b.userId))
            assertEquals(profileB, viewedB.profile)
            assertEquals(achievementsB, viewedB.achievements)
            assertEquals(unlocksB.sortedBy { it.unlockId }, viewedB.unlocks.sortedBy { it.unlockId })
            denied(a.userId) // Own history uses the existing own-profile APIs.
            auth.logout()
            val unrelated = login("supabaseRegistration")
            assertTrue(unrelated.userId != a.userId && unrelated.userId != b.userId)
            denied(a.userId)
            denied(b.userId)
            auth.logout()
            denied(a.userId)
        } finally {
            auth.logout()
        }
    }
}
