// File: model/PermissionState.kt
package com.nerojust.jetimagepicker.model

data class PermissionState(
    val permission: String,
    val isGranted: Boolean,
    val isDenied: Boolean,
    val isPermanentlyDenied: Boolean,
    val shouldShowRationale: Boolean
)
