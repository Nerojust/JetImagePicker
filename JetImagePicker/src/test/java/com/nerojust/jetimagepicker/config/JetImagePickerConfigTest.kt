package com.nerojust.jetimagepicker.config

import org.junit.Assert.assertEquals
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
    }
}
