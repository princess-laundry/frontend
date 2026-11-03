package com.sidefocus.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.*

class SessionTest {
    @Test fun `pause away and missing detection do not count as study`() {
        val recorder = SessionRecorder("id", 0, 1000)
        recorder.transition(Posture.NORMAL, 2000)
        recorder.transition(Posture.AWAY, 7000)
        recorder.pause(10000)
        recorder.resume(20000)
        recorder.transition(Posture.FHP, 21000)
        val result = recorder.snapshot(23000)
        assertEquals(7000L, result.studyMs)
        assertEquals(3000L, result.duration(Posture.AWAY))
        assertEquals(10000L, result.duration(Posture.PAUSED))
        assertEquals(2000L, result.duration(Posture.UNKNOWN))
        assertEquals(22000L, result.segments.sumOf { it.durationMs })
    }
    @Test fun `snapshots do not duplicate intervals`() {
        val recorder = SessionRecorder("id", 0, 0)
        recorder.transition(Posture.NORMAL, 0)
        recorder.snapshot(1000); recorder.snapshot(2000)
        assertEquals(listOf(Segment(Posture.NORMAL, 0, 3000)), recorder.snapshot(3000).segments)
    }
    @Test fun `time cannot go backwards`() {
        val recorder = SessionRecorder("id", 0, 0)
        recorder.snapshot(1000)
        assertFailsWith<IllegalArgumentException> { recorder.snapshot(999) }
    }
    @Test fun `daily ratio is weighted and demos are excluded`() {
        val start = Instant.parse("2026-11-02T16:00:00Z").toEpochMilli()
        fun session(id: String, state: Posture, ms: Long, demo: Boolean = false) =
            StudySession(id, start, start + ms, listOf(Segment(state, 0, ms)), demo = demo)
        val day = dailySummary(listOf(session("1", Posture.NORMAL, 3000), session("2", Posture.FHP, 1000),
            session("3", Posture.NORMAL, 9000, true)), LocalDate.of(2026, 11, 3))
        assertEquals(4000L, day.studyMs)
        assertEquals(0.75f, day.normalRatio)
        assertEquals(2, day.sessions.size)
    }
    @Test fun `empty day has finite ratio`() {
        assertEquals(0f, dailySummary(emptyList(), LocalDate.now()).normalRatio)
    }
}
