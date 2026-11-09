package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserUnlock
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

// Unlocks are awarded by the backend. This repository only reads them.
class UserUnlockRepository {
    private val supabase = SupabaseProvider.client

    suspend fun getMyUnlocks(): List<UserUnlock> {
        supabase.auth.awaitInitialization()

        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before loading your unlocks."
        }

        // RLS also enforces ownership, even if a request uses a different filter.
        return supabase.from("user_unlocks")
            .select {
                filter {
                    eq("user_id", user.id)
                }
            }
            .decodeList<UserUnlock>()
    }
}
