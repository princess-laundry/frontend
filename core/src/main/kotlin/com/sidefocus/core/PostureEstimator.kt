package com.sidefocus.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

@Serializable
data class Thresholds(
    val version: Int = 1,
    val headRatio: Double = 0.35,
    val leanDegrees: Double = 20.0,
    val stableMs: Long = 2_000,
    val analysisFps: Int = 5
) {
    init {
        require(version > 0 && headRatio in 0.05..2.0 && leanDegrees in 1.0..89.0)
        require(stableMs in 200..10_000 && analysisFps in 1..10)
    }
}

data class Point(val x: Double, val y: Double, val confidence: Float = 1f)
data class BodySide(val ear: Point, val shoulder: Point, val hip: Point) {
    val confidence: Float get() = minOf(ear.confidence, shoulder.confidence, hip.confidence)
}
data class PoseObservation(val timestampMs: Long, val personDetected: Boolean, val left: BodySide? = null, val right: BodySide? = null)
data class PoseFeedback(
    val state: Posture = Posture.UNKNOWN,
    val body: BodySide? = null,
    val headRatio: Double? = null,
    val leanDegrees: Double? = null,
    val needsReset: Boolean = false,
    val message: String = "귀, 어깨, 골반이 보이도록 앉아 주세요"
) {
    val framed: Boolean get() = body != null && !needsReset
}

/** Initial rule baseline; replace this adapter with validated AI outputs without changing UI or storage. */
class PostureEstimator(private val thresholds: Thresholds = Thresholds()) {
    private var current = Posture.UNKNOWN
    private var candidate = Posture.UNKNOWN
    private var candidateSince = 0L
    private var missingSince: Long? = null
    private var baselineLength: Double? = null
    private val ratios = ArrayDeque<Double>()
    private val angles = ArrayDeque<Double>()

    fun update(observation: PoseObservation): PoseFeedback {
        val body = listOfNotNull(observation.left, observation.right)
            .filter { it.confidence >= 0.5f }
            .maxByOrNull { (it.ear.confidence + it.shoulder.confidence + it.hip.confidence) / 3 }
        if (body == null) {
            val since = missingSince ?: observation.timestampMs.also { missingSince = it }
            if (observation.timestampMs - since >= thresholds.stableMs) {
                current = if (observation.personDetected) Posture.UNKNOWN else Posture.AWAY
                candidate = current
                ratios.clear(); angles.clear()
            }
            return PoseFeedback(state = current, message = if (observation.personDetected) "귀, 어깨, 골반이 가려졌어요" else "측면에 앉아 주세요")
        }
        missingSince = null
        val length = hypot(body.shoulder.x - body.hip.x, body.shoulder.y - body.hip.y)
        if (length < 0.001 || !length.isFinite()) return PoseFeedback(message = "카메라 구도를 다시 맞춰 주세요")
        val baseline = baselineLength ?: length.also { baselineLength = it }
        if (abs(length / baseline - 1) > 0.30) {
            current = Posture.UNKNOWN
            candidate = Posture.UNKNOWN
            ratios.clear(); angles.clear()
            return PoseFeedback(needsReset = true, message = "거리가 달라졌어요. 구도를 다시 맞춰 주세요")
        }
        val ratio = median(ratios, abs(body.ear.x - body.shoulder.x) / length)
        val angle = median(angles, Math.toDegrees(atan2(abs(body.shoulder.x - body.hip.x), body.hip.y - body.shoulder.y)))
        val raw = when {
            ratio > thresholds.headRatio -> Posture.FHP
            angle > thresholds.leanDegrees -> Posture.LEAN
            else -> Posture.NORMAL
        }
        if (candidate != raw) {
            candidate = raw
            candidateSince = observation.timestampMs
        }
        if (observation.timestampMs - candidateSince >= thresholds.stableMs) current = candidate
        val message = when (current) {
            Posture.NORMAL -> "좋아요. 편안하게 집중해 보세요"
            Posture.FHP -> "턱을 가볍게 당기고 시선을 높여 보세요"
            Posture.LEAN -> "허리를 펴고 상체를 세워 보세요"
            Posture.AWAY -> "돌아오면 공부 시간을 자동으로 기록해요"
            else -> "자세가 안정될 때까지 잠시 기다려 주세요"
        }
        return PoseFeedback(current, body, ratio, angle, message = message)
    }

    private fun median(window: ArrayDeque<Double>, value: Double): Double {
        window.addLast(value)
        if (window.size > 5) window.removeFirst()
        return window.sorted()[window.size / 2]
    }
}

class SetupGate {
    private var validSince: Long? = null
    fun reset() { validSince = null }
    /** 3 seconds of stable framing followed by a 3 second countdown. */
    fun progress(valid: Boolean, now: Long): SetupProgress {
        if (!valid) { reset(); return SetupProgress() }
        val since = validSince ?: now.also { validSince = it }
        val elapsed = (now - since).coerceAtLeast(0)
        return SetupProgress((elapsed / 3000f).coerceAtMost(1f),
            if (elapsed >= 3000) ((6000 - elapsed + 999) / 1000).toInt().coerceAtLeast(0) else null,
            elapsed >= 6000)
    }
}
data class SetupProgress(val stability: Float = 0f, val countdown: Int? = null, val ready: Boolean = false)
