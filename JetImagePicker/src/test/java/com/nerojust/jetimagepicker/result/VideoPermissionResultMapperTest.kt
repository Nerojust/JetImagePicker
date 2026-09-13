package com.nerojust.jetimagepicker.result

import com.nerojust.jetimagepicker.model.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPermissionResultMapperTest {
    @Test
    fun `granted permission produces no result`() {
        val state =
            PermissionState(
                permission = "android.permission.CAMERA",
                isGranted = true,
                isDenied = false,
                isPermanentlyDenied = false,
                shouldShowRationale = false,
            )
        assertNull(state.toVideoPickerResult())
    }

    @Test
    fun `permanently denied maps to PermissionPermanentlyDenied`() {
        val state =
            PermissionState(
                permission = "android.permission.CAMERA",
                isGranted = false,
                isDenied = true,
                isPermanentlyDenied = true,
                shouldShowRationale = false,
            )
        val result = state.toVideoPickerResult()
        assertEquals(VideoPickerResult.PermissionPermanentlyDenied("android.permission.CAMERA"), result)
    }

    @Test
    fun `should-show-rationale maps to ShowRationale`() {
        val state =
            PermissionState(
                permission = "android.permission.CAMERA",
                isGranted = false,
                isDenied = false,
                isPermanentlyDenied = false,
                shouldShowRationale = true,
            )
        val result = state.toVideoPickerResult()
        assertEquals(VideoPickerResult.ShowRationale("android.permission.CAMERA"), result)
    }

    @Test
    fun `plain denied maps to PermissionDenied`() {
        val state =
            PermissionState(
                permission = "android.permission.CAMERA",
                isGranted = false,
                isDenied = true,
                isPermanentlyDenied = false,
                shouldShowRationale = false,
            )
        val result = state.toVideoPickerResult()
        assertEquals(VideoPickerResult.PermissionDenied("android.permission.CAMERA"), result)
    }
}
