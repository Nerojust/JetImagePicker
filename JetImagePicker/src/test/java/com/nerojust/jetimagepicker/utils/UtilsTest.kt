package com.nerojust.jetimagepicker.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {
    @Test
    fun `landscape image scales down preserving aspect ratio`() {
        val (width, height) =
            Utils.calculateScaledDimensions(
                srcWidth = 4000,
                srcHeight = 2000,
                maxWidth = 1024,
                maxHeight = 1024,
            )
        assertEquals(1024, width)
        assertEquals(512, height)
    }

    @Test
    fun `portrait image scales down preserving aspect ratio`() {
        val (width, height) =
            Utils.calculateScaledDimensions(
                srcWidth = 2000,
                srcHeight = 4000,
                maxWidth = 1024,
                maxHeight = 1024,
            )
        assertEquals(512, width)
        assertEquals(1024, height)
    }

    @Test
    fun `image smaller than target box is not upscaled`() {
        val (width, height) =
            Utils.calculateScaledDimensions(
                srcWidth = 500,
                srcHeight = 300,
                maxWidth = 1024,
                maxHeight = 1024,
            )
        assertEquals(500, width)
        assertEquals(300, height)
    }

    @Test
    fun `square image fits exactly into square target`() {
        val (width, height) =
            Utils.calculateScaledDimensions(
                srcWidth = 2048,
                srcHeight = 2048,
                maxWidth = 1024,
                maxHeight = 1024,
            )
        assertEquals(1024, width)
        assertEquals(1024, height)
    }

    @Test
    fun `huge photo is sampled down while staying at least target size`() {
        // 8000x6000 into 1024x1024 scales to 1024x768; halving 8000/6000 three times (8x) gives
        // 1000x750 - too small - so the sample size stops at 4 (2000x1500).
        assertEquals(4, Utils.calculateInSampleSize(8000, 6000, 1024, 1024))
    }

    @Test
    fun `image already within target is not sampled`() {
        assertEquals(1, Utils.calculateInSampleSize(800, 600, 1024, 1024))
    }

    @Test
    fun `sample size covers a non-square box in either orientation`() {
        // Box 2000x500: a 4000x3000 image fits to 667x500, but if EXIF rotates it the box is
        // effectively 500x2000 -> 500x375. The larger requirement (667x500) wins: 4000/4 = 1000 >= 667,
        // 3000/4 = 750 >= 500, while 8x (500x375) would be too small.
        assertEquals(4, Utils.calculateInSampleSize(4000, 3000, 2000, 500))
    }

    @Test
    fun `unknown source dimensions fall back to no sampling instead of looping`() {
        // An undecodable file reports -1 x -1 from a bounds-only decode.
        assertEquals(1, Utils.calculateInSampleSize(-1, -1, 1024, 1024))
        assertEquals(1, Utils.calculateInSampleSize(0, 3000, 1024, 1024))
        assertEquals(1, Utils.calculateInSampleSize(4000, 0, 1024, 1024))
    }

    @Test
    fun `invalid target box falls back to no sampling`() {
        assertEquals(1, Utils.calculateInSampleSize(4000, 3000, 0, 1024))
        assertEquals(1, Utils.calculateInSampleSize(4000, 3000, 1024, -5))
    }

    @Test
    fun `sample size is exact on a power-of-two boundary`() {
        // 4096x4096 into 1024x1024: 4x gives exactly 1024 - allowed; 8x would give 512.
        assertEquals(4, Utils.calculateInSampleSize(4096, 4096, 1024, 1024))
    }

    @Test
    fun `extreme panorama is sampled by its constraining side`() {
        // 20000x1000 into 2000x2000 fits to 2000x100: 8x (2500x125) is fine, 16x (1250x62) too small.
        assertEquals(8, Utils.calculateInSampleSize(20000, 1000, 2000, 2000))
    }

    @Test
    fun `tiny target never samples below a 1x1 result`() {
        val sample = Utils.calculateInSampleSize(4000, 3000, 1, 1)
        assertTrue(4000 / sample >= 1 && 3000 / sample >= 1)
    }

    @Test
    fun `scaled dimensions pass through invalid sources unchanged`() {
        assertEquals(-1 to -1, Utils.calculateScaledDimensions(-1, -1, 1024, 1024))
    }
}
