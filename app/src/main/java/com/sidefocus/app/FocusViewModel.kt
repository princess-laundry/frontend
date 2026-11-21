package com.sidefocus.app

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sidefocus.app.camera.CameraFrame
import com.sidefocus.app.data.*
import com.sidefocus.app.sync.*
import androidx.work.WorkManager
import com.sidefocus.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class Screen { HOME, SETUP, SESSION, REPORT, STATS, SETTINGS }

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FocusApplication
    val preferences = app.preferences.state
    val rows = app.store.rows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var screen by mutableStateOf(Screen.HOME); private set
    var ready by mutableStateOf(false); private set
    var demo by mutableStateOf(false); private set
    var feedback by mutableStateOf(PoseFeedback()); private set
    var setup by mutableStateOf(SetupProgress()); private set
    var frameSize by mutableStateOf(480 to 640); private set
    var stable by mutableStateOf(true); private set
    var frontCamera by mutableStateOf(false); private set
    var cameraError by mutableStateOf<String?>(null); private set
    var snapshot by mutableStateOf<StudySession?>(null); private set
    var report by mutableStateOf<StudySession?>(null); private set
    var selectedDate by mutableStateOf(LocalDate.now(ZoneId.of("Asia/Seoul"))); private set
    var message by mutableStateOf<String?>(null); private set
    var connecting by mutableStateOf(false); private set
    val syncInfo = WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(SYNC_WORK)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var sessionThresholds = app.preferences.thresholds()
    private var estimator = PostureEstimator(app.preferences.thresholds())
    private val latency = LatencyMeter()
    private val setupGate = SetupGate()
    private var recorder: SessionRecorder? = null
    private var backgrounded = false
    private var lastFrameAt = 0L
    private var lastCheckpoint = 0L
    private var checkpointJob: Job? = null
    private val saveMutex = kotlinx.coroutines.sync.Mutex()
    val paused: Boolean get() = recorder?.paused == true
    val analysisIntervalMs: Long get() = 1000L / sessionThresholds.analysisFps

    init {
        viewModelScope.launch {
            runCatching { app.store.recoverInterrupted() }.onFailure { message = "저장한 기록을 불러오지 못했어요." }
            ready = true
            scheduleSync(app)
            while (isActive) {
                delay(250)
                updateSessionClock()
            }
        }
    }

    private fun updateSessionClock() {
        val active = recorder ?: return
        val now = SystemClock.elapsedRealtime()
        if (!active.paused && now - lastFrameAt > 3000) {
            feedback = PoseFeedback(message = "카메라 입력을 기다리고 있어요")
            active.transition(Posture.UNKNOWN, now)
        }
        snapshot = active.snapshot(now)
        if (now - lastCheckpoint >= 5000) checkpoint(active = true)
    }

    fun navigate(target: Screen) { if (recorder == null) screen = target }
    fun date(value: LocalDate) { selectedDate = value }
    fun settings(value: Preferences) { app.preferences.update(value) }
    fun clearMessage() { message = null }

    fun beginSetup(isDemo: Boolean = false) {
        if (!ready || recorder != null) return
        demo = isDemo
        sessionThresholds = app.preferences.thresholds()
        estimator = PostureEstimator(sessionThresholds)
        feedback = PoseFeedback()
        cameraError = null
        setupGate.reset(); setup = SetupProgress()
        screen = Screen.SETUP
    }

    fun cancelSetup() { if (recorder == null) { setupGate.reset(); screen = Screen.HOME } }
    fun switchCamera() {
        frontCamera = !frontCamera
        estimator = PostureEstimator(sessionThresholds)
        setupGate.reset(); setup = SetupProgress(); feedback = PoseFeedback(); cameraError = null
    }
    fun cameraFailure(value: String) {
        cameraError = value
        feedback = PoseFeedback(message = value)
        setupGate.reset(); setup = SetupProgress()
        if (recorder != null && !paused) pause()
    }
    fun retryCamera() { cameraError = null; estimator = PostureEstimator(sessionThresholds) }
    fun reframe() {
        estimator = PostureEstimator(sessionThresholds)
        feedback = PoseFeedback()
        recorder?.transition(Posture.UNKNOWN, SystemClock.elapsedRealtime())
    }
    fun deviceStable(value: Boolean) { stable = value }

    fun frame(frame: CameraFrame) {
        if (backgrounded || screen !in listOf(Screen.SETUP, Screen.SESSION) || paused || demo) return
        lastFrameAt = SystemClock.elapsedRealtime()
        frameSize = frame.width to frame.height
        feedback = estimator.update(frame.observation)
        if (screen == Screen.SESSION) {
            latency.add(SystemClock.elapsedRealtime() - frame.observation.timestampMs, frame.observation.timestampMs)
        }
        if (!stable) feedback = feedback.copy(needsReset = true, message = "스마트폰을 세워 흔들리지 않게 고정해 주세요")
        if (screen == Screen.SETUP) {
            setup = setupGate.progress(feedback.framed && stable && cameraError == null, lastFrameAt)
            if (setup.ready) startSession()
        } else {
            recorder?.transition(if (feedback.needsReset) Posture.UNKNOWN else feedback.state, lastFrameAt)
        }
    }

    fun startDemo() { beginSetup(true); startSession() }
    private fun startSession() {
        val now = SystemClock.elapsedRealtime()
        latency.reset()
        lastFrameAt = now
        recorder = SessionRecorder(UUID.randomUUID().toString(), System.currentTimeMillis(), now, demo, sessionThresholds.version)
        recorder!!.transition(if (demo) Posture.NORMAL else feedback.state, now)
        if (demo) feedback = PoseFeedback(Posture.NORMAL, message = "직접 상태를 바꿔 앱 흐름을 확인하세요")
        snapshot = recorder!!.snapshot(now)
        screen = Screen.SESSION
        checkpoint(active = true)
    }

    fun demoState(value: Posture) {
        if (!demo || paused) return
        lastFrameAt = SystemClock.elapsedRealtime()
        recorder?.transition(value, lastFrameAt)
        feedback = PoseFeedback(value, message = "데모 입력 · 실제 자세 측정이 아니에요")
    }
    fun demoHeartbeat() { if (demo) lastFrameAt = SystemClock.elapsedRealtime() }
    fun pause() {
        recorder?.pause(SystemClock.elapsedRealtime())
        snapshot = recorder?.snapshot(SystemClock.elapsedRealtime())
        checkpoint(active = true)
    }
    fun resume() {
        if (cameraError != null) return
        estimator = PostureEstimator(sessionThresholds)
        lastFrameAt = SystemClock.elapsedRealtime()
        recorder?.resume(lastFrameAt)
        if (demo) demoState(Posture.NORMAL)
        snapshot = recorder?.snapshot(lastFrameAt)
    }
    fun background(value: Boolean) {
        backgrounded = value
        setupGate.reset(); setup = SetupProgress()
        if (value && recorder != null && !paused) pause()
    }
    fun alertSent() { recorder?.recordAlert() }

    fun finish() {
        val active = recorder ?: return
        val result = active.snapshot(SystemClock.elapsedRealtime())
        if (!result.demo) {
            Log.i("SideFocusLatency", "session=${result.id} device=${Build.MANUFACTURER} ${Build.MODEL} " +
                "sdk=${Build.VERSION.SDK_INT} thresholdVersion=${result.thresholdVersion} " +
                "elapsedMs=${result.endedAt - result.startedAt} ${latency.summary().report()}")
        }
        recorder = null
        snapshot = result
        report = result
        screen = Screen.REPORT
        checkpointJob?.cancel()
        viewModelScope.launch {
            saveMutex.lock()
            try { app.store.checkpoint(result, false); scheduleSync(app) }
            catch (_: Exception) { message = "기록 저장에 실패했어요. 다시 저장을 눌러 주세요." }
            finally { saveMutex.unlock() }
        }
    }
    fun retrySave() {
        val result = report ?: return
        viewModelScope.launch {
            runCatching { app.store.checkpoint(result, false) }
                .onSuccess { scheduleSync(app); message = "기록을 저장했어요" }
                .onFailure { message = "저장 공간을 확인한 뒤 다시 시도해 주세요" }
        }
    }
    fun openReport(value: StudySession) { report = value; screen = Screen.REPORT }
    fun deleteSession(id: String) {
        viewModelScope.launch {
            runCatching { app.syncMutex.withLock { app.store.dao.delete(id) } }.onSuccess { screen = Screen.STATS }
                .onFailure { message = "삭제하지 못했어요. 다시 시도해 주세요." }
        }
    }
    fun deleteLocalData() {
        viewModelScope.launch {
            runCatching { app.syncMutex.withLock { app.store.dao.deleteAll() } }.onSuccess { report = null; message = "기기의 기록을 모두 삭제했어요" }
                .onFailure { message = "기록을 삭제하지 못했어요" }
        }
    }
    fun connectServer(value: String) {
        if (connecting) return
        val endpoint = runCatching { normalizeEndpoint(value, allowUsbLoopback = BuildConfig.DEBUG) }
            .getOrElse { message = it.message; return }
        connecting = true
        viewModelScope.launch {
            try {
                app.syncMutex.withLock {
                    val config = withContext(Dispatchers.IO) { SyncClient(endpoint, app.preferences.deviceId, TokenVault(app)).config() }
                    app.preferences.saveThresholds(config)
                    settings(preferences.value.copy(endpoint = endpoint))
                }
                scheduleSync(app, replacePending = true)
                message = "서버에 연결했어요. 저장된 기록의 전송을 예약했어요."
            } catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { message = "연결하지 못했어요. 서버 주소와 네트워크를 확인해 주세요." }
            finally { connecting = false }
        }
    }
    fun syncNow() {
        viewModelScope.launch { app.store.dao.retryErrors(); scheduleSync(app); message = "기록 전송을 예약했어요" }
    }
    fun disconnectServer() {
        viewModelScope.launch {
            settings(preferences.value.copy(endpoint = ""))
            WorkManager.getInstance(app).cancelUniqueWork(SYNC_WORK)
            app.syncMutex.withLock { withContext(Dispatchers.IO) { TokenVault(app).clear() } }
            message = "서버 연결을 해제했어요. 기기 기록은 보관돼요."
        }
    }
    fun deleteRemoteAndLocalData() {
        if (connecting || preferences.value.endpoint.isBlank()) return
        connecting = true
        viewModelScope.launch {
            try {
                app.syncMutex.withLock {
                    withContext(Dispatchers.IO) { SyncClient(preferences.value.endpoint, app.preferences.deviceId, TokenVault(app)).deleteUser() }
                    settings(preferences.value.copy(endpoint = ""))
                    WorkManager.getInstance(app).cancelUniqueWork(SYNC_WORK)
                    app.store.dao.deleteAll()
                    app.preferences.reset()
                    report = null
                }
                message = "서버와 기기의 데이터를 모두 삭제했어요"
            } catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { message = "서버 삭제에 실패했어요. 기기 기록은 보존했어요." }
            finally { connecting = false }
        }
    }
    private fun checkpoint(active: Boolean) {
        val value = recorder?.snapshot(SystemClock.elapsedRealtime()) ?: return
        lastCheckpoint = SystemClock.elapsedRealtime()
        checkpointJob = viewModelScope.launch {
            saveMutex.lock()
            try { app.store.checkpoint(value, active) }
            catch (_: Exception) { message = "기록을 임시 저장하지 못했어요. 저장 공간을 확인해 주세요." }
            finally { saveMutex.unlock() }
        }
    }
}
