package com.nerojust.jetimagepicker.result

import com.nerojust.jetimagepicker.model.PermissionState

/**
 * Maps a [PermissionState] to the [ImagePickerResult] that should be surfaced
 * to the caller, or `null` if the permission is granted and there is nothing to report.
 */
fun PermissionState.toImagePickerResult(): ImagePickerResult? =
    when {
        isGranted -> null
        isPermanentlyDenied -> ImagePickerResult.PermissionPermanentlyDenied(permission)
        shouldShowRationale -> ImagePickerResult.ShowRationale(permission)
        isDenied -> ImagePickerResult.PermissionDenied(permission)
        else -> null
    }
