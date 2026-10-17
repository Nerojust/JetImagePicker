package com.nerojust.jetimagepicker.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs against real sample clips in androidTest/assets, which are gitignored. Regenerate with:
 * ```
 * ffmpeg -f lavfi -i "testsrc2=size=1920x1080:rate=30,noise=alls=20:allf=t" -t 3 -c:v libx264 \
 *   -b:v 15M -maxrate 15M -bufsize 30M -pix_fmt yuv420p sample_1080p_15mbps.mp4
 * ffmpeg -f lavfi -i "testsrc2=size=1280x720:rate=30,noise=alls=20:allf=t" -t 3 -c:v libx264 \
 *   -b:v 2M -maxrate 2M -bufsize 4M -pix_fmt yuv420p sample_720p_2mbps.mp4
 * ```
 */
@RunWith(AndroidJUnit4::class)
class CompressVideoTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleUri(asset: String): Uri {
        val file = File(context.cacheDir, asset)
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun sizeOf(uri: Uri): Long = context.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize }

    @Test
    fun highBitrateVideo_shrinksSubstantially() {
        val source = sampleUri("sample_1080p_15mbps.mp4")
        val output = runBlocking { VideoUtils.compressVideo(context, source) }
        assertNotNull("compressVideo returned null", output)
        val (before, after) = sizeOf(source) to sizeOf(output!!)
        Log.i("CompressVideoTest", "1080p/15Mbps: $before -> $after bytes")
        assertTrue("expected < 50% of $before, got $after", after < before / 2)
        assertEquals(720, runBlocking { getVideoShortSide(context, output) })
    }

    @Test
    fun alreadySmallVideo_isNeverReturnedLarger() {
        // 720p at ~2 Mbps sits on Android's quality floor, so re-encoding can't shrink it:
        // compressVideo must return null (caller keeps the original) rather than a bigger file.
        val source = sampleUri("sample_720p_2mbps.mp4")
        val output = runBlocking { VideoUtils.compressVideo(context, source) }
        output?.let { assertTrue("compressed output grew", sizeOf(it) < sizeOf(source)) }
        Log.i("CompressVideoTest", "720p/2Mbps: ${sizeOf(source)} -> ${output?.let(::sizeOf) ?: "kept original"}")
    }
}
