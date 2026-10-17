package com.nerojust.jetimagepicker.config

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JetVideoPickerConfigTest {
    @Test
    fun `default config values`() {
        val config = JetVideoPickerConfig()

        assertTrue(config.enableCompression)
        assertTrue(config.enableThumbnail)
        assertNull(config.durationLimitSeconds)
    }
}
