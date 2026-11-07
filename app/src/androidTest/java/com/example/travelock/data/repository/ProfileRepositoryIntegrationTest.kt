package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileRepositoryIntegrationTest {

    @Test
    fun createReadAndUpdateMyProfile() = runBlocking {
        // Credentials come from local test arguments.
        val arguments = InstrumentationRegistry.getArguments()
        val testEmail = requireNotNull(
            arguments.getString("supabaseTestEmail")
        ) { "Add the supabaseTestEmail test argument." }

        val testPassword = requireNotNull(
            arguments.getString("supabaseTestPassword")
        ) { "Add the supabaseTestPassword test argument." }

        val supabase = SupabaseProvider.client
        supabase.auth.awaitInitialization()

        try {
            supabase.auth.signInWith(Email) {
                email = testEmail
                password = testPassword
            }

            val user = checkNotNull(supabase.auth.currentUserOrNull())
            val repository = ProfileRepository()

            // Reuse the test profile on later runs.
            val profile = repository.getMyProfile()
                ?: repository.createMyProfile(
                    username = "test_${user.id.replace("-", "")}",
                    name = "Repository Test",
                )

            assertEquals(user.id, profile.userId)

            val updated = repository.updateMyProfile(
                username = profile.username,
                name = "Updated Repository Test",
                profileImage = profile.profileImage,
            )

            assertEquals("Updated Repository Test", updated.name)

            val fetched = checkNotNull(repository.getMyProfile())
            assertEquals(updated, fetched)
        } finally {
            supabase.auth.signOut()
        }
    }

}