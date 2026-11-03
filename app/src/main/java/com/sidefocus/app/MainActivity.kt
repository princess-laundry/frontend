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
import com.sidefocus.app.ui.FocusApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FocusTheme { FocusApp() } }
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

