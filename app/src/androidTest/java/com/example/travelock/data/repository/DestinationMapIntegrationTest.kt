package com.example.travelock.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.SupabaseProvider
import com.example.travelock.data.model.DestinationMapData
import com.example.travelock.data.model.DestinationMapStatus
import com.example.travelock.data.model.UserUnlock
import io.github.jan.supabase.postgrest.from
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DestinationMapIntegrationTest {
    // Reads existing catalogue/unlocks only. A/B should have the completed Oodi fixture.
    @Test
    fun showsPublicMarkersAndOnlyTheCurrentAccountsUnlockStatus() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun argument(name: String) = requireNotNull(args.getString(name)) { "Add local $name." }
        val auth = AuthRepository()
        val repository = DestinationMapRepository()
        suspend fun login(prefix: String) = auth.loginWithEmail(argument("${prefix}Email"),argument("${prefix}Password"))
        suspend fun verifyOwn(result: DestinationMapData, userId: String, fixtureId: String) {
            val ownIds = UserUnlockRepository().getMyUnlocks().map { it.destinationId }.toSet()
            assertEquals(userId,result.userId)
            assertTrue("Use the existing completed-visit fixture.",fixtureId in ownIds)
            assertEquals(DestinationMapStatus.UNLOCKED,
                result.destinations.single { it.destination.destinationId == fixtureId }.status)
            for (item in result.destinations) {
                assertEquals(if (item.destination.destinationId in ownIds) DestinationMapStatus.UNLOCKED else DestinationMapStatus.LOCKED,item.status)
            }
        }
        try {
            auth.logout()
            val fixture = checkNotNull(DestinationRepository().getDestination(argument("supabaseTestDestinationId")))
            val guest = repository.getMapData(fixture.locationId)
            assertEquals(null,guest.userId)
            assertEquals(fixture,guest.destinations.single { it.destination.destinationId==fixture.destinationId }.destination)
            assertTrue(guest.destinations.all { it.status==DestinationMapStatus.SIGN_IN_REQUIRED })
            assertTrue(guest.destinations.all { it.destination.locationId==fixture.locationId })
            assertTrue(repository.getMapData(UUID.randomUUID().toString()).destinations.isEmpty())
            val a = login("supabaseTest")
            verifyOwn(repository.getMapData(fixture.locationId),a.userId,fixture.destinationId)
            auth.logout()
            val b = login("supabaseSecondTest")
            assertTrue("Use different test accounts.",a.userId!=b.userId)
            verifyOwn(repository.getMapData(fixture.locationId),b.userId,fixture.destinationId)
            val other = SupabaseProvider.client.from("user_unlocks").select {
                filter { eq("user_id",a.userId) }
            }.decodeList<UserUnlock>()
            assertTrue(other.isEmpty())
            auth.logout()
            val signedOutAgain = repository.getMapData(fixture.locationId)
            assertEquals(null,signedOutAgain.userId)
            assertTrue(signedOutAgain.destinations.all { it.status==DestinationMapStatus.SIGN_IN_REQUIRED })
        } finally { auth.logout() }
    }
}
