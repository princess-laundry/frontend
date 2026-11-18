package com.sidefocus.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

/**
 * 프론트 core의 기존 PostureEstimatorTest를 새 판정 규칙에 맞게 고친 버전입니다(프론트의 같은 파일을 이걸로 교체).
 * 바뀐 점: 앞쪽 방향을 코로 정하고, 머리 특징은 몸통 기준, 2초 창 70% 일치로 전환.
 */
class PostureEstimatorTest {
    /** 이미지 오른쪽을 보는 사람. headRatio·torsoDeg를 정확히 갖도록 만든 측면 몸(y 아래). */
    private fun body(headRatio: Double = 0.0, torsoDeg: Double = 0.0, length: Double = 200.0, facing: Int = 1, conf: Float = 0.9f): BodySide {
        val th = Math.toRadians(torsoDeg)
        val ux = sin(th); val uy = cos(th); val nx = cos(th); val ny = -sin(th)
        val sx = length * ux; val sy = length * uy
        val ex = sx + 0.42 * length * ux + headRatio * length * nx
        val ey = sy + 0.42 * length * uy + headRatio * length * ny
        fun img(x: Double, y: Double) = Point(240 + facing * x, 520 - y, conf)
        return BodySide(img(ex, ey), img(sx, sy), img(0.0, 0.0))
    }
    private fun nose(b: BodySide, facing: Int = 1) = Point(b.ear.x + facing * 40, b.ear.y, 0.9f)
    private fun observe(time: Long, b: BodySide, facing: Int = 1) =
        PoseObservation(time, true, right = if (facing > 0) b else null, left = if (facing < 0) b else null, nose = nose(b, facing))
    private fun run(estimator: PostureEstimator, from: Long, to: Long, b: BodySide, facing: Int = 1): PoseFeedback {
        var result = PoseFeedback()
        for (time in from..to step 200) result = estimator.update(observe(time, b, facing))
        return result
    }

    @Test fun `brief movement does not change confirmed posture`() {
        val estimator = PostureEstimator()
        run(estimator, 0, 3000, body())
        val result = run(estimator, 3200, 3600, body(headRatio = 0.6))
        assertEquals(Posture.NORMAL, result.state)
    }

    @Test fun `persistent head movement becomes FHP`() {
        val estimator = PostureEstimator()
        run(estimator, 0, 2200, body())
        assertEquals(Posture.NORMAL, run(estimator, 2400, 3200, body(headRatio = 0.5)).state)  // 1초로는 부족
        val result = run(estimator, 3400, 4600, body(headRatio = 0.5))
        assertEquals(Posture.FHP, result.state)
        assertEquals(true, result.headForward)
        assertEquals(false, result.torsoLean)
    }

    @Test fun `person missing and occluded person differ`() {
        val estimator = PostureEstimator()
        estimator.update(PoseObservation(0, false))
        assertEquals(Posture.AWAY, estimator.update(PoseObservation(2000, false)).state)
        assertEquals(Posture.UNKNOWN, estimator.update(PoseObservation(2200, true)).state)
    }

    @Test fun `short dropout keeps the confirmed posture`() {
        val estimator = PostureEstimator()
        run(estimator, 0, 3000, body())
        assertEquals(Posture.NORMAL, estimator.update(PoseObservation(3200, false)).state)
        assertEquals(Posture.AWAY, estimator.update(PoseObservation(5200, false)).state)
    }

    @Test fun `large distance change requests reframing`() {
        val estimator = PostureEstimator()
        run(estimator, 0, 1800, body())
        val result = run(estimator, 2000, 2800, body(length = 300.0))
        assertTrue(result.needsReset)
        assertFalse(result.framed)
    }

    @Test fun `setup countdown resets on lost framing`() {
        val gate = SetupGate()
        assertNull(gate.progress(true, 0).countdown)
        assertEquals(3, gate.progress(true, 3000).countdown)
        assertFalse(gate.progress(false, 5000).ready)
        assertFalse(gate.progress(true, 6000).ready)
        assertTrue(gate.progress(true, 12000).ready)
    }

