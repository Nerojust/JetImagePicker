package com.nerojust.jetimagepicker.result

import com.nerojust.jetimagepicker.model.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun grantedState(permission: String) =
    PermissionState(
        permission = permission,
        isGranted = true,
        isDenied = false,
        isPermanentlyDenied = false,
        shouldShowRationale = false,
    )

private fun deniedState(permission: String) =
    PermissionState(
        permission = permission,
        isGranted = false,
        isDenied = true,
        isPermanentlyDenied = false,
        shouldShowRationale = false,
    )

private fun permanentlyDeniedState(permission: String) =
    PermissionState(
        permission = permission,
        isGranted = false,
        isDenied = false,
        isPermanentlyDenied = true,
        shouldShowRationale = false,
    )

private fun rationaleState(permission: String) =
    PermissionState(
        permission = permission,
        isGranted = false,
        isDenied = false,
        isPermanentlyDenied = false,
        shouldShowRationale = true,
    )

class VideoPermissionResultMapperTest {
    @Test
    fun `both granted produces no result`() {
        val result =
            toPermissionsRequiredOrNull(
                cameraState = grantedState("android.permission.CAMERA"),
                audioState = grantedState("android.permission.RECORD_AUDIO"),
            )
        assertNull(result)
    }

    @Test
    fun `camera denied, audio granted buckets camera as denied`() {
        val result =
            toPermissionsRequiredOrNull(
                cameraState = deniedState("android.permission.CAMERA"),
                audioState = grantedState("android.permission.RECORD_AUDIO"),
            )
        assertEquals(
            VideoPickerResult.PermissionsRequired(
                denied = listOf("android.permission.CAMERA"),
                permanentlyDenied = emptyList(),
                shouldShowRationaleFor = emptyList(),
            ),
            result,
        )
    }

    @Test
    fun `camera permanently denied, audio needs rationale buckets both independently`() {
        val result =
            toPermissionsRequiredOrNull(
                cameraState = permanentlyDeniedState("android.permission.CAMERA"),
                audioState = rationaleState("android.permission.RECORD_AUDIO"),
            )
        assertEquals(
            VideoPickerResult.PermissionsRequired(
                denied = emptyList(),
                permanentlyDenied = listOf("android.permission.CAMERA"),
                shouldShowRationaleFor = listOf("android.permission.RECORD_AUDIO"),
            ),
            result,
        )
    }

    @Test
    fun `both denied buckets both as denied`() {
        val result =
            toPermissionsRequiredOrNull(
                cameraState = deniedState("android.permission.CAMERA"),
                audioState = deniedState("android.permission.RECORD_AUDIO"),
            )
        assertEquals(
            VideoPickerResult.PermissionsRequired(
                denied = listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"),
                permanentlyDenied = emptyList(),
                shouldShowRationaleFor = emptyList(),
            ),
            result,
        )
    }




}
