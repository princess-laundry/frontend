package com.sidefocus.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class Thresholds(
    val version: Int = 1,
    /** 서버 t1_ratio. 판정에는 쓰지 않음(서버·기존 코드 호환용). */
    val headRatio: Double = 0.35,
    /** 서버 t2_angle. 골반→귀 선 기울기 임계값(도). 1차 실험(3명) 기준 예비값. */
    val leanDegrees: Double = 15.0,
    val stableMs: Long = 2_000,
    val analysisFps: Int = 5,
    /** 서버 t2_delta. 캘리브레이션했을 때 기준 자세 대비 기울기 증가량 임계값(도). 1차 실험 기준 예비값. */
    val leanDeltaDegrees: Double = 9.5,
    /** 서버 t3_pitch(아직 없음). 고개 숙임(귀→코) 임계값(도). 1차 실험 기준 예비값. */
    val pitchDegrees: Double = 42.0,
    /** 서버 t3_delta(아직 없음). 캘리브레이션했을 때 기준 자세 대비 고개 숙임 증가량 임계값(도). */
    val pitchDeltaDegrees: Double = 15.0
) {
    init {
        require(version > 0 && headRatio in 0.05..2.0 && leanDegrees in 1.0..89.0)
        require(stableMs in 200..10_000 && analysisFps in 1..10)
        require(leanDeltaDegrees in 1.0..89.0 && pitchDegrees in 1.0..89.0 && pitchDeltaDegrees in 1.0..89.0)
    }
}

data class Point(val x: Double, val y: Double, val confidence: Float = 1f)
data class BodySide(val ear: Point, val shoulder: Point, val hip: Point) {
    val confidence: Float get() = minOf(ear.confidence, shoulder.confidence, hip.confidence)
    val valid: Boolean get() = listOf(ear, shoulder, hip).all { it.x.isFinite() && it.y.isFinite() }
}

/**
 * 판정기 입력(분석 프레임 하나). 좌표는 회전 보정된 분석 이미지의 픽셀 좌표.
 * @param nose MediaPipe 0번(코). 앞쪽 방향을 정하는 데 씀. 없으면 보이는 쪽으로 추정.
 * @param deviceRollDeg 이미지 좌표(y 아래)에서 중력 방향이 (sin r, cos r)일 때의 r. 없으면 보정 안 함.
 */
data class PoseObservation(
    val timestampMs: Long,
    val personDetected: Boolean,
    val left: BodySide? = null,
    val right: BodySide? = null,
    val nose: Point? = null,
    val deviceRollDeg: Double? = null
)

data class PoseFeedback(
    val state: Posture = Posture.UNKNOWN,
    /** 화면에 그릴 몸(원본 좌표). */
    val body: BodySide? = null,
    /** 몸통 기준 머리 전방 비율(스무딩, 참고용). 앞쪽이 +. */
    val headRatio: Double? = null,
    /** 골반→귀 선이 수직에서 앞으로 기운 각도(도, 스무딩). **판정에 쓰는 값.** 앞으로 기울면 +, 뒤로 기대면 −. */
    val leanDegrees: Double? = null,
    val needsReset: Boolean = false,
    val message: String = "귀, 어깨, 골반이 보이도록 앉아 주세요",
    /** 몸통 기준 목 각도(도, 참고용). */
    val neckAngle: Double? = null,
    /** 골반→어깨 몸통 각도(도, 참고용). 앞으로 숙이면 +. */
    val torsoAngle: Double? = null,
    /** +1: 이미지 오른쪽이 앞, −1: 왼쪽이 앞, 0: 아직 모름. */
    val facing: Int = 0,
    /** 개인 캘리브레이션 기준으로 판정했는지. */
    val calibrated: Boolean = false,
    /** 고개 숙임 각도(도, 스무딩). 판정에 쓰는 값 ②. 이번 프레임에 코가 안 보이면 null. */
    val headPitch: Double? = null
) {
    val framed: Boolean get() = body != null && !needsReset
}

/**
 * 측면 자세 판정기. ai 저장소 python/src/posture_ai/estimator.py 와 1:1로 대응하고,
 * ai/fixtures/golden 의 입력에 대해 같은 출력을 내는지 GoldenParityTest로 확인합니다.
 *
 * 1. 품질 필터·측면 선택 → 2. 앞쪽 방향 통일(코, 없으면 보이는 쪽, 세션 다수결) → 3. 거치 기울기 보정
 * → 4. 5프레임 중앙값 스무딩 → 5. ① 골반→귀 선 기울기 ② 고개 숙임(귀→코)
 * → 6. ① > leanDegrees 또는 ② > pitchDegrees면 거북목 후보(코가 안 보이면 ①만)
 * → 7. 최근 2초 창에서 70% 이상 일치하면 전환(히스테리시스) → NORMAL 또는 FHP.
 * 머리를 내밀어도, 허리를 숙여도, 허리는 펴고 고개만 숙여도 거북목(2026-11-18 팀 결정).
 *
 * @param calibration 있으면 각각 기준 자세 대비 증가량을 leanDeltaDegrees·pitchDeltaDegrees와 비교.
 *   지금 앱에서는 쓰지 않음(2026-11-18 결정). 다시 켤 때를 위해 남겨 둠.
 */