    @Test fun `invalid server configuration cannot enter estimator`() {
        assertFailsWith<IllegalArgumentException> { Thresholds(headRatio = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Thresholds(analysisFps = 500) }
    }

    @Test fun `torso leaning is classified independently of head offset`() {
        val estimator = PostureEstimator()
        val result = run(estimator, 0, 3000, body(torsoDeg = 30.0))
        assertEquals(Posture.LEAN, result.state)
        assertEquals(0.0, result.headRatio!!, 1e-9)  // 몸통과 함께 숙인 머리는 전방머리가 아님
        assertEquals(false, result.headForward)
    }

    @Test fun `both attributes are reported and FHP wins the single state`() {
        val result = run(PostureEstimator(), 0, 3000, body(headRatio = 0.5, torsoDeg = 30.0))
        assertEquals(Posture.FHP, result.state)
        assertEquals(true, result.headForward)
        assertEquals(true, result.torsoLean)
    }

    @Test fun `leaning back is not leaning forward`() {
        val result = run(PostureEstimator(), 0, 3000, body(torsoDeg = -25.0))
        assertEquals(Posture.NORMAL, result.state)
        assertEquals(-25.0, result.leanDegrees!!, 1e-6)
    }

    @Test fun `left facing person gives the same features`() {
        val right = run(PostureEstimator(), 0, 3000, body(headRatio = 0.3, torsoDeg = 10.0))
        val left = run(PostureEstimator(), 0, 3000, body(headRatio = 0.3, torsoDeg = 10.0, facing = -1), facing = -1)
        assertEquals(1, right.facing)
        assertEquals(-1, left.facing)
        assertEquals(right.headRatio!!, left.headRatio!!, 1e-9)
        assertEquals(right.leanDegrees!!, left.leanDegrees!!, 1e-9)
    }

    @Test fun `device roll is corrected`() {
        val b = body(torsoDeg = 10.0)
        fun tilt(p: Point) = PostureFeatures.rotate(p, -8.0)
        val tilted = BodySide(tilt(b.ear), tilt(b.shoulder), tilt(b.hip))
        val estimator = PostureEstimator()
        var result = PoseFeedback()
        for (time in 0L..1000L step 200)
            result = estimator.update(PoseObservation(time, true, right = tilted, nose = tilt(nose(b)), deviceRollDeg = 8.0))
        assertEquals(10.0, result.leanDegrees!!, 1e-6)
    }

    @Test fun `calibration makes a habitually slouched person read as normal`() {
        val usual = body(headRatio = 0.40, torsoDeg = 22.0)   // 이 사람의 평소 바른 자세
        assertEquals(Posture.FHP, run(PostureEstimator(), 0, 3000, usual).state)  // 절대 기준이면 계속 경고

        val collector = CalibrationCollector()
        val estimator = PostureEstimator()
        var result = CalibrationResult()
        for (time in 0L..5000L step 200) result = collector.add(time, estimator.update(observe(time, usual)))
        assertEquals(CalibrationStatus.DONE, result.status)

        val calibrated = PostureEstimator(Thresholds(), result.calibration)
        val normal = run(calibrated, 0, 3000, usual)
        assertEquals(Posture.NORMAL, normal.state)
        assertTrue(normal.calibrated)
        assertEquals(Posture.FHP, run(calibrated, 3200, 6000, body(headRatio = 0.60, torsoDeg = 22.0)).state)
    }

    @Test fun `calibration rejects moving, hidden, and clearly slouched starts`() {
        fun calibrate(frame: (Long) -> PoseObservation): CalibrationStatus {
            val collector = CalibrationCollector()
            val estimator = PostureEstimator()
            var result = CalibrationResult()
            for (time in 0L..5000L step 200) result = collector.add(time, estimator.update(frame(time)))
            return result.status
        }
        assertEquals(CalibrationStatus.MOVING, calibrate { t -> observe(t, body(torsoDeg = if (t / 1000 % 2 == 0L) 0.0 else 25.0)) })
        assertEquals(CalibrationStatus.NOT_VISIBLE, calibrate { t -> PoseObservation(t, false) })
        assertEquals(CalibrationStatus.NOT_UPRIGHT, calibrate { t -> observe(t, body(headRatio = 0.7)) })
    }

    @Test fun `nonfinite landmarks cannot pass the setup gate`() {
        val estimator = PostureEstimator()
        val invalid = BodySide(Point(Double.NaN, 10.0), Point(20.0, 30.0), Point(20.0, 100.0))
        val feedback = estimator.update(PoseObservation(0, true, invalid))
        assertFalse(feedback.framed)
        assertEquals(Posture.UNKNOWN, feedback.state)
    }
}
