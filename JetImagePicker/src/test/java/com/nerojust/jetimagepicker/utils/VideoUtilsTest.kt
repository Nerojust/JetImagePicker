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
}
