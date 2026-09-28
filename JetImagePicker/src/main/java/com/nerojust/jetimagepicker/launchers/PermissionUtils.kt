package com.nerojust.jetimagepicker.launchers

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nerojust.jetimagepicker.model.PermissionState

/**
 * Computes the current [PermissionState] for [permission], used by both picker launchers to
 * decide whether to request it, show a rationale, or report it as permanently denied.
 *
 * @param hasBeenRequestedBefore Whether this launcher has already requested [permission] once
 * in this session — needed to distinguish a first-time "not yet asked" denial from a genuine
 * "asked and denied" one, since Android reports both the same way before the first request.
 */
internal fun calculatePermissionState(
    activity: Activity,
    context: Context,
    permission: String,
    hasBeenRequestedBefore: Boolean,
): PermissionState {
    val isGranted =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    val isPermanentlyDenied = !isGranted && !shouldShowRationale && hasBeenRequestedBefore
    val isDenied = !isGranted && !shouldShowRationale && !hasBeenRequestedBefore
    return PermissionState(
        permission = permission,
        isGranted = isGranted,
        isDenied = isDenied,
        isPermanentlyDenied = isPermanentlyDenied,
        shouldShowRationale = shouldShowRationale,
    )
}
