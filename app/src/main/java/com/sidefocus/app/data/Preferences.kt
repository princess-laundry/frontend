package com.sidefocus.app.data

import android.content.Context
import com.sidefocus.core.Thresholds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

// Config v1 describes the old torso angle; v2 is the first pelvis-to-ear configuration.
internal const val MIN_AXIS_THRESHOLD_VERSION = 2

enum class AlertMode(val label: String) { SOUND("소리"), VIBRATION("진동"), OFF("끄기") }
data class Preferences(
    val alertMode: AlertMode = AlertMode.VIBRATION,
    val skeletonOnly: Boolean = true,
    val dimScreen: Boolean = true,
    val voiceGuide: Boolean = true,
    val endpoint: String = ""
)

class PreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    val deviceId: String get() = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        check(prefs.edit().putString("device_id", it).commit())
    }
    fun update(value: Preferences) {
        prefs.edit().putString("alert", value.alertMode.name).putBoolean("skeleton", value.skeletonOnly)
            .putBoolean("dim", value.dimScreen).putBoolean("voice", value.voiceGuide)
            .putString("endpoint", value.endpoint.trim()).apply()
        mutable.value = value.copy(endpoint = value.endpoint.trim())
    }
    fun thresholds(): Thresholds {
        val stored = prefs.getString("thresholds", null)?.let { json ->
            runCatching { AppJson.decodeFromString<Thresholds>(json) }.getOrNull()
        }
        return stored?.takeIf { it.version >= MIN_AXIS_THRESHOLD_VERSION }
            ?: Thresholds(version = MIN_AXIS_THRESHOLD_VERSION)
    }
    fun saveThresholds(value: Thresholds) { prefs.edit().putString("thresholds", AppJson.encodeToString(value)).apply() }
    fun reset() { prefs.edit().clear().apply(); mutable.value = Preferences() }
    private fun read() = Preferences(
        alertMode = runCatching { AlertMode.valueOf(prefs.getString("alert", "VIBRATION")!!) }.getOrDefault(AlertMode.VIBRATION),
        skeletonOnly = prefs.getBoolean("skeleton", true), dimScreen = prefs.getBoolean("dim", true),
        voiceGuide = prefs.getBoolean("voice", true), endpoint = prefs.getString("endpoint", "")!!
    )
}
