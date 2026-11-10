package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.VisitAttempt
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

// Real backend fixture only. Synthetic JPEG and destination coordinates exercise
// server rules; they do not test live camera capture or on-site GPS presence.
internal object CompletedVisitFixture {
    suspend fun getOrCreate(destinationId: String, jpegBytes: ByteArray): VisitAttempt {
        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()
        val user = checkNotNull(supabase.auth.currentUserOrNull())
        val existing = supabase.from("visit_attempts").select {
            filter {
                eq("user_id", user.id)
                eq("destination_id", destinationId)
            }
        }.decodeList<VisitAttempt>().firstOrNull {
            it.completedAt != null && it.cancelledAt == null && it.photoPath != null
        }
        if (existing != null) return existing

        val destination = checkNotNull(DestinationRepository().getDestination(destinationId))
        val repository = UserUnlockRepository()
        val attempt = repository.startVisit(destination.qrCode)
        val path = VisitPhotoRepository().uploadPhoto(attempt, jpegBytes)
        repository.completeVisit(attempt.visitId, path, destination.latitude, destination.longitude)
        return checkNotNull(supabase.from("visit_attempts").select {
            filter { eq("visit_id", attempt.visitId) }
        }.decodeSingleOrNull<VisitAttempt>())
    }
}
