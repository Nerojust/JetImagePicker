package com.nerojust.jetimagepicker.result

import android.net.Uri

/**
 * Result of a pick/capture operation, delivered via [rememberJetImagePickerState]'s `onResult` callback.
 */
sealed class ImagePickerResult {
    /** One or more images were successfully picked or captured. */
    data class Success(val uris: List<Uri>) : ImagePickerResult()

    /** [permission] was denied for this request; it can still be requested again. */
    data class PermissionDenied(val permission: String) : ImagePickerResult()

    /** [permission] was permanently denied ("Don't ask again"); direct the user to app settings. */
    data class PermissionPermanentlyDenied(val permission: String) : ImagePickerResult()

    /** The OS recommends showing a rationale for [permission] before requesting it again. */
    data class ShowRationale(val permission: String) : ImagePickerResult()
}
