package com.sidefocus.core

import kotlin.test.*

class PostureEstimatorTest {
    private fun body(headX: Double = 100.0, shoulderX: Double = 100.0) =
        BodySide(Point(headX, 30.0), Point(shoulderX, 100.0), Point(100.0, 300.0))
    @Test fun `brief movement does not change confirmed posture`() {
        val estimator = PostureEstimator()
        for (time in 0L..3000L step 200) estimator.update(PoseObservation(time, true, body()))
        val result = estimator.update(PoseObservation(3200, true, body(240.0)))
        assertEquals(Posture.NORMAL, result.state)
    }
    @Test fun `persistent head movement becomes FHP`() {
        val estimator = PostureEstimator()
        var result = PoseFeedback()
        for (time in 0L..3000L step 200) result = estimator.update(PoseObservation(time, true, body(200.0)))
        assertEquals(Posture.FHP, result.state)
    }
    @Test fun `person missing and occluded person differ`() {
        val estimator = PostureEstimator()
        estimator.update(PoseObservation(0, false))
        assertEquals(Posture.AWAY, estimator.update(PoseObservation(2000, false)).state)
        assertEquals(Posture.UNKNOWN, estimator.update(PoseObservation(2200, true)).state)
    }
    @Test fun `large distance change requests reframing`() {
        val estimator = PostureEstimator()
        estimator.update(PoseObservation(0, true, body()))
        val changed = BodySide(Point(10.0, 10.0), Point(10.0, 30.0), Point(10.0, 60.0))
        assertTrue(estimator.update(PoseObservation(200, true, changed)).needsReset)
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
        val leaning = body(headX = 190.0, shoulderX = 190.0)
        var feedback = PoseFeedback()
        for (time in 0L..3000L step 200) feedback = estimator.update(PoseObservation(time, true, leaning))
        assertEquals(Posture.LEAN, feedback.state)
        assertEquals(0.0, feedback.headRatio)
    }
    @Test fun `nonfinite landmarks cannot pass the setup gate`() {
        val estimator = PostureEstimator()
        val invalid = BodySide(Point(Double.NaN, 10.0), Point(20.0, 30.0), Point(20.0, 100.0))
        val feedback = estimator.update(PoseObservation(0, true, invalid))
        assertFalse(feedback.framed)
        assertEquals(Posture.UNKNOWN, feedback.state)
    }
}
