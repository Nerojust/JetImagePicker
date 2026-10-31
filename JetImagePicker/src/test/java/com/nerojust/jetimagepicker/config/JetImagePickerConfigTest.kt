package com.nerojust.jetimagepicker.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JetImagePickerConfigTest {
    @Test
    fun `default config values`() {
        val config = JetImagePickerConfig()

        assertTrue(config.enableCompression)
        assertEquals(75, config.compressionQuality)
        assertNull(config.targetWidth)
        assertNull(config.targetHeight)
        assertTrue(config.allowMultiple)
        assertNull(config.maxSelection)
        assertFalse(config.cropMultiple)
        assertEquals(ImageOutputFormat.JPEG, config.outputFormat)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `maxSelection below 1 is rejected`() {
        JetImagePickerConfig(maxSelection = 0)
    }

    @Test
    fun `maxSelection of 1 is allowed`() {
        assertEquals(1, JetImagePickerConfig(maxSelection = 1).maxSelection)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative maxSelection is rejected`() {
        JetImagePickerConfig(maxSelection = -3)
    }

    @Test
    fun `new options are appended so positional construction from 2_1_0 still compiles`() {
        // Same positional arguments a 2.1.0 caller could have written.
        val config = JetImagePickerConfig(false, 50, 800, 600, false, true, CropAspectRatio.Square)
        assertFalse(config.enableCompression)
        assertEquals(50, config.compressionQuality)
        assertEquals(800, config.targetWidth)
        assertEquals(600, config.targetHeight)
        assertFalse(config.allowMultiple)
        assertTrue(config.enableCrop)
        assertEquals(CropAspectRatio.Square, config.cropAspectRatio)
    }

    @Test
    fun `output formats map to their file extensions`() {
        assertEquals("jpg", ImageOutputFormat.JPEG.extension)
        assertEquals("webp", ImageOutputFormat.WEBP.extension)
    }

    @Test
    fun `compression quality remains permissive because processing clamps it`() {
        assertEquals(101, JetImagePickerConfig(compressionQuality = 101).compressionQuality)
        assertEquals(-1, JetImagePickerConfig(compressionQuality = -1).compressionQuality)
    }

    @Test
    fun `only one target dimension remains supported for compatibility`() {
        val config = JetImagePickerConfig(targetWidth = 100)

        assertEquals(100, config.targetWidth)
        assertNull(config.targetHeight)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive target dimensions are rejected`() {
        JetImagePickerConfig(targetWidth = 0, targetHeight = 100)
    }
}
