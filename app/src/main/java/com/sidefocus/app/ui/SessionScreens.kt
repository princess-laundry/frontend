package com.sidefocus.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sidefocus.app.*
import com.sidefocus.app.camera.*
import com.sidefocus.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CameraPanel(vm: FocusViewModel, foreground: Boolean, modifier: Modifier = Modifier) {
    val prefs by vm.preferences.collectAsState()
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permission = granted }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
        if (vm.demo || permission) {
            CameraSurface(enabled = !vm.demo && foreground && !vm.paused && vm.cameraError == null,
                skeletonOnly = prefs.skeletonOnly, useFrontCamera = vm.frontCamera,
                intervalMs = if (vm.feedback.state == Posture.AWAY) 1000 else vm.analysisIntervalMs,
                feedback = vm.feedback, frameSize = vm.frameSize, onFrame = vm::frame, onError = vm::cameraFailure,
                modifier = Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Color(0xCC27364D), shape = RoundedCornerShape(8.dp)) {
                    Text(if (vm.demo) "DEMO · 실제 측정 아님" else "ON DEVICE", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                }
            }
            if (!vm.demo) Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                IconButton(onClick = { vm.settings(prefs.copy(skeletonOnly = !prefs.skeletonOnly)) }) {
                    Icon(if (prefs.skeletonOnly) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "영상과 스켈레톤 전환", tint = Color.White)
                }
                if (vm.screen == Screen.SETUP) IconButton(onClick = vm::switchCamera) { Icon(Icons.Outlined.Cameraswitch, "전면 후면 카메라 전환", tint = Color.White) }
            }
            if (vm.paused) Text("일시정지 · 카메라 꺼짐", Modifier.align(Alignment.Center).background(Color(0xDD17243A)).padding(16.dp), color = Color.White)
            if (vm.cameraError != null) Column(Modifier.align(Alignment.BottomCenter).background(Color(0xEE17243A)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.cameraError!!, color = Color.White, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = vm::retryCamera) { Text("카메라 다시 연결", color = Color(0xFF89AEFF)) }
            }
            if (vm.screen == Screen.SETUP && vm.setup.countdown != null) Text("${vm.setup.countdown}", Modifier.align(Alignment.Center), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (!vm.demo && vm.cameraError == null) Surface(Modifier.align(Alignment.BottomStart).padding(14.dp), color = Color(0xCC17243A), shape = RoundedCornerShape(8.dp)) {
                Text("영상 미저장 · 외부 전송 없음", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color(0xFFC9D5E8), style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Column(Modifier.fillMaxSize().background(Color(0xFFEAF0FF)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.CameraAlt, null, tint = Blue, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
                Text("자세 확인에 카메라가 필요해요", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("영상은 기기 안에서만 처리해요.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text("카메라 허용하기") }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("앱 설정에서 권한 변경") }
            }
        }
    }
}

@Composable
fun SetupScreen(vm: FocusViewModel, foreground: Boolean) {
    ObserveDeviceStability(vm::deviceStable)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = vm::cancelSetup, contentPadding = PaddingValues(0.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("돌아가기") }
        PageTitle("READY TO FOCUS", "나에게 맞는 구도 찾기", "책상 옆에 놓고, 평소 공부 자세로 앉아 주세요.")
        CameraPanel(vm, foreground, Modifier.height(320.dp))
        FocusCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (vm.feedback.framed && vm.stable) Icons.Outlined.CheckCircle else Icons.Outlined.CropFree, null, tint = Teal)
                Spacer(Modifier.width(10.dp))
                Text(if (vm.setup.countdown != null) "잠시 후 자동으로 시작해요" else "구도를 3초간 유지해 주세요", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { vm.setup.stability }, modifier = Modifier.fillMaxWidth(), color = Teal)
            Text(if (!vm.stable) "스마트폰을 수직으로 세워 단단히 고정해 주세요" else vm.feedback.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        listOf("거리 1~1.5m" to "화면에 귀, 어깨, 골반이 함께 나오게 놓아요.", "측면 90°" to "몸의 옆면을 바라보도록 책상 높이에 거치해요.", "안정적인 거치" to "밝은 곳에서 머리카락이나 옷의 가림을 줄여요.").forEach { (title, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, color = Blue); Text(body, style = MaterialTheme.typography.bodySmall) }
        }
        PrivacyNote()
    }
}

@Composable
fun SessionScreen(vm: FocusViewModel, foreground: Boolean, onFinish: () -> Unit) {
    val session = vm.snapshot ?: return
    ObserveDeviceStability(vm::deviceStable)
    val state = if (vm.paused) Posture.PAUSED else vm.feedback.state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (vm.demo) "앱 체험 중" else "지금, 집중하는 시간", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(color = postureColor(state).copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)) {
                Text(state.label, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = postureColor(state), style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("순수 공부 시간", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(clock(session.studyMs), fontSize = 46.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
            Text("전체 ${duration(session.endedAt - session.startedAt)} · 자리비움 ${duration(session.duration(Posture.AWAY))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CameraPanel(vm, foreground, Modifier.height(270.dp))
        FocusCard(color = postureColor(state).copy(alpha = 0.08f)) {
            Text(if (vm.paused) "잠깐 쉬어 가도 괜찮아요" else vm.feedback.message, style = MaterialTheme.typography.titleSmall, color = postureColor(state))
            Text(if (vm.paused) "준비되면 이어하기를 눌러 주세요." else "바른 자세 ${if (session.studyMs > 0) percent(session.normalRatio) else "—"} · 알림 ${session.alertCount}회", style = MaterialTheme.typography.bodySmall)
            if (vm.feedback.needsReset && !vm.demo) TextButton(onClick = vm::reframe) { Text("현재 구도로 다시 확인") }
        }
        if (vm.demo) {
            Text("상태를 눌러 기록 흐름을 확인하세요.\n데모 기록은 통계와 서버 전송에서 제외돼요.", style = MaterialTheme.typography.bodySmall, color = Blue)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Posture.NORMAL, Posture.FHP).forEach { p -> FilterChip(state == p, { vm.demoState(p) }, { Text(p.label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Posture.LEAN, Posture.AWAY).forEach { p -> FilterChip(state == p, { vm.demoState(p) }, { Text(p.label) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (vm.paused) vm.resume() else vm.pause() }, enabled = vm.cameraError == null,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(if (vm.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null); Spacer(Modifier.width(6.dp)); Text(if (vm.paused) "이어하기" else "잠깐 쉬기")
            }
            Button(onClick = onFinish, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("집중 마치기") }
        }
        Text("자세 판정은 실험 전 초기 기준이에요.\n인식이 불안정한 시간은 공부 시간에서 제외해요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrivacyNote()
    }
}

@Composable
fun ReportScreen(vm: FocusViewModel) {
    val session = vm.report ?: return
    var confirmDelete by remember(session.id) { mutableStateOf(false) }
    val started = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.of("Asia/Seoul"))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PageTitle(if (session.demo) "DEMO REPORT" else "SESSION REPORT", "차곡차곡 쌓인 집중", started.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm")))
        if (session.demo) Text("앱 체험 기록 · 실제 통계와 서버 전송에서 제외", color = Blue, style = MaterialTheme.typography.bodySmall)
        if (session.recovered) Text("앱이 종료되어 마지막 임시 저장 시점까지 복구한 기록이에요.", color = Amber, style = MaterialTheme.typography.bodySmall)
        FocusCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PostureRing(session.normalRatio, session.studyMs > 0)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { Stat("공부 시간", duration(session.studyMs)); Stat("자세 알림", "${session.alertCount}회") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PostureBreakdown(session::duration)
        }
        FocusCard {
            Text("나의 자세 흐름", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Canvas(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp))) {
                val elapsed = (session.endedAt - session.startedAt).coerceAtLeast(1)
                drawRect(Color(0xFFE7EBF2))
                session.segments.forEach { segment ->
                    drawRect(postureColor(segment.state), topLeft = Offset(segment.startMs.toFloat() / elapsed * size.width, 0f),
                        size = Size(segment.durationMs.toFloat() / elapsed * size.width, size.height))
                }
            }
            Text("회색은 자리비움·일시정지·인식 확인 시간이에요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row { Stat("일시정지", duration(session.duration(Posture.PAUSED)), Modifier.weight(1f)); Stat("인식 확인", duration(session.duration(Posture.UNKNOWN)), Modifier.weight(1f)) }
        }
        FocusCard(color = Color(0xFFE6F4EF)) {
            Text(if (session.normalRatio >= 0.7f) "편안한 자세를 잘 유지했어요" else "다음 집중도 가볍게 시작해요", color = Teal, fontWeight = FontWeight.Bold)
            Text("어깨의 힘을 풀고, 잠깐 스트레칭해 보세요.\n작은 휴식이 다음 집중을 도와줘요.", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { vm.navigate(Screen.HOME) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("홈으로 돌아가기") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = vm::retrySave) { Text("저장 다시 시도") }
            TextButton(onClick = { confirmDelete = true }) { Text("기기에서 기록 삭제", color = MaterialTheme.colorScheme.error) }
        }
        PrivacyNote()
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("기기의 이 기록을 삭제할까요?") },
        text = { Text("삭제한 로컬 기록은 되돌릴 수 없어요. 서버에 이미 전송한 기록은 서버 데이터 삭제를 이용해 주세요.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteSession(session.id) }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } })
}
