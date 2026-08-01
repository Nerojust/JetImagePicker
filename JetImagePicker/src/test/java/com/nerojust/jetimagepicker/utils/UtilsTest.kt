package com.nerojust.jetimagepicker.utils

import org.junit.Assert.assertEquals
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
}
