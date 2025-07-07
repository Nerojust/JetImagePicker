package com.nerojust.jetimagepicker.result

import android.net.Uri

sealed class ImagePickerResult {

    data class Success(val uris: List<Uri>) : ImagePickerResult()

    data class PermissionDenied(val permission: String) : ImagePickerResult()

    data class PermissionPermanentlyDenied(val permission: String) : ImagePickerResult()

    data class ShowRationale(val permission: String) : ImagePickerResult()
}
