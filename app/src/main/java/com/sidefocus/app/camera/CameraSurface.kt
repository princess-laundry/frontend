package com.sidefocus.app.camera

import android.content.Context
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.sidefocus.core.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class CameraFrame(val observation: PoseObservation, val width: Int, val height: Int)

@Composable
fun CameraSurface(
    enabled: Boolean,
    skeletonOnly: Boolean,
    useFrontCamera: Boolean,
    intervalMs: Long,
    feedback: PoseFeedback,
    frameSize: Pair<Int, Int>,
    onFrame: (CameraFrame) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val latestFrame by rememberUpdatedState(onFrame)
    val latestError by rememberUpdatedState(onError)
    val latestInterval by rememberUpdatedState(intervalMs)
    Box(modifier.background(Color(0xFF17243A))) {
        if (enabled) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().alpha(if (skeletonOnly) 0f else 1f))
        }
        Canvas(Modifier.fillMaxSize()) {
            val body = feedback.body
            val tint = when (feedback.state) {
                Posture.FHP, Posture.LEAN -> Color(0xFFFFBF69)
                else -> Color(0xFF73E2CA)
            }
            val points = if (body != null) listOf(body.ear, body.shoulder, body.hip) else
                listOf(Point(0.55, 0.23), Point(0.47, 0.42), Point(0.47, 0.78))
            fun mapped(p: Point): Offset {
                if (body == null) return Offset((p.x * size.width).toFloat(), (p.y * size.height).toFloat())
                val (w, h) = frameSize
                val scale = min(size.width / w.coerceAtLeast(1), size.height / h.coerceAtLeast(1))
                val x = if (useFrontCamera) w - p.x else p.x
                return Offset(((size.width - w * scale) / 2 + x * scale).toFloat(),
                    ((size.height - h * scale) / 2 + p.y * scale).toFloat())
            }
            val mapped = points.map(::mapped)
            val color = if (body == null) Color(0xFF687C97) else tint
            mapped.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 7.dp.toPx()) }
            mapped.forEach { drawCircle(color.copy(alpha = 0.16f), 17.dp.toPx(), it); drawCircle(color, 6.dp.toPx(), it) }
            drawLine(Color.White.copy(alpha = 0.1f), Offset(size.width * 0.47f, size.height * 0.15f), Offset(size.width * 0.47f, size.height * 0.88f), 1.dp.toPx())
        }
    }
    DisposableEffect(enabled, useFrontCamera, owner) {
        val disposed = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        var detector: PoseLandmarker? = null
        var broken = false
        var lastAnalysis = 0L
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        if (enabled) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed.get()) runCatching {
                    provider = future.get()
                    val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    check(provider!!.hasCamera(selector)) { "선택한 카메라를 사용할 수 없어요. 다른 카메라로 전환해 주세요." }
                    preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
                    analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build().apply {
                            setAnalyzer(executor) { image ->
                                try {
                                    val now = SystemClock.elapsedRealtime()
                                    if (disposed.get() || broken || now - lastAnalysis < latestInterval) return@setAnalyzer
                                    lastAnalysis = now
                                    if (detector == null) detector = PoseLandmarker.createFromOptions(context,
                                        PoseLandmarker.PoseLandmarkerOptions.builder()
                                            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                                            .setRunningMode(RunningMode.VIDEO).setNumPoses(1)
                                            .setMinPoseDetectionConfidence(0.5f).setMinPosePresenceConfidence(0.5f)
                                            .setMinTrackingConfidence(0.5f).build())
                                    val raw = image.toBitmap()
                                    val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                                    val bitmap = android.graphics.Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, false)
                                    try {
                                        val mpImage = BitmapImageBuilder(bitmap).build()
                                        val result = try { detector!!.detectForVideo(mpImage, now) } finally { mpImage.close() }
                                        val landmarks = result.landmarks().firstOrNull()
                                        fun side(ear: Int, shoulder: Int, hip: Int): BodySide? {
                                            if (landmarks == null || landmarks.size < 25) return null
                                            fun point(i: Int) = landmarks[i].let { p -> Point(p.x().toDouble() * bitmap.width,
                                                p.y().toDouble() * bitmap.height, minOf(p.visibility().orElse(0f), p.presence().orElse(0f))) }
                                            return BodySide(point(ear), point(shoulder), point(hip))
                                        }
                                        val frame = CameraFrame(PoseObservation(now, landmarks != null, side(7,11,23), side(8,12,24)), bitmap.width, bitmap.height)
                                        ContextCompat.getMainExecutor(context).execute { if (!disposed.get()) latestFrame(frame) }
                                    } finally {
                                        if (bitmap !== raw) bitmap.recycle()
                                        raw.recycle()
                                    }
                                } catch (exception: Exception) {
                                    broken = true
                                    ContextCompat.getMainExecutor(context).execute {
                                        if (!disposed.get()) latestError("카메라 분석을 시작하지 못했어요. 권한과 모델 파일을 확인하고 다시 시도해 주세요.")
                                    }
                                } finally { image.close() }
                            }
                        }
                    provider!!.bindToLifecycle(owner, selector, preview, analysis)
                }.onFailure { latestError(it.message ?: "카메라를 사용할 수 없어요") }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            disposed.set(true)
            analysis?.clearAnalyzer()
            listOfNotNull(preview, analysis).toTypedArray().takeIf { it.isNotEmpty() }?.let { provider?.unbind(*it) }
            executor.execute { detector?.close() }
            executor.shutdown()
        }
    }
}

@Composable
fun ObserveDeviceStability(onStable: (Boolean) -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onStable)
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        var last: FloatArray? = null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                val delta = last?.let { old -> sqrt(values.indices.sumOf { i -> ((values[i] - old[i]) * (values[i] - old[i])).toDouble() }) } ?: 0.0
                // Portrait or landscape is supported; camera should remain upright rather than face the ceiling.
                callback(delta < 0.8 && abs(values[2]) < 5.5)
                last = values.clone()
            }
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) else callback(true)
        onDispose { manager.unregisterListener(listener) }
    }
}
