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
 * Temporary bridge for sequencing Tasks 2→6: maps a single [PermissionState] to
 * [VideoPickerResult.PermissionsRequired] format (three independent lists). Removed
 * once Task 6 updates [com.nerojust.jetimagepicker.state.rememberJetVideoPickerState]
 * to call the new [toPermissionsRequiredOrNull] directly.
 */
@Deprecated("Temporary bridge for Task 2->6 sequencing; removed once rememberJetVideoPickerState.kt stops calling it in Task 6.")
internal fun PermissionState.toVideoPickerResult(): VideoPickerResult? {
    if (isGranted) return null
    return VideoPickerResult.PermissionsRequired(
        denied = if (isDenied) listOf(permission) else emptyList(),
        permanentlyDenied = if (isPermanentlyDenied) listOf(permission) else emptyList(),
        shouldShowRationaleFor = if (shouldShowRationale) listOf(permission) else emptyList(),
    )
}
