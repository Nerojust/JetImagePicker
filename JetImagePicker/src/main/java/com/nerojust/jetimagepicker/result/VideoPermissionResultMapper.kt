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

/**
 * Deprecated: compatibility bridge during Task 2-6 transition. Will be removed in Task 5/6.
 * Maps a single [PermissionState] to a [VideoPickerResult.PermissionsRequired] by wrapping it
 * into a pair of states (current + granted dummy state).
 */
fun PermissionState.toVideoPickerResult(): VideoPickerResult? {
    val grantedDummy =
        PermissionState(
            permission = "android.permission.RECORD_AUDIO", // arbitrary, won't be used
            isGranted = true,
            isDenied = false,
            isPermanentlyDenied = false,
            shouldShowRationale = false,
        )
    return toPermissionsRequiredOrNull(this, grantedDummy)
}
