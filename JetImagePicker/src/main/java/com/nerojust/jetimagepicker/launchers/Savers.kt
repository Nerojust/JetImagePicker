package com.nerojust.jetimagepicker.launchers

import android.net.Uri
import androidx.compose.runtime.saveable.Saver

/** Shared [Saver] for a nullable [Uri] across process death, used by both picker launchers. */
internal val NullableUriSaver =
    Saver<Uri?, String>(
        save = { it?.toString() ?: "" },
        restore = { if (it.isEmpty()) null else Uri.parse(it) },
    )
