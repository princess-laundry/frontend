package com.sidefocus.core

import kotlinx.serialization.json.Json
import kotlin.test.*

class ApiContractTest {
    @Test fun `only explicit HTTPS endpoints are accepted`() {
        assertEquals("https://focus.example/api/v1/", normalizeEndpoint("https://focus.example/"))
        assertEquals("https://focus.example:8443/api/v1/", normalizeEndpoint("https://focus.example:8443/api/v1"))
        listOf("http://example.com", "https://user:pass@example.com", "https://example.com/other", "https://example.com?token=secret").forEach {
            assertFailsWith<IllegalArgumentException> { normalizeEndpoint(it) }
        }
    }
    @Test fun `USB mode allows HTTP only on the explicit loopback address and port`() {
        val usbEndpoint = "http://127.0.0.1:8080"
        assertEquals("$usbEndpoint/api/v1/", normalizeEndpoint(usbEndpoint, allowUsbLoopback = true))
        assertFailsWith<IllegalArgumentException> { normalizeEndpoint(usbEndpoint) }
        for (invalid in listOf("http://127.0.0.1", "http://127.0.0.1:99999", "http://192.168.1.2:8080", "http://127.0.0.1.example.com:8080", "http://user@127.0.0.1:8080")) {
            assertFailsWith<IllegalArgumentException> { normalizeEndpoint(invalid, allowUsbLoopback = true) }
        }
    }
    @Test fun `upload excludes local states and contains only numeric timing data`() {
        val session = StudySession("uuid", 0, 5000, listOf(Segment(Posture.NORMAL, 0, 1100), Segment(Posture.PAUSED, 1100, 2100),
            Segment(Posture.UNKNOWN, 2100, 3100), Segment(Posture.FHP, 3100, 5000)))
        val upload = session.upload("0.1")
        assertEquals(2, upload.segments.size)
        assertEquals(upload.summary.normalSec + upload.summary.fhpSec + upload.summary.leanSec, upload.summary.studySec)
        assertTrue(upload.startedAt.endsWith("+09:00"))
        assertEquals(upload, Json.decodeFromString<SessionUpload>(Json.encodeToString(upload)))
    }
    @Test fun `demo uploads are rejected`() {
        assertFailsWith<IllegalArgumentException> { StudySession("id", 0, 0, emptyList(), demo = true).upload("0.1") }
    }
}
