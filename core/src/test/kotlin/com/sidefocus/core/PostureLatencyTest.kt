package com.sidefocus.core

import kotlin.test.*

class PostureLatencyTest {
    @Test fun `percentile uses nearest rank`() {
        val values = (1L..100L).toList()
        assertEquals(50L, LatencyMeter.percentile(values, 0.50))
        assertEquals(95L, LatencyMeter.percentile(values, 0.95))
        assertEquals(1L, LatencyMeter.percentile(listOf(1L), 0.95))
    }

    @Test fun `summary reports p95, over-target count and analysis fps`() {
        val meter = LatencyMeter()
        // 5fps로 100프레임: 95개는 80ms, 5개는 250ms → p95 = 80ms(통과), 200ms 초과 5회
        for (i in 0 until 100) meter.add(if (i % 20 == 0) 250L else 80L, frameTimestampMs = i * 200L)
        val summary = meter.summary()
        assertEquals(100, summary.count)
        assertEquals(80L, summary.p95Ms)
        assertEquals(250L, summary.maxMs)
        assertEquals(5, summary.overTarget)
        assertEquals(5.0, summary.analysisFps!!, 1e-9)
        assertTrue(summary.meetsTarget)
        assertTrue(summary.report().contains("통과"))
    }

    @Test fun `slow device fails the target`() {
        val meter = LatencyMeter()
        repeat(100) { meter.add(if (it < 90) 120L else 260L) }
        val summary = meter.summary()
        assertEquals(260L, summary.p95Ms)
        assertFalse(summary.meetsTarget)
        assertNull(summary.analysisFps)
        assertTrue(summary.report().contains("미달"))
    }

    @Test fun `empty meter has no verdict`() {
        assertFalse(LatencyMeter().summary().meetsTarget)
        assertEquals("처리 지연: 측정값 없음", LatencyMeter().summary().report())
    }
}
