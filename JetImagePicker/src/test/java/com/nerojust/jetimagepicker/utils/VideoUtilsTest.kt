package com.nerojust.jetimagepicker.utils

import com.nerojust.jetimagepicker.launchers.ownedVideoResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUtilsTest {
    @Test
    fun `no limit configured never exceeds`() {
        assertFalse(VideoUtils.isDurationExceeded(durationMillis = 120_000, limitSeconds = null))
    }

    @Test
    fun `unknown duration never exceeds`() {
        assertFalse(VideoUtils.isDurationExceeded(durationMillis = null, limitSeconds = 30))
    }

    @Test
    fun `duration under limit does not exceed`() {
        assertFalse(VideoUtils.isDurationExceeded(durationMillis = 29_999, limitSeconds = 30))
    }

    @Test
    fun `duration over limit exceeds`() {
        assertTrue(VideoUtils.isDurationExceeded(durationMillis = 30_001, limitSeconds = 30))
    }

    @Test
    fun `duration exactly at limit does not exceed`() {
        assertFalse(VideoUtils.isDurationExceeded(durationMillis = 30_000, limitSeconds = 30))
    }

    // Uri can't be instantiated in a plain JVM unit test, so these exercise shouldDeleteSource
    // through its generic parameter with Strings standing in for the two uris.
    @Test
    fun `library-owned source superseded by a different output is deleted`() {
        assertTrue(VideoUtils.shouldDeleteSource(isLibraryOwned = true, source = "raw", output = "compressed"))
    }

    @Test
    fun `library-owned source returned as-is is kept`() {
        assertFalse(VideoUtils.shouldDeleteSource(isLibraryOwned = true, source = "raw", output = "raw"))
    }

    @Test
    fun `library-owned source with no output at all is deleted`() {
        assertTrue(VideoUtils.shouldDeleteSource(isLibraryOwned = true, source = "raw", output = null))
    }

    @Test
    fun `caller-owned source is never deleted`() {
        assertFalse(VideoUtils.shouldDeleteSource(isLibraryOwned = false, source = "picked", output = "compressed"))
        assertFalse(VideoUtils.shouldDeleteSource(isLibraryOwned = false, source = "picked", output = "picked"))
        assertFalse(VideoUtils.shouldDeleteSource(isLibraryOwned = false, source = "picked", output = null))
    }

    @Test
    fun `no limit configured never stops`() {
        assertFalse(VideoUtils.shouldStopRecording(elapsedSeconds = 120, limitSeconds = null))
    }

    @Test
    fun `elapsed under limit does not stop`() {
        assertFalse(VideoUtils.shouldStopRecording(elapsedSeconds = 29, limitSeconds = 30))
    }

    @Test
    fun `elapsed exactly at limit stops`() {
        assertTrue(VideoUtils.shouldStopRecording(elapsedSeconds = 30, limitSeconds = 30))
    }

    @Test
    fun `elapsed over limit stops`() {
        assertTrue(VideoUtils.shouldStopRecording(elapsedSeconds = 31, limitSeconds = 30))
    }

    @Test
    fun `range shorter than 1 second is invalid`() {
        assertFalse(VideoUtils.isValidTrimRange(startMs = 1000, endMs = 1500, durationMs = 10000))
    }

    @Test
    fun `range exactly 1 second is valid`() {
        assertTrue(VideoUtils.isValidTrimRange(startMs = 1000, endMs = 2000, durationMs = 10000))
    }

    @Test
    fun `reversed range is invalid`() {
        assertFalse(VideoUtils.isValidTrimRange(startMs = 5000, endMs = 2000, durationMs = 10000))
    }

    @Test
    fun `negative start is invalid`() {
        assertFalse(VideoUtils.isValidTrimRange(startMs = -100, endMs = 2000, durationMs = 10000))
    }

    @Test
    fun `end past the actual duration is invalid`() {
        assertFalse(VideoUtils.isValidTrimRange(startMs = 0, endMs = 10001, durationMs = 10000))
    }

    @Test
    fun `end exactly at the duration is valid`() {
        assertTrue(VideoUtils.isValidTrimRange(startMs = 0, endMs = 10000, durationMs = 10000))
    }

    @Test
    fun `gallery video returned unchanged is not library owned but thumbnail is`() {
        assertEquals(listOf("thumbnail"), ownedVideoResults("gallery", "gallery", false, "thumbnail"))
    }

    @Test
    fun `compressed and captured videos are library owned`() {
        assertEquals(listOf("compressed"), ownedVideoResults("gallery", "compressed", false, null))
        assertEquals(listOf("capture"), ownedVideoResults("capture", "capture", true, null))
    }
}
