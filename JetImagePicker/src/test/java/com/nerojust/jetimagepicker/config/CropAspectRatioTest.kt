package com.nerojust.jetimagepicker.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropAspectRatioTest {
    @Test
    fun `free has no ratio`() {
        assertNull(CropAspectRatio.Free.toRatioOrNull())
    }

    @Test
    fun `square is 1 to 1`() {
        assertEquals(1f, CropAspectRatio.Square.toRatioOrNull())
    }

    @Test
    fun `custom divides ratioX by ratioY`() {
        val ratio = CropAspectRatio.Custom(ratioX = 16f, ratioY = 9f).toRatioOrNull()
        assertEquals(16f / 9f, ratio!!, 0.0001f)
    }
}
