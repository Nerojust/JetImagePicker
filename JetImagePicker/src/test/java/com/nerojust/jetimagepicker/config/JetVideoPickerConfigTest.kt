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

    @Test(expected = IllegalArgumentException::class)
    fun `zero duration limit is rejected`() {
        JetVideoPickerConfig(durationLimitSeconds = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative duration limit is rejected`() {
        JetVideoPickerConfig(durationLimitSeconds = -1)
    }
}
