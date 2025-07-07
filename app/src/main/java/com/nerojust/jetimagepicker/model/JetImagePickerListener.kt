// File: model/JetImagePickerListener.kt
package com.nerojust.jetimagepicker.model

import android.net.Uri

interface JetImagePickerListener {
    fun onImagesPicked(uris: List<Uri>) {}
    fun onPermissionGranted(permission: String) {}
    fun onPermissionDenied(permission: String) {}
    fun onPermissionPermanentlyDenied(permission: String) {}
    fun onPermissionRationaleShouldShow(permission: String) {}
}
