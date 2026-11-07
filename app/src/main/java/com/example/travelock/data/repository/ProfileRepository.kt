package com.example.travelock.data.repository

import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Database errors are left for the caller to handle, such as a username already in use.
class ProfileRepository {
    private val supabase = SupabaseProvider.client

    // A signed-in user may not have completed their profile yet, so this can return null.
    suspend fun getMyProfile(): Profile? {
        // Let Auth finish loading a saved session before checking who is signed in.
        supabase.auth.awaitInitialization()

        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before loading your profile."
        }

        return supabase.from("profiles")
            .select {
                filter {
                    eq("user_id", user.id)
                }
            }
            .decodeSingleOrNull<Profile>()
    }

    // Creates a profile once; an existing profile or username will cause an error.
    suspend fun createMyProfile(
        username: String,
        name: String,
        profileImage: String = "",
    ): Profile {
        require(username.isNotBlank()) { "Username cannot be blank." }
        require(name.isNotBlank()) { "Name cannot be blank." }

        supabase.auth.awaitInitialization()

        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before creating your profile."
        }

        // Use the Auth account's ID. The database supplies created_at.
        val data = buildJsonObject {
            put("user_id", user.id)
            put("username", username.trim())
            put("name", name.trim())
            put("profile_image", profileImage)
        }

        return supabase.from("profiles")
            .insert(data) {
                select()
            }
            .decodeSingle<Profile>()
    }

    // Supply all three editable fields. Pass the current image to keep it, or "" to clear it.
    suspend fun updateMyProfile(
        username: String,
        name: String,
        profileImage: String,
    ): Profile {
        require(username.isNotBlank()) { "Username cannot be blank." }
        require(name.isNotBlank()) { "Name cannot be blank." }

        supabase.auth.awaitInitialization()

        val user = checkNotNull(supabase.auth.currentUserOrNull()) {
            "You must sign in before updating your profile."
        }

        val data = buildJsonObject {
            put("username", username.trim())
            put("name", name.trim())
            put("profile_image", profileImage)
        }

        // Target this user's row; RLS also checks ownership on the database side.
        return supabase.from("profiles")
            .update(data) {
                filter {
                    eq("user_id", user.id)
                }
                select()
            }
            .decodeSingle<Profile>()
    }
}
