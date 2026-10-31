package com.nerojust.jetimagepicker.launchers

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import com.mr0xf00.easycrop.CropError
import com.mr0xf00.easycrop.CropResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CropBatchTest {
    private fun imageBitmap() = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()

    @Test
    fun saveFailure_reportsErrorAndDeletesEarlierCropFiles() =
        runBlocking {
            val inputUris = listOf(Uri.parse("content://source/first"), Uri.parse("content://source/second"))
            val savedUri = Uri.parse("content://owned/first")
            var saveCount = 0
            var deletedUris = emptyList<Uri>()
            var caughtException: Exception? = null

            val result =
                cropBatch(
                    uris = inputUris,
                    crop = { CropResult.Success(imageBitmap()) },
                    save = { if (saveCount++ == 0) savedUri else null },
                    delete = { deletedUris = it },
                    onCropException = { _, error -> caughtException = error },
                )

            assertTrue("crop threw unexpectedly: $caughtException", caughtException == null)
            assertEquals(CropBatchResult.Failed("Failed to save the cropped image"), result)
            assertEquals(listOf(savedUri), deletedUris)
        }

    @Test
    fun cropError_reportsCropFailureAndCleansPartialOutput() =
        runBlocking {
            val savedUri = Uri.parse("content://owned/first")
            var cropCount = 0
            var deletedUris = emptyList<Uri>()
            val result =
                cropBatch(
                    uris = listOf(Uri.parse("content://source/first"), Uri.parse("content://source/second")),
                    crop = { if (cropCount++ == 0) CropResult.Success(imageBitmap()) else CropError.SavingError },
                    save = { savedUri },
                    delete = { deletedUris = it },
                )

            assertEquals(CropBatchResult.Failed("Crop failed: SavingError"), result)
            assertEquals(listOf(savedUri), deletedUris)
        }

    @Test
    fun cropCancellation_cleansPartialOutput() =
        runBlocking {
            val savedUri = Uri.parse("content://owned/first")
            var cropCount = 0
            var deletedUris = emptyList<Uri>()
            val result =
                cropBatch(
                    uris = listOf(Uri.parse("content://source/first"), Uri.parse("content://source/second")),
                    crop = { if (cropCount++ == 0) CropResult.Success(imageBitmap()) else CropResult.Cancelled },
                    save = { savedUri },
                    delete = { deletedUris = it },
                )

            assertEquals(CropBatchResult.Cancelled, result)
            assertEquals(listOf(savedUri), deletedUris)
        }
}
