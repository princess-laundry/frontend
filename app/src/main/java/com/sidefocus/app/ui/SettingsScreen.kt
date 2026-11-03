package com.sidefocus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sidefocus.app.FocusViewModel
import com.sidefocus.app.data.AlertMode
import androidx.work.WorkInfo

@Composable
fun SettingsScreen(vm: FocusViewModel) {
    val prefs by vm.preferences.collectAsState()
    var delete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PageTitle("MY PREFERENCES", "나의 집중 방식", "편안한 환경을 직접 골라 보세요.")
        FocusCard {
            Text("자세가 달라지면 알려 주세요", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertMode.entries.forEach { mode -> FilterChip(prefs.alertMode == mode, { vm.settings(prefs.copy(alertMode = mode)) }, { Text(mode.label) }) }
            }
            Text("2초 이상 유지된 자세 변화에만 알려 드려요.\n알림 간격은 최소 30초예요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FocusCard {
            PreferenceToggle("스켈레톤만 표시", "실제 영상 대신 자세의 점과 선을 보여요", prefs.skeletonOnly) { vm.settings(prefs.copy(skeletonOnly = it)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceToggle("공부 중 화면 어둡게", "화면은 켜 두고 밝기를 낮춰요", prefs.dimScreen) { vm.settings(prefs.copy(dimScreen = it)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceToggle("시작 음성 안내", "세팅과 시작을 목소리로 알려 줘요", prefs.voiceGuide) { vm.settings(prefs.copy(voiceGuide = it)) }
        }
        FocusCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Shield, null, tint = Teal); Text("나의 영상은, 나의 기기에", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) }
            Text("카메라 영상과 관절 좌표는 메모리에서 분석한 뒤 버려요. 서버에는 시간과 자세 상태만 전송할 수 있어요. 계정 가입 없이 무작위 설치 ID를 사용해요.", style = MaterialTheme.typography.bodySmall)
            Text("이 앱의 자세 피드백은 일상 습관을 돌아보기 위한 안내예요. 의료 진단을 제공하지 않아요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ServerSettings(vm)
        OutlinedButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) { Text("기기의 모든 기록 삭제", color = MaterialTheme.colorScheme.error) }
        Text("사이드포커스 0.1.0 · Android\n실험 전 초기 판정 기준을 사용해요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrivacyNote()
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("기기 기록을 모두 삭제할까요?") },
        text = { Text("데모를 포함한 기기의 모든 기록을 삭제해요. 이 작업은 되돌릴 수 없어요. 서버 데이터는 별도로 삭제해야 해요.") },
        confirmButton = { TextButton(onClick = { delete = false; vm.deleteLocalData() }) { Text("모두 삭제") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("취소") } })
}

@Composable
private fun PreferenceToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ServerSettings(vm: FocusViewModel) {
    val prefs by vm.preferences.collectAsState()
    val rows by vm.rows.collectAsState()
    val work by vm.syncInfo.collectAsState()
    var endpoint by remember(prefs.endpoint) { mutableStateOf(prefs.endpoint) }
    var expanded by remember { mutableStateOf(false) }
    var deleteRemote by remember { mutableStateOf(false) }
    val last = work.lastOrNull()
    val working = last?.state in listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
    FocusCard {
        Text("오프라인으로도 기록해요", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("공부 기록은 먼저 기기에 저장돼요. 서버를 연결하면 시간과 자세 상태를 자동으로 전송해요.", style = MaterialTheme.typography.bodySmall)
        if (prefs.endpoint.isNotBlank()) {
            Text("연결됨 · ${rows.count { it.syncStatus == "PENDING" }}건 대기 · ${rows.count { it.syncStatus == "ERROR" }}건 확인 필요", style = MaterialTheme.typography.labelSmall, color = Teal)
            Text(when { last?.state == WorkInfo.State.FAILED -> "서버 응답을 확인해 주세요. 기록은 기기에 보존돼요."
                working -> "네트워크 연결 후 순서대로 전송해요. 실패하면 자동으로 다시 시도해요."
                else -> "마지막 전송 작업을 마쳤어요." }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::syncNow, enabled = !vm.connecting) { Text("지금 동기화") }
                TextButton(onClick = vm::disconnectServer, enabled = !vm.connecting) { Text("연결 해제") }
            }
        }
        TextButton(onClick = { expanded = !expanded }, enabled = !vm.connecting) { Text(if (expanded) "서버 설정 접기" else "개발용 서버 연결") }
        if (expanded) {
            OutlinedTextField(endpoint, { endpoint = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("HTTPS 서버 주소") }, placeholder = { Text("https://focus.example.com") }, enabled = !vm.connecting)
            Text("연결하면 이 기기에 쌓인 실제 기록을 모두 전송해요. 서버의 기기 인증과 설정 API가 준비되어 있어야 해요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { vm.connectServer(endpoint) }, enabled = endpoint.isNotBlank() && !vm.connecting) {
                if (vm.connecting) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (vm.connecting) "서버 확인 중" else "확인하고 연결")
            }
        }
        if (prefs.endpoint.isNotBlank()) TextButton(onClick = { deleteRemote = true }, enabled = !vm.connecting) { Text("서버와 기기 데이터 모두 삭제", color = MaterialTheme.colorScheme.error) }
    }
    if (deleteRemote) AlertDialog(onDismissRequest = { deleteRemote = false }, title = { Text("모든 데이터를 삭제할까요?") },
        text = { Text("서버의 본인 세션·통계·설정과 기기의 모든 기록을 삭제해요. 서버 삭제가 성공한 경우에만 기기 기록을 지워요. 되돌릴 수 없어요.") },
        confirmButton = { TextButton(onClick = { deleteRemote = false; vm.deleteRemoteAndLocalData() }) { Text("모두 삭제") } },
        dismissButton = { TextButton(onClick = { deleteRemote = false }) { Text("취소") } })
}
