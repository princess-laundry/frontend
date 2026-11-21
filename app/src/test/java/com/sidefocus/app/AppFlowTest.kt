package com.sidefocus.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.sidefocus.app.camera.CameraFrame
import com.sidefocus.core.BodySide
import com.sidefocus.core.Point
import com.sidefocus.core.PoseObservation
import com.sidefocus.core.Posture
import com.sidefocus.core.Segment
import com.sidefocus.core.StudySession
import com.sidefocus.core.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp", application = FocusApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule(order = 0) val work = object : ExternalResource() {
        override fun before() { WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication()) }
        override fun after() { WorkManagerTestInitHelper.closeWorkDatabase() }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `run demo then verify v2 starts directly and recognizes bowed head`() {
        ShadowLog.clear()
        val vm = ViewModelProvider(compose.activity)[FocusViewModel::class.java]
        compose.waitUntil(10_000) { vm.ready }
        compose.onNodeWithText("집중 시작하기").assertIsEnabled()
        screenshot("01-home")
        compose.onNodeWithText("기록").performClick()
        compose.onNodeWithText("매일 조금 더 바르게").assertIsDisplayed()
        screenshot("02-statistics")
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("나의 집중 방식").assertIsDisplayed()
        screenshot("03-settings")
        compose.onNodeWithText("홈").performClick()
        compose.onNodeWithText("집중 시작하기").performClick()
        compose.onNodeWithText("카메라 허용하기").assertIsDisplayed()
        screenshot("04-setup")
        compose.onNodeWithText("돌아가기").performClick()
        compose.onNodeWithText("카메라 없이 데모 체험").performScrollTo().performClick()
        compose.onNodeWithText("앱 체험 중").assertIsDisplayed()
        screenshot("05-session")
        compose.onNodeWithText("잠깐 쉬기").performScrollTo().performClick()
        compose.onNodeWithText("이어하기").assertIsDisplayed().performClick()
        compose.onNodeWithText("집중 마치기").performClick()
        compose.onNodeWithText("저장하고 마치기").performClick()
        compose.onNodeWithText("차곡차곡 쌓인 집중").assertIsDisplayed()
        screenshot("06-report")
        compose.waitUntil(10_000) { vm.rows.value.any { it.session().demo } }
        assertTrue(vm.rows.value.all { it.syncStatus == "DEMO" })
        assertTrue(ShadowLog.getLogsForTag("SideFocusLatency").isEmpty())
        verifyV2Session(vm)
        verifyLegacyReport(vm)
    }

    private fun verifyV2Session(vm: FocusViewModel) {
        compose.runOnIdle {
            val app = compose.activity.application as FocusApplication
            app.preferences.saveThresholds(Thresholds(version = 1, leanDegrees = 20.0))
            vm.beginSetup()
            feedFrames(vm, count = 30, noseY = 30.0)
            assertEquals(Screen.SETUP, vm.screen)
            feedFrames(vm, count = 1, noseY = 30.0)
            assertEquals(Screen.SESSION, vm.screen)
            assertEquals(2, vm.snapshot?.thresholdVersion)
            feedFrames(vm, count = 20, noseY = 30.0, analysisMs = 80)
            assertEquals(Posture.NORMAL, vm.feedback.state)
            feedFrames(vm, count = 20, noseY = 90.0, analysisMs = 260)
            assertEquals(Posture.FHP, vm.feedback.state)
            assertFalse(vm.feedback.calibrated)
        }
        compose.onNodeWithText("거북목").assertIsDisplayed()
        compose.runOnIdle {
            vm.pause()
            feedFrames(vm, count = 5, noseY = 30.0, analysisMs = 999)
            vm.finish()
            val firstReport = ShadowLog.getLogsForTag("SideFocusLatency").single().msg
            assertTrue(firstReport.contains("처리 지연 n=40 p50=80ms p95=260ms max=260ms"))
            assertTrue(firstReport.contains("200ms 초과 20 회"))
            assertTrue(firstReport.contains("thresholdVersion=2"))

            // A new session must exclude setup frames and the previous session's samples.
            vm.beginSetup()
            feedFrames(vm, count = 31, noseY = 30.0)
            assertEquals(Screen.SESSION, vm.screen)
            feedFrames(vm, count = 3, noseY = 30.0, analysisMs = 120)
            vm.finish()
            val nextReport = ShadowLog.getLogsForTag("SideFocusLatency").last().msg
            assertTrue(nextReport.contains("처리 지연 n=3 p50=120ms p95=120ms max=120ms"))
        }
    }

    private fun feedFrames(vm: FocusViewModel, count: Int, noseY: Double, analysisMs: Long = 0) {
        val side = BodySide(Point(100.0, 30.0), Point(100.0, 100.0), Point(100.0, 300.0))
        repeat(count) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(200))
            val pose = PoseObservation(SystemClock.elapsedRealtime(), true, right = side, nose = Point(140.0, noseY))
            ShadowSystemClock.advanceBy(Duration.ofMillis(analysisMs))
            vm.frame(CameraFrame(pose, 480, 640))
        }
    }

    private fun verifyLegacyReport(vm: FocusViewModel) {
        compose.runOnIdle {
            vm.openReport(StudySession("legacy", 0, 12000, listOf(
                Segment(Posture.NORMAL, 0, 7000),
                Segment(Posture.FHP, 7000, 9000),
                Segment(Posture.LEAN, 9000, 12000)
            )))
        }
        compose.onNodeWithText("거북목").assertIsDisplayed()
        compose.onNodeWithText("5초").assertIsDisplayed()
        compose.onNodeWithText("상체 기울임").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val target = File("build/screenshots/$name.png")
            requireNotNull(target.parentFile).mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
