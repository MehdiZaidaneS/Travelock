package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.UserEnrolment
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class UserEnrolmentRepository {
    private val supabase = SupabaseProvider.client

    suspend fun enrolInDestination(destinationId: String): UserEnrolment {
        require(destinationId.isNotBlank()) { "Destination ID cannot be blank." }
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before enrolling in a destination."
        }
        val data = buildJsonObject {
            put("user_id", user.id)
            put("destination_id", destinationId)
        }
        try {
            return supabase.from("user_enrolments").insert(data) { select() }
                .decodeSingle<UserEnrolment>()
        } catch (error: PostgrestRestException) {
            if (error.code != "23505") throw error
            // Repeated taps or competing requests return the existing enrolment.
            // Avoid upsert: this table deliberately grants no UPDATE permission.
            return supabase.from("user_enrolments").select {
                filter {
                    eq("user_id", user.id)
                    eq("destination_id", destinationId)
                }
            }.decodeSingleOrNull<UserEnrolment>() ?: throw error
        }
    }

    // The foreign-key join loads destination details in this same request.
    suspend fun getMyEnrolmentsWithDestinations(): List<UserEnrolment> {
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before loading your enrolments."
        }
        return supabase.from("user_enrolments")
            .select(Columns.raw("*,destination:destinations(*)")) {
                filter { eq("user_id", user.id) }
            }.decodeList<UserEnrolment>()
    }

    suspend fun getMyEnrolments(): List<UserEnrolment> {
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before loading your enrolments."
        }
        return supabase.from("user_enrolments").select {
            filter { eq("user_id", user.id) }
        }.decodeList<UserEnrolment>()
    }
}