class PostureEstimator(
    private val thresholds: Thresholds = Thresholds(),
    private val calibration: Calibration? = null
) {
    private var current = Posture.UNKNOWN
    private var missingSince: Long? = null
    private var baselineLength: Double? = null
    private var baselineStart: Long? = null
    private val baselineSamples = ArrayList<Double>()
    private val ratios = ArrayDeque<Double>()
    private val angles = ArrayDeque<Double>()
    private val necks = ArrayDeque<Double>()
    private val axes = ArrayDeque<Double>()
    private val pitches = ArrayDeque<Double>()
    private val lengths = ArrayDeque<Double>()
    private val window = ArrayDeque<Pair<Long, Boolean>>()
    private var windowStart: Long? = null
    private var fhp: Boolean? = null
    private var noseVotes = 0.0
    private var sideVotes = 0.0

    val facing: Int get() = when {
        noseVotes != 0.0 -> if (noseVotes > 0) 1 else -1
        sideVotes != 0.0 -> if (sideVotes > 0) 1 else -1
        else -> 1
    }

    fun update(observation: PoseObservation): PoseFeedback {
        val (body, sideCode) = PostureFeatures.selectSide(observation.left, observation.right)
        if (body == null) {
            val since = missingSince ?: observation.timestampMs.also { missingSince = it }
            if (observation.timestampMs - since >= thresholds.stableMs) {
                current = if (observation.personDetected) Posture.UNKNOWN else Posture.AWAY
                clearTracking()
            }
            return PoseFeedback(state = current, message = if (observation.personDetected) "귀, 어깨, 골반이 가려졌어요" else "측면에 앉아 주세요")
        }
        missingSince = null

        val roll = observation.deviceRollDeg ?: 0.0
        val upright = PostureFeatures.rotate(body, roll)
        val nose = observation.nose?.let { PostureFeatures.rotate(it, roll) }
        val (noseVote, sideVote) = PostureFeatures.facingVote(upright, sideCode, nose)
        noseVotes += noseVote
        sideVotes += sideVote
        val facing = facing

        val raw = PostureFeatures.compute(upright, facing)
            ?: return PoseFeedback(facing = facing, message = "카메라 구도를 다시 맞춰 주세요")

        val ratio = smooth(ratios, raw.headRatio)
        val angle = smooth(angles, raw.torsoAngle)
        val neck = smooth(necks, raw.neckAngle)
        val axis = smooth(axes, raw.axisAngle)
        val pitch = PostureFeatures.headPitch(upright.ear, nose, facing)?.let { smooth(pitches, it) }
        val length = smooth(lengths, raw.length)

        val baseline = baselineLength
        if (baseline == null) {
            val start = baselineStart ?: observation.timestampMs.also { baselineStart = it }
            baselineSamples.add(raw.length)
            if (observation.timestampMs - start >= BASELINE_MS) baselineLength = PostureFeatures.upperMedian(baselineSamples)
        } else if (abs(length / baseline - 1.0) > RESET_TOLERANCE) {
            current = Posture.UNKNOWN
            clearTracking()
            return PoseFeedback(body = body, needsReset = true, facing = facing, message = "거리가 달라졌어요. 구도를 다시 맞춰 주세요")
        }

        val c = calibration
        val leaning = if (c != null) axis - c.axisAngle > thresholds.leanDeltaDegrees else axis > thresholds.leanDegrees
        val basePitch = c?.headPitch
        val bowing = when {
            pitch == null -> false
            basePitch != null -> pitch - basePitch > thresholds.pitchDeltaDegrees
            else -> pitch > thresholds.pitchDegrees
        }
        val fhpNow = leaning || bowing
        if (windowStart == null) windowStart = observation.timestampMs
        window.addLast(observation.timestampMs to fhpNow)
        while (window.isNotEmpty() && window.first().first <= observation.timestampMs - thresholds.stableMs) window.removeFirst()

        if (observation.timestampMs - windowStart!! >= thresholds.stableMs && window.isNotEmpty()) {
            fhp = decide(fhp, window.count { it.second }, window.size)
            current = if (fhp == true) Posture.FHP else Posture.NORMAL
        }

        val message = when (current) {
            Posture.NORMAL -> "좋아요. 편안하게 집중해 보세요"
            Posture.FHP -> "턱을 당기고 허리를 펴 보세요"
            Posture.AWAY -> "돌아오면 공부 시간을 자동으로 기록해요"
            else -> "자세가 안정될 때까지 잠시 기다려 주세요"
        }
        return PoseFeedback(current, body, ratio, axis, false, message, neck, angle, facing, calibration != null, pitch)
    }

    private fun clearTracking() {
        ratios.clear(); angles.clear(); necks.clear(); axes.clear(); pitches.clear(); lengths.clear()
        window.clear()
        windowStart = null
        fhp = null
    }

    private fun smooth(buffer: ArrayDeque<Double>, value: Double): Double {
        buffer.addLast(value)
        if (buffer.size > MEDIAN_FRAMES) buffer.removeFirst()
        return PostureFeatures.upperMedian(buffer)
    }

    private fun decide(previous: Boolean?, count: Int, total: Int): Boolean? = when {
        previous == null -> count * 2 >= total
        count * 100 >= AGREEMENT_PERCENT * total -> true
        (total - count) * 100 >= AGREEMENT_PERCENT * total -> false
        else -> previous
    }

    companion object {
        /** 스무딩에 쓰는 최근 프레임 수(5fps에서 약 1초). */
        const val MEDIAN_FRAMES = 5
        /** 2초 창 안에서 같은 판정이 이 비율(%) 이상이면 상태를 바꿈. */
        const val AGREEMENT_PERCENT = 70
        /** 어깨–골반 길이가 기준에서 ±30% 넘게 변하면 재세팅 안내. */
        const val RESET_TOLERANCE = 0.30
        /** 처음 1초 동안의 길이 중앙값을 기준 길이로 씀. */
        const val BASELINE_MS = 1_000L
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
