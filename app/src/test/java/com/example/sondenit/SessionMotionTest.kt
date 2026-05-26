package com.example.sondenit

import com.example.sondenit.data.SessionEvent
import com.example.sondenit.data.SessionStatsComputer
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMotionTest {
    @Test
    fun wakeMotionCountsAsInterruption() {
        val stats = SessionStatsComputer.compute(
            events = listOf(
                SessionEvent.SessionStart(0L),
                SessionEvent.Motion(
                    timestamp = 1_000L,
                    durationMs = 400L,
                    peakAcceleration = 0.3f,
                    avgAcceleration = 0.2f,
                    orientationChangeDeg = 4f,
                    wakeEvent = false,
                ),
                SessionEvent.Motion(
                    timestamp = 300_000L,
                    durationMs = 900L,
                    peakAcceleration = 1.6f,
                    avgAcceleration = 0.8f,
                    orientationChangeDeg = 30f,
                    wakeEvent = true,
                ),
                SessionEvent.SessionEnd(600_000L),
            ),
        )

        assertEquals(2, stats.movementEvents)
        assertEquals(1, stats.wakeMovementEvents)
        assertEquals(1, stats.interruptions)
    }
}
