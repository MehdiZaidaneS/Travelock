package com.example.travelock.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserAchievement
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AchievementRepositoryIntegrationTest {
    // Completes missing backend fixtures in a small test location (max 10 destinations).
    // Keeps its visits/evidence/unlocks/achievement. No actual camera/GPS is exercised.
    @Test
    fun verifiedLocationAwardsOnceAndProgressMatchesTheCatalogue() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val email = argument("supabaseRegistrationEmail")
        val password = argument("supabaseRegistrationPassword")
        val destinationId = argument("supabaseTestDestinationId")
        val auth = AuthRepository()
        val repository = AchievementRepository()
        val supabase = SupabaseProvider.client
        suspend fun denied(call: suspend () -> Unit) {
            try { call(); fail("Expected permission rejection.") }
            catch (error: PostgrestRestException) {
                assertEquals("Expected permission rejection: ${error.message}", "42501", error.code)
            }
        }
        try {
            auth.logout()
            val user = auth.loginWithEmail(email, password)
            val seed = checkNotNull(DestinationRepository().getDestination(destinationId))
            val destinations = DestinationRepository().getDestinationsByLocation(seed.locationId)
            require(destinations.size in 1..10) { "Use a small dedicated test location with 1 to 10 destinations." }
            val before = repository.getMyProgress().single { it.locationId == seed.locationId }
            assertEquals(destinations.size.toLong(), before.totalDestinations)
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val bytes = try {
                bitmap.eraseColor(Color.BLUE)
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)); output.toByteArray()
                }
            } finally { bitmap.recycle() }
            destinations.forEach { CompletedVisitFixture.getOrCreate(it.destinationId, bytes) }
            // Check automatic awarding before explicitly requesting any refresh.
            val complete = repository.getMyProgress().single { it.locationId == seed.locationId }
            assertTrue(complete.isComplete && complete.isEarned)
            assertEquals(complete.totalDestinations, complete.verifiedDestinations)
            val award = repository.getMyAchievements().single { it.location.locationId == seed.locationId }
            assertEquals(user.userId, award.userId)
            assertEquals(1, award.level)
            assertEquals(award.achievementId, complete.achievementId)
            assertEquals(award.unlockedAt, complete.unlockedAt)
            assertEquals(checkNotNull(LocationRepository().getLocation(seed.locationId)), award.location)
            if (before.isEarned) {
                assertEquals(before.achievementId, complete.achievementId)
                assertEquals(before.unlockedAt, complete.unlockedAt)
            }
            val visit = CompletedVisitFixture.getOrCreate(seed.destinationId, bytes)
            UserUnlockRepository().completeVisit(visit.visitId, checkNotNull(visit.photoPath), seed.latitude, seed.longitude)
            repository.refreshMyAchievements()
            repository.refreshMyAchievements()
            assertEquals(award, repository.getMyAchievements().single { it.achievementId == award.achievementId })
            val raw = supabase.from("user_achievements").select(
                Columns.raw("achievement_id,user_id,level,unlocked_at,location:locations(*)"),
            ).decodeList<UserAchievement>()
            assertTrue(raw.all { it.userId == user.userId })
            denied { supabase.from("user_achievements").insert(buildJsonObject {
                put("user_id", user.userId); put("location_id", seed.locationId)
            }) }
            // Target the fixture so safe-update guards do not mask permission checks.
            denied { supabase.from("user_achievements").update(buildJsonObject { put("level", 1) }) {
                filter { eq("achievement_id", award.achievementId) }
            } }
            denied { supabase.from("user_achievements").delete {
                filter { eq("achievement_id", award.achievementId) }
            } }
            auth.logout()
            denied { supabase.postgrest.rpc("get_my_location_progress") }
            denied { supabase.postgrest.rpc("refresh_my_achievements") }
        } finally { auth.logout() }
    }
}
