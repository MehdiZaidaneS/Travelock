package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserUnlock
import com.example.travelock.data.model.VisitAttempt
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Scanning starts a visit. The database awards an unlock after photo/location checks.
class UserUnlockRepository {
    private val supabase = SupabaseProvider.client

    suspend fun startVisit(qrCode: String): VisitAttempt {
        require(qrCode.isNotBlank()) { "QR code cannot be blank." }

        supabase.auth.awaitInitialization()
        checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before starting a visit."
        }

        // The session supplies ownership. A valid scan alone does not award an unlock.
        val parameters = buildJsonObject {
            put("scanned_qr_code", qrCode)
        }

        return supabase.postgrest.rpc("start_visit", parameters)
            .decodeAs<VisitAttempt>()
    }

    suspend fun completeVisit(
        visitId: String,
        photoPath: String,
        latitude: Double,
        longitude: Double,
    ): UserUnlock {
        require(visitId.isNotBlank()) { "Visit ID cannot be blank." }
        require(photoPath.isNotBlank()) { "An uploaded visit photo is required." }
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees." }

        supabase.auth.awaitInitialization()
        checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before completing a visit."
        }

        // Pass the Storage object path, not its URL. The server checks that it
        // belongs to this visit and that the reported location is within 50m.
        val parameters = buildJsonObject {
            put("attempt_id", visitId)
            put("uploaded_photo_path", photoPath)
            put("user_latitude", latitude)
            put("user_longitude", longitude)
        }

        return supabase.postgrest.rpc("complete_visit", parameters)
            .decodeAs<UserUnlock>()
    }

    suspend fun cancelVisit(visitId: String): VisitAttempt {
        require(visitId.isNotBlank()) { "Visit ID cannot be blank." }
        supabase.auth.awaitInitialization()
        checkNotNull(supabase.auth.currentUserOrNull()) { "You must sign in before cancelling a visit." }
        return supabase.postgrest.rpc("cancel_visit", buildJsonObject {
            put("attempt_id", visitId)
        }).decodeAs<VisitAttempt>()
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
