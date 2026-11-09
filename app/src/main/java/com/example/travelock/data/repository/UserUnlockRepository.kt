package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserUnlock
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The database validates QR codes and awards unlocks for the signed-in user.
class UserUnlockRepository {
    private val supabase = SupabaseProvider.client

    suspend fun unlockDestination(qrCode: String): UserUnlock {
        require(qrCode.isNotBlank()) { "QR code cannot be blank." }

        supabase.auth.awaitInitialization()
        checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before unlocking a destination."
        }

        // Send only the scanned value. The function determines who gets the unlock
        // and returns the original unlock if this destination was already scanned.
        val parameters = buildJsonObject {
            put("scanned_qr_code", qrCode)
        }

        return supabase.postgrest.rpc("unlock_destination", parameters)
            .decodeAs<UserUnlock>()
    }

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
