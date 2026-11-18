package com.sidefocus.core

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * 개인 기준 자세: 바른 자세일 때 골반→귀 선 기울기(도)와 고개 숙임 각도(도, 코가 충분히 안 보였으면 null).
 * axisAngle은 서버 user_settings.calib_angle에 대응합니다. headPitch는 아직 저장할 칸이 없어요.
 * 2026-11-18 결정: 지금 앱에서는 캘리브레이션을 쓰지 않아요(다시 켤 때를 위해 남겨 둠).
 */
@Serializable
data class Calibration(val axisAngle: Double, val headPitch: Double? = null)

enum class CalibrationStatus(val message: String) {
    PENDING("바른 자세로 편하게 앉아 5초만 기다려 주세요"),
    DONE("기준 자세를 저장했어요"),
    NOT_VISIBLE("귀, 어깨, 골반이 잘 보이도록 다시 앉아 주세요"),
    MOVING("움직이지 말고 5초만 그대로 앉아 주세요"),
    NOT_UPRIGHT("허리를 펴고 턱을 당긴 바른 자세로 다시 해 주세요")
}

data class CalibrationResult(
    val status: CalibrationStatus = CalibrationStatus.PENDING,
    /** 0~1, 5초 중 진행 비율. */
    val progress: Double = 0.0,
    /** DONE이면 저장할 기준. NOT_UPRIGHT일 때도 참고용으로 담김(사용하지 말 것). */
    val calibration: Calibration? = null
) {
    val finished: Boolean get() = status != CalibrationStatus.PENDING
}

/**
 * 5초 개인 캘리브레이션. ai 저장소 python/src/posture_ai/calibration.py 와 1:1로 대응합니다.
 *
 * 사용법: 캘리브레이션 없이 만든 PostureEstimator의 출력을 프레임마다 add()에 넣고,
 * DONE이면 `PostureEstimator(thresholds, result.calibration)`으로 판정기를 다시 만듭니다.
 * 실패하면 status.message로 안내하고 새 CalibrationCollector로 다시 시작합니다.
 */
class CalibrationCollector(private val thresholds: Thresholds = Thresholds()) {
    private var start: Long? = null
    private var total = 0
    private val axes = ArrayList<Double>()
    private val pitches = ArrayList<Double>()
    private var result: CalibrationResult? = null

    fun add(timestampMs: Long, feedback: PoseFeedback): CalibrationResult {
        result?.let { return it }
        val begin = start ?: timestampMs.also { start = it }
        total++
        val axis = feedback.leanDegrees
        if (axis != null && !feedback.needsReset) {
            axes.add(axis)
            feedback.headPitch?.let { pitches.add(it) }
        }
        val elapsed = timestampMs - begin
        if (elapsed < CALIBRATION_MS) return CalibrationResult(CalibrationStatus.PENDING, elapsed.toDouble() / CALIBRATION_MS)
        return finish().also { result = it }
    }

    private fun finish(): CalibrationResult {
        val valid = axes.size
        if (valid < MIN_VALID_FRAMES || valid * 100 < MIN_VALID_PERCENT * total)
            return CalibrationResult(CalibrationStatus.NOT_VISIBLE, 1.0)
        val hasPitch = pitches.size >= MIN_VALID_FRAMES
        if (std(axes) > MAX_AXIS_STD || (hasPitch && std(pitches) > MAX_PITCH_STD))
            return CalibrationResult(CalibrationStatus.MOVING, 1.0)
        val calibration = Calibration(PostureFeatures.upperMedian(axes), if (hasPitch) PostureFeatures.upperMedian(pitches) else null)
        val basePitch = calibration.headPitch
        // 절대 임계값만으로 막으면 평소 자세가 기준선 근처인 사람은 캘리브레이션을 못 하므로 Δ만큼 여유를 둡니다.
        if (calibration.axisAngle > thresholds.leanDegrees + thresholds.leanDeltaDegrees ||
            (basePitch != null && basePitch > thresholds.pitchDegrees + thresholds.pitchDeltaDegrees))
            return CalibrationResult(CalibrationStatus.NOT_UPRIGHT, 1.0, calibration)
        return CalibrationResult(CalibrationStatus.DONE, 1.0, calibration)
    }

    private fun std(values: List<Double>): Double {
        var sum = 0.0
        for (v in values) sum += v
        val mean = sum / values.size
        var squares = 0.0
        for (v in values) squares += (v - mean) * (v - mean)
        return sqrt(squares / values.size)
    }

    companion object {
        const val CALIBRATION_MS = 5_000L
        const val MIN_VALID_PERCENT = 80
        const val MIN_VALID_FRAMES = 5
        const val MAX_AXIS_STD = 4.0
        const val MAX_PITCH_STD = 8.0
    }
}
