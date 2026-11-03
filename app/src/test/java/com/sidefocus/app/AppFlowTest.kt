package com.sidefocus.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.work.testing.WorkManagerTestInitHelper
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
import java.io.File

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

    @Test fun `navigate screens and finish a demo without camera permission`() {
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
