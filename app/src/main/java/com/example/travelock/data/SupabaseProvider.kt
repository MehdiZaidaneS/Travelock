package com.example.travelock.data

import com.example.travelock.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage

object SupabaseProvider {
    val client by lazy {
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties, then rebuild."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Postgrest)
            install(Auth)
            install(Storage)
        }
    }
}
