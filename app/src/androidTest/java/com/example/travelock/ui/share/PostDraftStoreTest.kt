package com.example.travelock.ui.share

import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.PostVisibility
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PostDraftStoreTest {
    @Test fun attemptedRequestIsPrivateToItsAccountAndVisitAndContainsNoPhotoBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "share-test-${UUID.randomUUID()}"
        val store = PostDraftStore(context, name)
        val draft = PostPublishDraft(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "A caption", PostVisibility.FRIENDS)
        try {
            store.save(draft)
            assertEquals(draft, PostDraftStore(context, name).load(draft.userId, draft.visitId))
            assertNull(store.load(UUID.randomUUID().toString(), draft.visitId))
            assertNull(store.load(draft.userId, UUID.randomUUID().toString()))
            val raw = context.getSharedPreferences(name, 0).all.values.single().toString()
            assertFalse(raw.contains("photoBytes")); assertFalse(raw.contains("Authorization"))
            store.clear(draft.userId, draft.visitId)
            assertNull(store.load(draft.userId, draft.visitId))
        } finally { context.getSharedPreferences(name, 0).edit().clear().commit() }
    }
}
