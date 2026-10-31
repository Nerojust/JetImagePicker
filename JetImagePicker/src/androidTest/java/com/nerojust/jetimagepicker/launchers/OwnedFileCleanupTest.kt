package com.nerojust.jetimagepicker.launchers

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nerojust.jetimagepicker.utils.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OwnedFileCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deleteOwnedFiles_removesEveryFileProviderBackedCacheFile() {
        val files =
            List(2) {
                File(context.cacheDir, "OWNED_${UUID.randomUUID()}.jpg").apply {
                    writeText("test")
                }
            }
        val uris =
            files.map { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
        assertTrue(files.all(File::exists))

        deleteOwnedFiles(context, uris)

        assertFalse(files.any(File::exists))
    }

    @Test
    fun pruneStalePickerCache_removesOnlyOldFilesInPickerFolder() {
        val stalePickerOutput =
            File(Utils.pickerCacheDir(context), "COMP_VIDEO_${UUID.randomUUID()}.mp4").apply {
                writeText("stale")
                setLastModified(1_000L)
            }
        val recentPickerOutput =
            File(Utils.pickerCacheDir(context), "CROP_${UUID.randomUUID()}.jpg").apply {
                writeText("recent")
            }
        // A host-app file in the cache root, named like a picker output, must survive.
        val unrelatedFile =
            File(context.cacheDir, "IMG_${UUID.randomUUID()}.jpg").apply {
                writeText("consumer")
                setLastModified(1_000L)
            }

        val deleted = Utils.pruneStalePickerCache(context, nowMillis = 1_000L + 25 * 60 * 60 * 1_000L)

        assertEquals(1, deleted)
        assertFalse(stalePickerOutput.exists())
        assertTrue(recentPickerOutput.exists())
        assertTrue(unrelatedFile.exists())
        recentPickerOutput.delete()
        unrelatedFile.delete()
    }
}
