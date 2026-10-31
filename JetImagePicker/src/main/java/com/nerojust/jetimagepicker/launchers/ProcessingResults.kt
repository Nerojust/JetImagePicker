package com.nerojust.jetimagepicker.launchers

/** Uses a successfully processed output, otherwise preserves the original input as a fallback. */
internal fun <T> compressionOutputOrOriginal(
    original: T,
    output: T?,
): T = output ?: original

/** Returns only final image results whose files were created by this library. */
internal fun <T> ownedImageResults(
    finalResults: List<T>,
    croppedResults: List<T>,
    libraryOwnedInputs: Set<T>,
    processingInputs: List<T>,
): List<T> =
    finalResults.filter { result ->
        result in croppedResults || result in libraryOwnedInputs || result !in processingInputs
    }

/** Returns only final video/thumbnail results whose files were created by this library. */
internal fun <T : Any> ownedVideoResults(
    source: T,
    output: T?,
    isSourceLibraryOwned: Boolean,
    thumbnail: T?,
): List<T> =
    buildList {
        output?.takeIf { it != source || isSourceLibraryOwned }?.let(::add)
        thumbnail?.let(::add)
    }
