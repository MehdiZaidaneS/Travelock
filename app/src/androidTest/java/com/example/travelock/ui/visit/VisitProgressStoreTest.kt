package com.example.travelock.ui.visit

import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.VisitAttempt
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VisitProgressStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "visit_progress_test_${UUID.randomUUID()}"
    private val visit = VisitAttempt("visit-1", "user-1", "destination-1", Instant.EPOCH)
    private val pending = VisitProgress(visit, "user-1/visit-1/photo.jpg")

    @After
    fun cleanUp() {
        context.deleteSharedPreferences(name)
    }

    @Test
    fun anotherStoreInstanceLoadsTheSameVisitAndUploadedPath() = runBlocking {
        VisitProgressStore(context, name).save("user-1", pending)
        assertEquals(pending, VisitProgressStore(context, name).load("user-1"))
    }

    @Test
    fun scanCheckpointCanBeReopenedBeforeTakingAPhoto() = runBlocking {
        val scanned = VisitProgress(visit)
        VisitProgressStore(context, name).save("user-1", scanned)
        assertEquals(scanned, VisitProgressStore(context, name).load("user-1"))
    }

    @Test
    fun differentAccountCannotLoadOrOverwriteTheVisit() = runBlocking {
        val store = VisitProgressStore(context, name)
        store.save("user-1", pending)
        assertEquals(VisitProgress(), store.load("user-2"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.save("user-2", pending) }
        }
        assertEquals(pending, store.load("user-1"))
    }

    @Test
    fun cancelledProgressRemovesTheSavedRetry() = runBlocking {
        val store = VisitProgressStore(context, name)
        store.save("user-1", pending)
        store.save("user-1", pending.copy(visit = visit.copy(cancelledAt = Instant.now())))
        assertEquals(VisitProgress(), VisitProgressStore(context, name).load("user-1"))
    }

    @Test
    fun completionRemovesTheSavedRetry() = runBlocking {
        val store = VisitProgressStore(context, name)
        store.save("user-1", pending)
        store.save("user-1", pending.copy(completed = true))
        assertEquals(VisitProgress(), VisitProgressStore(context, name).load("user-1"))
    }

    @Test
    fun corruptDataAndWrongPhotoPathsCannotBecomeRetryState() = runBlocking<Unit> {
        val preferences = context.getSharedPreferences(name, 0)
        preferences.edit().putString("user-1", "broken json").commit()
        val store = VisitProgressStore(context, name)
        assertEquals(VisitProgress(), store.load("user-1"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.save("user-1", pending.copy(uploadedPhotoPath = "user-2/visit-1/photo.jpg")) }
        }
    }
}
