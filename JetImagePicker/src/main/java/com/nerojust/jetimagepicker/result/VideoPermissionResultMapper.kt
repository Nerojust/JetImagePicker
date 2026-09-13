package com.nerojust.jetimagepicker.result

import com.nerojust.jetimagepicker.model.PermissionState

/**
 * Maps a [PermissionState] to the [VideoPickerResult] that should be surfaced
 * to the caller, or `null` if the permission is granted and there is nothing to report.
 */
fun PermissionState.toVideoPickerResult(): VideoPickerResult? =
    when {
        isGranted -> null
        isPermanentlyDenied -> VideoPickerResult.PermissionPermanentlyDenied(permission)
        shouldShowRationale -> VideoPickerResult.ShowRationale(permission)
        isDenied -> VideoPickerResult.PermissionDenied(permission)
        else -> null
    }
