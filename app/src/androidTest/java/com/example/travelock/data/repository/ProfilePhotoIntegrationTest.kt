package com.example.travelock.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilePhotoIntegrationTest {
    // Keeps two small uploads and makes the second A's current profile picture.
    // Existing A/B profiles are required; their names and other fields are kept.
    @Test
    fun uploadsReplacesAndSharesOnlyCurrentPicturesWithSignedInUsers() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val auth = AuthRepository()
        val profiles = ProfileRepository()
        val photos = ProfilePhotoRepository()
        val supabase = SupabaseProvider.client
        val bucket = supabase.storage.from("profile-photos")
        suspend fun login(prefix: String) = auth.loginWithEmail(
            argument("${prefix}Email"), argument("${prefix}Password"),
        )
        suspend fun freshPhoto(path: String) = bucket.downloadAuthenticated(path) {
            httpOverride {
                url.parameters.append("cacheNonce", UUID.randomUUID().toString())
                headers.append("Cache-Control", "no-cache, no-store")
            }
        }
        suspend fun deniedStorage(guest: Boolean = false, call: suspend () -> Unit) {
            try { call(); fail("Storage operation should be denied.") }
            catch (error: RestException) {
                assertTrue(error.message, error.statusCode in if (guest) setOf(400,401,403,404) else setOf(400,403,404))
            }
        }
        suspend fun invalidAttachment(path: String) {
            try { profiles.updateMyProfilePhoto(path); fail("Invalid attachment was saved.") }
            catch (error: PostgrestRestException) { assertEquals(error.message,"23514",error.code) }
        }
        fun jpeg(color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            return try {
                bitmap.eraseColor(color)
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,output))
                    output.toByteArray()
                }
            } finally { bitmap.recycle() }
        }
        try {
            auth.logout()
            val a = login("supabaseTest")
            val originalA = checkNotNull(profiles.getMyProfile()) { "Create A's test profile first." }
            val firstBytes = jpeg(Color.BLUE)
            val secondBytes = jpeg(Color.GREEN)
            val first = photos.uploadProfilePhoto(firstBytes)
            val second = photos.uploadProfilePhoto(secondBytes)
            assertArrayEquals(firstBytes,freshPhoto(first))
            assertArrayEquals(secondBytes,freshPhoto(second))
            val saved = profiles.updateMyProfilePhoto(first)
            assertEquals(originalA.copy(profileImage=first),saved)
            invalidAttachment("${a.userId}/${UUID.randomUUID()}.jpg")
            auth.logout()
            val b = login("supabaseSecondTest")
            assertTrue("Use different A/B accounts.",a.userId != b.userId)
            val originalB = checkNotNull(profiles.getMyProfile()) { "Create B's test profile first." }
            assertEquals(first,checkNotNull(profiles.getProfileSummary(a.userId)).profileImage)
            assertArrayEquals(firstBytes,freshPhoto(first))
            deniedStorage { freshPhoto(second) }
            invalidAttachment(first)
            assertEquals(originalB,profiles.getMyProfile())
            deniedStorage {
                bucket.upload("${a.userId}/${UUID.randomUUID()}.jpg",firstBytes) {
                    contentType=ContentType.Image.JPEG; upsert=false
                }
            }
            deniedStorage {
                bucket.upload(first,secondBytes) { contentType=ContentType.Image.JPEG; upsert=true }
            }
            // DELETE may return an empty result under RLS rather than an error.
            try { bucket.delete(first) } catch (error: RestException) {
                assertTrue(error.message,error.statusCode in setOf(400,403,404))
            }
            assertArrayEquals(firstBytes,freshPhoto(first))
            // The existing full-profile update must not bypass the attachment check.
            try {
                profiles.updateMyProfile(originalB.username,originalB.name,first)
                fail("Full profile update accepted somebody else's photo.")
            } catch (error: PostgrestRestException) { assertEquals("23514",error.code) }
            val changed = supabase.from("profiles").update(buildJsonObject { put("profile_image","") }) {
                filter { eq("user_id",a.userId) }; select()
            }.decodeList<com.example.travelock.data.model.Profile>()
            assertTrue(changed.isEmpty())
            auth.logout()
            login("supabaseTest")
            assertEquals(originalA.copy(profileImage=second),profiles.updateMyProfilePhoto(second))
            auth.logout()
            login("supabaseSecondTest")
            assertEquals(second,checkNotNull(profiles.getProfileSummary(a.userId)).profileImage)
            deniedStorage { freshPhoto(first) }
            assertArrayEquals(secondBytes,freshPhoto(second))
            auth.logout()
            login("supabaseTest")
            assertEquals(originalA.copy(profileImage=""),profiles.updateMyProfilePhoto(""))
            // Reattach the valid upload so the test leaves a usable current image.
            assertEquals(originalA.copy(profileImage=second),profiles.updateMyProfilePhoto(second))
            auth.logout()
            assertNull(auth.getCurrentUser())
            deniedStorage(guest=true) { freshPhoto(second) }
            deniedStorage(guest=true) {
                bucket.upload("${a.userId}/${UUID.randomUUID()}.jpg",firstBytes) {
                    contentType=ContentType.Image.JPEG; upsert=false
                }
            }
        } finally { auth.logout() }
    }
}
