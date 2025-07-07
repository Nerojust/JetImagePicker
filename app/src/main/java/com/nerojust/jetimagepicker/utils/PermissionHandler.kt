package com.nerojust.jetimagepicker.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHandler(private val context: Context) {

    private var onResult: ((Boolean) -> Unit)? = null

    private val permissionLauncher =
        (context as? ComponentActivity)
            ?.activityResultRegistry
            ?.register(
                "permission_request_${System.currentTimeMillis()}",
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                val granted = result.all { it.value }
                onResult?.invoke(granted)
            }

    fun requestPermissions(permissions: Array<String>, callback: (Boolean) -> Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            callback(true)
        } else {
            onResult = callback
            permissionLauncher?.launch(permissions)
        }
    }
}
