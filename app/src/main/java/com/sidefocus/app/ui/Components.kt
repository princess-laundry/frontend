package com.sidefocus.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidefocus.core.Posture
import java.util.Locale

val Blue = Color(0xFF3865E9)
val Teal = Color(0xFF008779)
val Amber = Color(0xFFD78B20)
val Coral = Color(0xFFE17463)
fun postureColor(state: Posture): Color = when (state) {
    Posture.NORMAL -> Teal; Posture.FHP -> Amber; Posture.LEAN -> Coral
    else -> Color(0xFF8B95A6)
}
fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}
fun duration(ms: Long): String = if (ms < 60_000) "${ms / 1000}초" else
    if (ms < 3_600_000) "${ms / 60_000}분" else "${ms / 3_600_000}시간 ${ms / 60_000 % 60}분"
fun percent(ratio: Float): String = "${(ratio * 100).toInt()}%"

@Composable
fun PageTitle(eyebrow: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow, color = Blue, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun FocusCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun PrivacyNote() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Shield, null, Modifier.size(14.dp), tint = Teal)
        Spacer(Modifier.width(6.dp))
        Text("영상은 저장하거나 전송하지 않아요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PostureRing(ratio: Float, hasData: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(142.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color(0xFFE9EEF8), -90f, 360f, false, size = Size(size.width, size.height), style = stroke)
            if (hasData) drawArc(Teal, -90f, ratio.coerceIn(0f, 1f) * 360f, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (hasData) percent(ratio) else "—", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("바른 자세", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PostureBreakdown(durationFor: (Posture) -> Long) {
    listOf(Posture.NORMAL, Posture.FHP, Posture.LEAN, Posture.AWAY).forEach { state ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(postureColor(state), RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Text(state.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(duration(durationFor(state)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
