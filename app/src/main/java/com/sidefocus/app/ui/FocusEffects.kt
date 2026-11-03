package com.sidefocus.app.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.sidefocus.app.*
import com.sidefocus.app.data.AlertMode
import com.sidefocus.core.Posture
import java.util.Locale

@Composable
fun FocusEffects(vm: FocusViewModel, foreground: Boolean) {
    val context = LocalContext.current
    val preferences by vm.preferences.collectAsState()
    val active = vm.screen == Screen.SESSION && foreground && !vm.paused
    var lastAlert by remember { mutableLongStateOf(0L) }
    var speechReady by remember { mutableStateOf(false) }
    val speech = remember { TextToSpeech(context) { speechReady = it == TextToSpeech.SUCCESS } }
    DisposableEffect(speech) { onDispose { speech.stop(); speech.shutdown() } }
    LaunchedEffect(speechReady, preferences.voiceGuide, vm.screen, vm.setup.countdown, foreground) {
        if (speechReady && preferences.voiceGuide && foreground && !vm.demo) {
            speech.language = Locale.KOREAN
            val words = when (vm.screen) {
                Screen.SETUP -> vm.setup.countdown?.let { if (it > 0) it.toString() else null } ?: "스마트폰을 책상 옆에 놓고 귀, 어깨, 골반이 보이도록 앉아 주세요"
                Screen.SESSION -> "집중을 시작합니다"
                else -> null
            }
            words?.let { speech.speak(it, TextToSpeech.QUEUE_FLUSH, null, "focus-guide") }
        }
        if (!foreground) speech.stop()
    }
    DisposableEffect(active, preferences.dimScreen) {
        val window = (context as? Activity)?.window
        val previousBrightness = window?.attributes?.screenBrightness
        if (active) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (preferences.dimScreen) window?.attributes = window.attributes.apply { screenBrightness = 0.12f }
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (previousBrightness != null) window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
        }
    }
    LaunchedEffect(vm.feedback.state, active, preferences.alertMode) {
        val now = SystemClock.elapsedRealtime()
        if (active && vm.feedback.state in listOf(Posture.FHP, Posture.LEAN) && now - lastAlert >= 30_000 && preferences.alertMode != AlertMode.OFF) {
            when (preferences.alertMode) {
                AlertMode.SOUND -> {
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
                        try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180) }
                        finally { Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500) }
                    }
                }
                AlertMode.VIBRATION -> {
                    val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
                        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    vibrator.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                else -> Unit
            }
            lastAlert = now
            vm.alertSent()
        }
    }
}
