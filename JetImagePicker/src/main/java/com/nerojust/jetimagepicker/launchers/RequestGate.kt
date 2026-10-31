package com.nerojust.jetimagepicker.launchers

/** Serializes external picker, permission, and camera requests until their callback completes. */
internal class RequestGate {
    private var inFlight = false

    fun tryStart(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    fun finish() {
        inFlight = false
    }
}
