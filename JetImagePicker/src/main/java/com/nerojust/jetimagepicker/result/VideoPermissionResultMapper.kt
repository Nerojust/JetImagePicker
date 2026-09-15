package com.nerojust.jetimagepicker.result

import com.nerojust.jetimagepicker.model.PermissionState

/**
 * Buckets the independent [cameraState] and [audioState] permission states into one
 * [VideoPickerResult.PermissionsRequired], or `null` if both are granted (nothing to report).
 */
fun toPermissionsRequiredOrNull(
    cameraState: PermissionState,
    audioState: PermissionState,
): VideoPickerResult.PermissionsRequired? {
    val states = listOf(cameraState, audioState)
    val denied = states.filter { it.isDenied }.map { it.permission }
    val permanentlyDenied = states.filter { it.isPermanentlyDenied }.map { it.permission }
    val shouldShowRationaleFor = states.filter { it.shouldShowRationale }.map { it.permission }
    if (denied.isEmpty() && permanentlyDenied.isEmpty() && shouldShowRationaleFor.isEmpty()) {
        return null
    }
    return VideoPickerResult.PermissionsRequired(
        denied = denied,
        permanentlyDenied = permanentlyDenied,
        shouldShowRationaleFor = shouldShowRationaleFor,
    )
}
