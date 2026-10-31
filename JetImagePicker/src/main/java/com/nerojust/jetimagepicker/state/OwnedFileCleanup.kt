package com.nerojust.jetimagepicker.state

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.nerojust.jetimagepicker.launchers.deleteOwnedFiles
import com.nerojust.jetimagepicker.launchers.findActivity
import com.nerojust.jetimagepicker.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val UriListSaver =
    listSaver<List<Uri>, String>(
        save = { list -> list.map(Uri::toString) },
        restore = { saved -> saved.map(Uri::parse) },
    )

/**
 * Only a user-finished Activity loses its files. Background destruction to reclaim memory is
 * neither finishing nor a configuration change, and its saved selection still points at them.
 */
internal fun shouldDeleteOwnedFilesOnDestroy(activity: Activity?): Boolean = activity?.isFinishing == true

/** Prunes stale picker files once, and deletes [ownedUris] when the host Activity finishes for good. */
@Composable
internal fun OwnedFileCleanup(
    context: Context,
    ownedUris: List<Uri>,
) {
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) { Utils.pruneStalePickerCache(context) }
    }

    DisposableEffect(context, ownedUris) {
        val activity = context.findActivity()
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY && shouldDeleteOwnedFilesOnDestroy(activity)) {
                    deleteOwnedFiles(context, ownedUris)
                }
            }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
}
