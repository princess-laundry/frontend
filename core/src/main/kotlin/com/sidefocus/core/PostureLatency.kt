package com.sidefocus.core

import kotlin.math.ceil

/**
 * 실기기 처리 지연 측정 (발표 목표: 판정 1회 처리 p95 ≤ 200ms).
 *
 * 처리 지연 = 프레임 분석 시작(CameraSurface의 `now`, 곧 PoseObservation.timestampMs) → 판정기 결과가 나온 시각.
 * 비트맵 변환, MediaPipe 추론, 메인 스레드 전달, 판정기 계산이 모두 들어가요.
 *
 * 사용법(FocusViewModel):
 * ```
 * private val latency = LatencyMeter()
 * feedback = estimator.update(frame.observation)
 * latency.add(SystemClock.elapsedRealtime() - frame.observation.timestampMs, frame.observation.timestampMs)
 * // 세션이 끝날 때
 * Log.i("SideFocus", latency.summary().report())
 * ```
 */
class LatencyMeter(private val maxSamples: Int = 20_000) {
    private val samples = ArrayList<Long>()
    private var firstFrameMs: Long? = null
    private var lastFrameMs: Long? = null
    private var frames = 0

    /**
     * @param elapsedMs 이 프레임의 처리 지연(ms). 음수는 0으로 봄.
     * @param frameTimestampMs 프레임 분석 시작 시각. 주면 실제 분석 fps도 계산해요.
     */
    fun add(elapsedMs: Long, frameTimestampMs: Long? = null) {
        if (samples.size < maxSamples) samples.add(elapsedMs.coerceAtLeast(0))
        if (frameTimestampMs != null) {
            if (firstFrameMs == null) firstFrameMs = frameTimestampMs
            lastFrameMs = frameTimestampMs
            frames++
        }
    }

    fun reset() {
        samples.clear()
        firstFrameMs = null
        lastFrameMs = null
        frames = 0
    }

    fun summary(): LatencySummary {
        if (samples.isEmpty()) return LatencySummary()
        val sorted = samples.sorted()
        val first = firstFrameMs
        val last = lastFrameMs
        val fps = if (first != null && last != null && last > first && frames > 1) (frames - 1) * 1000.0 / (last - first) else null
        return LatencySummary(
            count = sorted.size,
            p50Ms = percentile(sorted, 0.50),
            p95Ms = percentile(sorted, 0.95),
            maxMs = sorted.last(),
            overTarget = sorted.count { it > TARGET_P95_MS },
            analysisFps = fps
        )
    }

    companion object {
        /** 발표 목표: 처리 지연 p95 ≤ 200ms. */
        const val TARGET_P95_MS = 200L

        /** nearest-rank 백분위수: 정렬한 값에서 ceil(p·n)번째. */
        fun percentile(sorted: List<Long>, p: Double): Long {
            require(sorted.isNotEmpty() && p in 0.0..1.0)
            val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}

data class LatencySummary(
    val count: Int = 0,
    val p50Ms: Long = 0,
    val p95Ms: Long = 0,
    val maxMs: Long = 0,
    /** 200ms를 넘은 프레임 수. */
    val overTarget: Int = 0,
    /** 실제 분석 fps(설정은 5fps). 프레임 시각을 안 넣었으면 null. */
    val analysisFps: Double? = null
) {
    val meetsTarget: Boolean get() = count > 0 && p95Ms <= LatencyMeter.TARGET_P95_MS

    /** Logcat·보고서에 그대로 붙일 한 줄. */
    fun report(): String {
        if (count == 0) return "처리 지연: 측정값 없음"
        val fps = analysisFps?.let { " · 분석 %.1ffps".format(it) } ?: ""
        val verdict = if (meetsTarget) "통과" else "미달"
        return "처리 지연 n=$count p50=${p50Ms}ms p95=${p95Ms}ms max=${maxMs}ms 200ms 초과 $overTarget 회$fps " +
            "(목표 p95 ≤ ${LatencyMeter.TARGET_P95_MS}ms: $verdict)"
    }
}
