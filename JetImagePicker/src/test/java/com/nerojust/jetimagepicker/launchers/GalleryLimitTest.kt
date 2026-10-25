package com.nerojust.jetimagepicker.launchers

import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryLimitTest {
    @Test
    fun `multi picker is used only when multiple selection is on and the cap allows more than one`() {
        assertTrue(usesMultiPicker(JetImagePickerConfig(allowMultiple = true)))
        assertTrue(usesMultiPicker(JetImagePickerConfig(allowMultiple = true, maxSelection = 2)))
        assertFalse(usesMultiPicker(JetImagePickerConfig(allowMultiple = true, maxSelection = 1)))
        assertFalse(usesMultiPicker(JetImagePickerConfig(allowMultiple = false)))
        assertFalse(usesMultiPicker(JetImagePickerConfig(allowMultiple = false, maxSelection = 5)))
    }

    @Test
    fun `picker limit passes through values the contract accepts`() {
        assertEquals(5, pickerMaxItems(maxSelection = 5, systemLimit = 100))
        assertEquals(100, pickerMaxItems(maxSelection = 100, systemLimit = 100))
    }

    @Test
    fun `picker limit is capped at the system maximum`() {
        assertEquals(100, pickerMaxItems(maxSelection = 500, systemLimit = 100))
    }

    @Test
    fun `picker limit never drops below the contract minimum of 2`() {
        assertEquals(2, pickerMaxItems(maxSelection = 1, systemLimit = 100))
    }

    @Test
    fun `a nonsensical system limit cannot make the picker limit throw`() {
        assertEquals(2, pickerMaxItems(maxSelection = 5, systemLimit = 0))
        assertEquals(2, pickerMaxItems(maxSelection = 5, systemLimit = -1))
    }

    @Test
    fun `no system limit leaves the requested cap alone`() {
        assertEquals(250, pickerMaxItems(maxSelection = 250, systemLimit = Int.MAX_VALUE))
    }

    @Test
    fun `results are trimmed to the cap for pickers that ignore it`() {
        val picked = listOf("a", "b", "c", "d", "e", "f", "g")
        assertEquals(listOf("a", "b", "c", "d", "e"), picked.limitedTo(5))
    }

    @Test
    fun `results under the cap or without one are untouched`() {
        val picked = listOf("a", "b")
        assertEquals(picked, picked.limitedTo(5))
        assertEquals(picked, picked.limitedTo(null))
        assertEquals(emptyList<String>(), emptyList<String>().limitedTo(3))
    }
}
