package com.sidefocus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FocusTheme { FoundationScreen() } }
    }
}

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF3865E9), onPrimary = Color.White,
            secondary = Color(0xFF008779), background = Color(0xFFF6F8FC),
            surface = Color.White, onSurface = Color(0xFF192339),
            onSurfaceVariant = Color(0xFF6B7588), outlineVariant = Color(0xFFE5EAF3)
        ), content = content
    )
}

@Composable
private fun FoundationScreen() {
    Scaffold { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("사이드포커스", style = MaterialTheme.typography.headlineLarge)
            Text("바른 자세로, 더 깊은 집중", style = MaterialTheme.typography.titleMedium)
            Text("책상 옆에 스마트폰을 놓고 나의 공부 시간과 자세를 함께 기록하세요.")
            Text("영상은 저장하거나 전송하지 않아요.", color = MaterialTheme.colorScheme.secondary)
        }
    }
}
