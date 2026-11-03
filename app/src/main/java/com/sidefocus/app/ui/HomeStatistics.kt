package com.sidefocus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sidefocus.app.*
import com.sidefocus.app.data.SessionRow
import com.sidefocus.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: FocusViewModel, rows: List<SessionRow>) {
    val sessions = remember(rows) { rows.map { it.session() } }
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    val summary = dailySummary(sessions, today)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CenterFocusStrong, null, tint = Blue, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Text("사이드포커스", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(today.format(DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PageTitle("MY FOCUS", "바른 자세로,\n더 깊은 집중", "오늘도 나에게 편안한 공부를 시작해요.")
        FocusCard(color = Color(0xFFEAF0FF)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("오늘 쌓은 집중", style = MaterialTheme.typography.labelLarge, color = Blue)
                    Text(clock(summary.studyMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${summary.sessions.size}번의 공부 세션", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PostureRing(summary.normalRatio, summary.studyMs > 0, Modifier.size(120.dp))
            }
            Button(onClick = { vm.beginSetup() }, enabled = vm.ready,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(6.dp)); Text("집중 시작하기")
            }
        }
        FocusCard {
            Text("시작은 가볍게, 자세는 편안하게", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            listOf("01" to "스마트폰을 책상 옆 1~1.5m에 놓아요", "02" to "귀, 어깨, 골반이 보이도록 앉아요", "03" to "구도가 안정되면 자동으로 시작해요").forEach { (step, title) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(step, color = Blue, style = MaterialTheme.typography.labelMedium)
                    Text(title, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("최근 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.navigate(Screen.STATS) }) { Text("모두 보기"); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
        }
        if (rows.isEmpty()) FocusCard {
            Icon(Icons.Outlined.AutoGraph, null, tint = Blue)
            Text("첫 집중을 기록해 보세요", style = MaterialTheme.typography.titleSmall)
            Text("공부를 마치면 시간과 자세를 함께 돌아볼 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else rows.take(3).forEach { SessionItem(it) { vm.openReport(it.session()) } }
        TextButton(onClick = vm::startDemo, enabled = vm.ready, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.TouchApp, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("카메라 없이 데모 체험")
        }
        PrivacyNote()
    }
}

@Composable
fun SessionItem(row: SessionRow, onClick: () -> Unit) {
    val session = remember(row) { row.session() }
    val date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.of("Asia/Seoul"))
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(if (session.demo) Icons.Outlined.TouchApp else Icons.Outlined.School, null, tint = Blue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (session.demo) "앱 체험 기록" else "${date.format(DateTimeFormatter.ofPattern("M.d HH:mm"))}의 집중", style = MaterialTheme.typography.titleSmall)
                val status = when (row.syncStatus) { "SYNCED" -> "동기화 완료"; "DEMO" -> "통계 제외"; "ERROR" -> "전송 확인 필요"; else -> "기기에 저장됨" }
                Text("${duration(session.studyMs)} · $status${if (session.recovered) " · 중단 복구" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (session.studyMs > 0) percent(session.normalRatio) else "—", color = Teal, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatisticsScreen(vm: FocusViewModel, rows: List<SessionRow>) {
    val sessions = remember(rows) { rows.map { it.session() } }
    val selected = vm.selectedDate
    val summary = dailySummary(sessions, selected)
    val month = YearMonth.from(selected)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PageTitle("MY RECORD", "매일 조금 더 바르게", "공부 시간과 바른 자세의 비율을 함께 확인해요.")
        FocusCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.date(selected.minusMonths(1).withDayOfMonth(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "이전 달") }
                Text("${month.year}년 ${month.monthValue}월", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { vm.date(selected.plusMonths(1).withDayOfMonth(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "다음 달") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("월", "화", "수", "목", "금", "토", "일").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
            (0 until count step 7).forEach { start ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (start until start + 7).forEach { cell ->
                        val day = cell - offset + 1
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(38.dp)) else {
                            val date = month.atDay(day)
                            val daily = dailySummary(sessions, date)
                            val selectedDay = selected == date
                            val color = if (selectedDay) Blue else if (daily.studyMs > 0) Teal.copy(alpha = 0.08f + 0.5f * daily.normalRatio) else Color.Transparent
                            Box(Modifier.weight(1f).heightIn(min = 38.dp).background(color, RoundedCornerShape(10.dp)).clickable { vm.date(date) }, contentAlignment = Alignment.Center) {
                                Text(day.toString(), color = if (selectedDay) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Text("초록색이 짙을수록 바른 자세 비율이 높아요", style = MaterialTheme.typography.labelSmall, color = Teal)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(selected.format(DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.date(LocalDate.now(ZoneId.of("Asia/Seoul"))) }) { Text("오늘") }
        }
        FocusCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PostureRing(summary.normalRatio, summary.studyMs > 0)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Stat("공부 시간", duration(summary.studyMs)); Stat("집중 세션", "${summary.sessions.size}회") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PostureBreakdown(summary::duration)
        }
        if (summary.sessions.isEmpty()) Text("이날은 아직 기록이 없어요.\n홈에서 첫 집중을 시작해 보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        rows.filter { !it.session().demo && it.session().date == selected }.forEach { row -> SessionItem(row) { vm.openReport(row.session()) } }
        Text("자리비움·일시정지·인식 실패는 공부 시간에 포함하지 않아요. 자정을 넘긴 세션은 시작일(한국 시간)에 집계해요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrivacyNote()
    }
}
