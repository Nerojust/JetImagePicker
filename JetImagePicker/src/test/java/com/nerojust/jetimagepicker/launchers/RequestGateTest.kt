package com.nerojust.jetimagepicker.launchers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestGateTest {
    @Test
    fun `a second request is rejected until the first finishes`() {
        val gate = RequestGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
    }

    @Test
    fun `finish is safe when no request is active`() {
        val gate = RequestGate()

        gate.finish()

        assertTrue(gate.tryStart())
    }
}
