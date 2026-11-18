package com.sidefocus.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun normalizeEndpoint(value: String, allowUsbLoopback: Boolean = false): String {
    val uri = URI(value.trim())
    val usbLoopback = allowUsbLoopback && uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535
    require((uri.scheme == "https" || usbLoopback) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        if (allowUsbLoopback) "HTTPS 서버 주소 또는 USB 테스트 주소를 입력해 주세요" else "HTTPS 서버 주소를 입력해 주세요"
    }
    require(uri.path.orEmpty().trimEnd('/') in listOf("", "/api/v1")) { "서버의 기본 주소 또는 /api/v1 주소를 입력해 주세요" }
    return URI(uri.scheme, null, uri.host, uri.port, "/api/v1/", null, null).toASCIIString()
}

@Serializable
data class SessionUpload(
    @SerialName("client_session_id") val clientSessionId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("threshold_version") val thresholdVersion: Int,
    @SerialName("app_version") val appVersion: String,
    val summary: UploadSummary,
    val segments: List<UploadSegment>
)
@Serializable
data class UploadSummary(
    @SerialName("study_sec") val studySec: Long,
    @SerialName("away_sec") val awaySec: Long,
    @SerialName("normal_sec") val normalSec: Long,
    @SerialName("fhp_sec") val fhpSec: Long,
    @SerialName("lean_sec") val leanSec: Long,
    @SerialName("alert_count") val alertCount: Int
)
@Serializable
data class UploadSegment(val state: String, @SerialName("start_ms") val startMs: Long, @SerialName("end_ms") val endMs: Long)

fun StudySession.upload(appVersion: String): SessionUpload {
    require(!demo) { "Demo sessions must never be uploaded" }
    val normal = duration(Posture.NORMAL) / 1000
    val fhp = duration(Posture.FHP) / 1000
    val lean = duration(Posture.LEAN) / 1000
    fun iso(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    return SessionUpload(id, iso(startedAt), iso(endedAt), thresholdVersion, appVersion,
        UploadSummary(normal + fhp + lean, duration(Posture.AWAY) / 1000, normal, fhp, lean, alertCount),
        segments.filter { it.state in setOf(Posture.NORMAL, Posture.FHP, Posture.LEAN, Posture.AWAY) }
            .map { UploadSegment(it.state.name, it.startMs, it.endMs) })
}
