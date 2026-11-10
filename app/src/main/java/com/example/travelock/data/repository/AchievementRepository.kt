package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.LocationProgress
import com.example.travelock.data.model.UserAchievement
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class AchievementRepository {
    private val supabase = SupabaseProvider.client

    // Normally awarded by visit completion. This rechecks existing verified
    // progress after catalogue changes without allowing arbitrary client awards.
    suspend fun refreshMyAchievements() {
        signedInUserId()
        supabase.postgrest.rpc("refresh_my_achievements")
    }

    suspend fun getMyProgress(): List<LocationProgress> {
        signedInUserId()
        return supabase.postgrest.rpc("get_my_location_progress").decodeList<LocationProgress>()
    }

    suspend fun getMyAchievements(): List<UserAchievement> {
        val userId = signedInUserId()
        return supabase.from("user_achievements").select(
            Columns.raw("achievement_id,user_id,level,unlocked_at,location:locations(*)"),
        ) {
            filter { eq("user_id", userId) }
            order("unlocked_at", Order.DESCENDING)
            order("achievement_id", Order.DESCENDING)
        }.decodeList<UserAchievement>()
    }

    private suspend fun signedInUserId(): String {
        supabase.auth.awaitInitialization()
        return checkNotNull(supabase.auth.currentUserOrNull()) { "Sign in before loading achievements." }.id
    }
}
