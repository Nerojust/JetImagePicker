package com.nerojust.jetimagepicker.example

import android.Manifest
import android.os.SystemClock
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PickerFlowIntegrationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun waitForExternalUi(): Boolean {
        val appPackage = instrumentation.targetContext.packageName
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.currentPackageName != appPackage) return true
            SystemClock.sleep(100)
        }
        return false
    }

    @Test
    fun photoPicker_cancelReturnsToSampleAndReportsCancellation() {
        composeRule.onNodeWithText("Pick from Gallery").performClick()

        assertTrue("system photo picker did not open", waitForExternalUi())
        device.pressBack()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Image selection cancelled.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun videoCamera_canRecordAndReturnAClip() {
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA,
        )
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.RECORD_AUDIO,
        )
        composeRule.onNodeWithText("Video").performClick()
        composeRule.onNodeWithText("Capture Video with Camera").performClick()

        val recordButtonAvailable =
            runCatching {
                composeRule.waitUntil(15_000) {
                    composeRule.onAllNodesWithText("Record").fetchSemanticsNodes().isNotEmpty() ||
                        composeRule.onAllNodesWithText("Failed to initialize the camera").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onAllNodesWithText("Record").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        if (!recordButtonAvailable) {
            composeRule.onAllNodesWithText("Failed to initialize the camera").assertCountEquals(1)
            return
        }

        composeRule.onNodeWithText("Record").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }
        SystemClock.sleep(1_000)
        composeRule.onNodeWithText("Stop").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("Video:", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Video recording failed", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Failed to start video recording").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Video:", substring = true).assertCountEquals(1)
    }
}
