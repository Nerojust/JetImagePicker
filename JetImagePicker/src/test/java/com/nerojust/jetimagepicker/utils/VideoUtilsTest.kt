package com.nerojust.jetimagepicker.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUtilsTest {
    @Test
    fun `no limit configured never exceeds`() {
        assertFalse(VideoUtils.isDurationExceeded(durationSeconds = 120, limitSeconds = null))
    }

    @Test
    fun `unknown duration never exceeds`() {
        assertFalse(VideoUtils.isDurationExceeded(durationSeconds = null, limitSeconds = 30))
    }

    @Test
    fun `duration under limit does not exceed`() {
        assertFalse(VideoUtils.isDurationExceeded(durationSeconds = 29, limitSeconds = 30))
    }

    @Test
    fun `duration over limit exceeds`() {
        assertTrue(VideoUtils.isDurationExceeded(durationSeconds = 31, limitSeconds = 30))
    }

    @Test
    fun `duration exactly at limit does not exceed`() {
        assertFalse(VideoUtils.isDurationExceeded(durationSeconds = 30, limitSeconds = 30))
    }

    // Uri can't be instantiated in a plain JVM unit test, so these exercise shouldDeleteSource
    // through its generic parameter with Strings standing in for the two uris.
    @Test
    fun `camera capture superseded by a different output is deleted`() {
        assertTrue(VideoUtils.shouldDeleteSource(isCameraCapture = true, source = "raw", output = "compressed"))
    }

    @Test
    fun `camera capture returned as-is is kept`() {
        assertFalse(VideoUtils.shouldDeleteSource(isCameraCapture = true, source = "raw", output = "raw"))
    }

    @Test
    fun `camera capture with no output at all is deleted`() {
        assertTrue(VideoUtils.shouldDeleteSource(isCameraCapture = true, source = "raw", output = null))
    }

    @Test
    fun `gallery pick is never deleted`() {
        assertFalse(VideoUtils.shouldDeleteSource(isCameraCapture = false, source = "picked", output = "compressed"))
        assertFalse(VideoUtils.shouldDeleteSource(isCameraCapture = false, source = "picked", output = "picked"))
        assertFalse(VideoUtils.shouldDeleteSource(isCameraCapture = false, source = "picked", output = null))
    }
}
