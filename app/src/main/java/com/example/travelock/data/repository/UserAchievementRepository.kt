package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserAchievement
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException

/**
 * Queries the authenticated user's unlocked location achievements from Supabase.
 * Falls back to an empty list if the user_achievements table has not yet been created.
 */
class UserAchievementRepository {
    private val supabase = SupabaseProvider.client

    suspend fun getMyAchievements(): List<UserAchievement> {
        supabase.auth.awaitInitialization()
        val user = supabase.auth.currentUserOrNull() ?: return emptyList()
        return try {
            supabase.from("user_achievements")
                .select(Columns.raw("*,location:locations(*)")) {
                    filter {
                        eq("user_id", user.id)
                    }
                }
                .decodeList<UserAchievement>()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }
}
