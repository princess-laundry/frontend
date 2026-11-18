package com.sidefocus.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
enum class Posture(val label: String) {
    NORMAL("바른 자세"), FHP("거북목"), LEAN("상체 기울임"),
    AWAY("자리 비움"), UNKNOWN("자세 확인 중"), PAUSED("일시정지")
}

@Serializable
data class Segment(val state: Posture, val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0 && endMs >= startMs) }
    val durationMs: Long get() = endMs - startMs
}

@Serializable
data class StudySession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val segments: List<Segment>,
    val alertCount: Int = 0,
    val thresholdVersion: Int = 1,
    val demo: Boolean = false,
    val recovered: Boolean = false
) {
    fun duration(state: Posture): Long = segments.filter { it.state == state }.sumOf { it.durationMs }
    val studyMs: Long get() = duration(Posture.NORMAL) + duration(Posture.FHP) + duration(Posture.LEAN)
    val normalRatio: Float get() = if (studyMs == 0L) 0f else duration(Posture.NORMAL).toFloat() / studyMs
    val date: LocalDate get() = Instant.ofEpochMilli(startedAt).atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
}

/** All elapsed timing uses a monotonic clock; wall time is only used for dates. */
class SessionRecorder(
    private val id: String,
    private val wallStart: Long,
    private val monotonicStart: Long,
    private val demo: Boolean = false,
    private val thresholdVersion: Int = 1
) {
    private val segments = mutableListOf<Segment>()
    private var openState = Posture.UNKNOWN
    private var openOffset = 0L
    private var lastOffset = 0L
    private var alerts = 0
    val paused: Boolean get() = openState == Posture.PAUSED

    fun transition(state: Posture, now: Long) {
        val offset = checkedOffset(now)
        if (state == openState) return
        if (offset > openOffset) segments += Segment(openState, openOffset, offset)
        openState = state
        openOffset = offset
    }

    fun pause(now: Long) = transition(Posture.PAUSED, now)
    fun resume(now: Long) = transition(Posture.UNKNOWN, now)
    fun recordAlert() { alerts++ }

    fun snapshot(now: Long): StudySession {
        val offset = checkedOffset(now)
        val tail = if (offset > openOffset) listOf(Segment(openState, openOffset, offset)) else emptyList()
        return StudySession(id, wallStart, wallStart + offset, segments + tail, alerts, thresholdVersion, demo)
    }

    private fun checkedOffset(now: Long): Long {
        val offset = now - monotonicStart
        require(offset >= lastOffset) { "Monotonic time cannot move backwards" }
        lastOffset = offset
        return offset
    }
}

data class DailySummary(val date: LocalDate, val sessions: List<StudySession>) {
    val studyMs: Long get() = sessions.sumOf { it.studyMs }
    fun duration(state: Posture): Long = sessions.sumOf { it.duration(state) }
    val normalRatio: Float get() = if (studyMs == 0L) 0f else duration(Posture.NORMAL).toFloat() / studyMs
}

fun dailySummary(sessions: List<StudySession>, date: LocalDate) =
    DailySummary(date, sessions.filter { !it.demo && it.date == date })
