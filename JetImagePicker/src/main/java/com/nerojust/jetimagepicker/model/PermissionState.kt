// File: model/PermissionState.kt
package com.nerojust.jetimagepicker.model

/**
 * Snapshot of a single runtime permission's state, computed after a permission
 * request.
 *
 * @property permission The Android permission string this state describes (e.g.
 * `android.Manifest.permission.CAMERA`).
 * @property isGranted True if the permission is currently granted.
 * @property isDenied True if the permission was denied this round (may still be
 * re-requestable).
 * @property isPermanentlyDenied True if the user denied the permission with
 * "Don't ask again", or the system otherwise stopped prompting.
 * @property shouldShowRationale True if the OS recommends showing a rationale
 * before requesting again.
 */
data class PermissionState(
    val permission: String,
    val isGranted: Boolean,
    val isDenied: Boolean,
    val isPermanentlyDenied: Boolean,
    val shouldShowRationale: Boolean,
)
