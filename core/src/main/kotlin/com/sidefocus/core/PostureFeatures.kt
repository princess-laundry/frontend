package com.sidefocus.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 프레임 하나에서 특징값을 계산하는 순수 함수들.
 * ai 저장소 python/src/posture_ai/features.py 와 1:1로 대응합니다. 정의는 ai/docs/feature-spec.md.
 * 한쪽을 고치면 다른 쪽도 고치고 golden 파일을 다시 만들어야 합니다.
 */
object PostureFeatures {
    /** visibility·presence 중 작은 값이 이보다 낮은 점은 결측. */
    const val MIN_CONFIDENCE = 0.5f
    /** 코–귀 가로 거리가 어깨–골반 길이의 5%보다 작으면 방향 투표에 쓰지 않음. */
    const val NOSE_MIN_OFFSET = 0.05
    /** 코가 없을 때 '보이는 쪽'으로 추정한 방향의 투표 가중치(코 투표는 1). */
    const val SIDE_VOTE_WEIGHT = 0.25

    data class Features(
        /** (귀−어깨)의 몸통 축 수직 성분 ÷ 어깨–골반 길이. 앞쪽이 +. */
        val headRatio: Double,
        /** 골반→어깨 벡터와 수직선 사이 각도(도). 앞으로 숙이면 +. */
        val torsoAngle: Double,
        /** 어깨→귀 각도 − 몸통 각도(도). 몸통 기준 목 꺾임. */
        val neckAngle: Double,
        /** 어깨–골반 길이(픽셀). */
        val length: Double,
        /** 골반→귀 벡터와 수직선 사이 각도(도). 앞으로 기울면 +. 판정에 쓰는 값(2026-11-18 팀 결정). */
        val axisAngle: Double
    )

    /** 신뢰도 평균이 높은 쪽(같으면 왼쪽). 반환: 몸과 -1=왼쪽/+1=오른쪽/0=없음. */
    fun selectSide(left: BodySide?, right: BodySide?): Pair<BodySide?, Int> {
        var best: BodySide? = null
        var bestSide = 0
        for ((side, code) in listOf(left to -1, right to 1)) {
            if (side == null || !side.valid || side.confidence < MIN_CONFIDENCE) continue
            if (best == null || meanConfidence(side) > meanConfidence(best)) { best = side; bestSide = code }
        }
        return best to bestSide
    }

    fun meanConfidence(b: BodySide): Double =
        (b.ear.confidence.toDouble() + b.shoulder.confidence.toDouble() + b.hip.confidence.toDouble()) / 3

    /** 원점 기준 회전. 이미지 좌표(y 아래)에서 중력 방향이 (sin r, cos r)이면 r만큼 돌려 수직을 맞춤. */
    fun rotate(p: Point, degrees: Double): Point {
        if (degrees == 0.0) return p
        val r = Math.toRadians(degrees)
        val c = cos(r)
        val s = sin(r)
        return Point(p.x * c - p.y * s, p.x * s + p.y * c, p.confidence)
    }

    fun rotate(b: BodySide, degrees: Double) = BodySide(rotate(b.ear, degrees), rotate(b.shoulder, degrees), rotate(b.hip, degrees))

    /**
     * 이번 프레임의 방향 투표 (코 투표, 보이는 쪽 투표).
     * 코가 귀보다 이미지 오른쪽이면 +x가 앞. 코가 없으면 사람의 왼쪽 몸이 보일 때 앞은 -x
     * (앱의 분석 프레임은 전면 카메라라도 좌우 반전되지 않은 원본).
     */
    fun facingVote(body: BodySide, sideCode: Int, nose: Point?): Pair<Double, Double> {
        val length = hypot(body.shoulder.x - body.hip.x, body.shoulder.y - body.hip.y)
        var noseVote = 0.0
        if (nose != null && nose.confidence >= MIN_CONFIDENCE && nose.x.isFinite()) {
            val dx = nose.x - body.ear.x
            if (abs(dx) >= NOSE_MIN_OFFSET * length) noseVote = if (dx > 0) 1.0 else -1.0
        }
        return noseVote to sideCode.toDouble() * SIDE_VOTE_WEIGHT
    }

    /** facing: +1이면 이미지 +x가 앞. 길이가 0에 가까우면 null. */
    fun compute(body: BodySide, facing: Int): Features? {
        val tx = facing * (body.shoulder.x - body.hip.x)
        val tu = body.hip.y - body.shoulder.y
        val length = hypot(tx, tu)
        if (!length.isFinite() || length < 1e-3) return null
        val torsoAngle = Math.toDegrees(atan2(tx, tu))
        val hx = facing * (body.ear.x - body.shoulder.x)
        val hu = body.shoulder.y - body.ear.y
        val headRatio = (hx * tu - hu * tx) / (length * length)
        val neckAngle = wrapDegrees(Math.toDegrees(atan2(hx, hu)) - torsoAngle)
        val axisAngle = Math.toDegrees(atan2(facing * (body.ear.x - body.hip.x), body.hip.y - body.ear.y))
        return Features(headRatio, torsoAngle, neckAngle, length, axisAngle)
    }

    /**
     * 귀→코 선이 수평에서 아래로 숙인 각도(도). 고개를 숙이면 커짐. 코가 없거나 신뢰도가 낮으면 null.
     * 골반→귀 기울기로는 '허리는 펴고 고개만 숙인 자세'가 잘 안 잡혀서 함께 봄(2026-11-18 팀 결정).
     */
    fun headPitch(ear: Point, nose: Point?, facing: Int): Double? {
        if (nose == null || nose.confidence < MIN_CONFIDENCE || !nose.x.isFinite() || !nose.y.isFinite()) return null
        val forward = facing * (nose.x - ear.x)
        val down = nose.y - ear.y
        if (hypot(forward, down) < 1e-3) return null
        return Math.toDegrees(atan2(down, forward))
    }

    fun wrapDegrees(value: Double): Double {
        var v = value
        while (v > 180.0) v -= 360.0
        while (v <= -180.0) v += 360.0
        return v
    }

    /** 정렬 후 size/2 번째 값(짝수 개면 위쪽 중앙값). */
    fun upperMedian(values: Collection<Double>): Double = values.sorted()[values.size / 2]
}
