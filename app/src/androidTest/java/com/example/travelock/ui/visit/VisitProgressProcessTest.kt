package com.example.travelock.ui.visit

import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelock.data.model.VisitAttempt
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class VisitProgressProcessTest {
    // Run once with visitCheckpointPhase=write, stop the app process, then run
    // again with visitCheckpointPhase=read. Uses isolated synthetic data only.
    @Test
    fun checkpointSurvivesProcessRestart() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("visitCheckpointPhase")
        assumeTrue(phase == "write" || phase == "read")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "visit_progress_process_test"
        val store = VisitProgressStore(context, name)
        val expected = VisitProgress(
            VisitAttempt("process-visit", "process-user", "process-destination", Instant.EPOCH),
            "process-user/process-visit/photo.jpg",
        )
        if (phase == "write") {
            store.save("process-user", expected)
            assertEquals(expected, store.load("process-user"))
        } else {
            try {
                assertEquals(expected, store.load("process-user"))
            } finally {
                context.deleteSharedPreferences(name)
            }
        }
    }
}
