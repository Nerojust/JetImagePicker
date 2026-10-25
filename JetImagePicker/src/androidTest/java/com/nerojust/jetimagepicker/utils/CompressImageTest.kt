package com.nerojust.jetimagepicker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nerojust.jetimagepicker.config.ImageOutputFormat
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.random.Random

/**
 * Runs [Utils.compressImage] against real encoded files, so both decode paths are exercised:
 * ImageDecoder on API 28+, BitmapFactory + manual EXIF handling below that. Run on an API 26-27
 * emulator as well as a current one.
 */
@RunWith(AndroidJUnit4::class)
class CompressImageTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /**
     * Writes a [width] x [height] image whose top-left quarter is red and the rest blue, tagged
     * with EXIF [orientation] - so where the red quarter ends up shows how it was rotated.
     */
    private fun sourceImage(
        width: Int,
        height: Int,
        orientation: Int = ExifInterface.ORIENTATION_NORMAL,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        extension: String = "jpg",
    ): Uri {
        // RGB_565 halves the memory, so even API 23 emulators' small heaps can build the large sources.
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            drawRect(0f, 0f, width / 2f, height / 2f, Paint().apply { color = Color.RED })
        }
        val file = File(context.cacheDir, "SRC_${UUID.randomUUID()}.$extension")
        file.outputStream().use { bitmap.compress(format, 95, it) }
        bitmap.recycle()
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return uriFor(file)
    }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    private fun compress(
        uri: Uri,
        config: JetImagePickerConfig = JetImagePickerConfig(),
    ): Uri? = runBlocking { Utils.compressImage(context, uri, config) }

    private fun decode(uri: Uri): Bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it)!! }

    private fun bytesOf(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    private fun redCorner(bitmap: Bitmap): Corner? {
        val w = bitmap.width
        val h = bitmap.height

        fun isRed(
            x: Int,
            y: Int,
        ): Boolean {
            val pixel = bitmap.getPixel(x, y)
            return Color.red(pixel) > 180 && Color.blue(pixel) < 90
        }
        val corners =
            mapOf(
                Corner.TOP_LEFT to isRed(w / 10, h / 10),
                Corner.TOP_RIGHT to isRed(w - 1 - w / 10, h / 10),
                Corner.BOTTOM_LEFT to isRed(w / 10, h - 1 - h / 10),
                Corner.BOTTOM_RIGHT to isRed(w - 1 - w / 10, h - 1 - h / 10),
            ).filterValues { it }.keys
        return corners.singleOrNull()
    }

    // Where the stored top-left corner lands once each EXIF orientation is applied.
    private val expectedCorner =
        mapOf(
            ExifInterface.ORIENTATION_NORMAL to Corner.TOP_LEFT,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to Corner.TOP_RIGHT,
            ExifInterface.ORIENTATION_ROTATE_180 to Corner.BOTTOM_RIGHT,
            ExifInterface.ORIENTATION_FLIP_VERTICAL to Corner.BOTTOM_LEFT,
            ExifInterface.ORIENTATION_TRANSPOSE to Corner.TOP_LEFT,
            ExifInterface.ORIENTATION_ROTATE_90 to Corner.TOP_RIGHT,
            ExifInterface.ORIENTATION_TRANSVERSE to Corner.BOTTOM_RIGHT,
            ExifInterface.ORIENTATION_ROTATE_270 to Corner.BOTTOM_LEFT,
        )

    /** Compresses a [width] x [height] source in every EXIF orientation; returns what came out wrong. */
    private fun orientationFailures(
        width: Int,
        height: Int,
        config: JetImagePickerConfig,
        expectedWidth: Int,
        expectedHeight: Int,
    ): List<String> =
        expectedCorner.mapNotNull { (orientation, corner) ->
            val output = compress(sourceImage(width, height, orientation), config) ?: return@mapNotNull "$orientation: null output"
            val bitmap = decode(output)
            val swapsSides = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
            val expectedSize = if (swapsSides) expectedHeight to expectedWidth else expectedWidth to expectedHeight
            val actualSize = bitmap.width to bitmap.height
            val actualCorner = redCorner(bitmap)
            if (actualSize != expectedSize || actualCorner != corner) {
                "$orientation: expected $expectedSize/$corner, got $actualSize/$actualCorner"
            } else {
                null
            }
        }

    @Test
    fun allEightExifOrientations_comeOutUpright() {
        val failures = orientationFailures(400, 300, JetImagePickerConfig(), 400, 300)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun allEightExifOrientations_comeOutUprightWhenDownsampled() {
        // Downsampling while decoding is its own code path (and where Android 9's decoder trips).
        val config = JetImagePickerConfig(targetWidth = 400, targetHeight = 400)
        val failures = orientationFailures(1600, 1200, config, 400, 300)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun hugePhoto_isShrunkToFitTheTargetBox() {
        val output = compress(sourceImage(3200, 2400), JetImagePickerConfig(targetWidth = 1024, targetHeight = 1024))
        assertNotNull(output)
        val bitmap = decode(output!!)
        assertEquals(1024 to 768, bitmap.width to bitmap.height)
        assertEquals(Corner.TOP_LEFT, redCorner(bitmap))
    }

    @Test
    fun rotatedHugePhoto_fitsANonSquareBoxAfterRotation() {
        // Stored 3200x2400 + rotate-90 = upright 2400x3200, which fits 2000x500 as 375x500.
        val source = sourceImage(3200, 2400, ExifInterface.ORIENTATION_ROTATE_90)
        val output = compress(source, JetImagePickerConfig(targetWidth = 2000, targetHeight = 500))
        assertNotNull(output)
        val bitmap = decode(output!!)
        assertEquals(375 to 500, bitmap.width to bitmap.height)
        assertEquals(Corner.TOP_RIGHT, redCorner(bitmap))
    }

    @Test
    fun noTargetBox_keepsOriginalSize() {
        val bitmap = decode(compress(sourceImage(640, 480))!!)
        assertEquals(640 to 480, bitmap.width to bitmap.height)
    }

    @Test
    fun smallerThanTarget_isNeverUpscaled() {
        val output = compress(sourceImage(300, 200), JetImagePickerConfig(targetWidth = 1024, targetHeight = 1024))
        val bitmap = decode(output!!)
        assertEquals(300 to 200, bitmap.width to bitmap.height)
    }

    @Test
    fun onlyOneTargetSide_leavesSizeAlone() {
        // Documented: resizing needs both targetWidth and targetHeight.
        val output = compress(sourceImage(640, 480), JetImagePickerConfig(targetWidth = 100))
        val bitmap = decode(output!!)
        assertEquals(640 to 480, bitmap.width to bitmap.height)
    }

    @Test
    fun jpegOutput_isARealJpegFile() {
        val output = compress(sourceImage(200, 200), JetImagePickerConfig(outputFormat = ImageOutputFormat.JPEG))!!
        assertTrue(output.toString().endsWith(".jpg"))
        val bytes = bytesOf(output)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
    }

    @Test
    fun webpOutput_isARealWebpFile() {
        val output = compress(sourceImage(200, 200), JetImagePickerConfig(outputFormat = ImageOutputFormat.WEBP))!!
        assertTrue(output.toString().endsWith(".webp"))
        val bytes = bytesOf(output)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(200 to 200, decode(output).let { it.width to it.height })
    }

    @Test
    fun webpOutput_isSmallerThanJpegAtTheSameQuality() {
        // Photo-like content - smooth gradients, soft shapes and mild sensor-style noise. (Solid
        // blocks compress to almost nothing in either format, and harsh synthetic patterns favor JPEG.)
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawPaint(
                Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, 600f, Color.rgb(90, 150, 220), Color.rgb(240, 200, 150), Shader.TileMode.CLAMP)
                },
            )
            drawCircle(
                560f,
                220f,
                140f,
                Paint().apply {
                    shader = RadialGradient(560f, 220f, 140f, Color.rgb(250, 240, 200), Color.rgb(200, 120, 60), Shader.TileMode.CLAMP)
                },
            )
            drawRect(
                0f,
                420f,
                800f,
                600f,
                Paint().apply {
                    shader = LinearGradient(0f, 420f, 800f, 600f, Color.rgb(40, 90, 40), Color.rgb(110, 150, 70), Shader.TileMode.CLAMP)
                },
            )
        }
        val random = Random(42)
        for (y in 0 until 600) {
            for (x in 0 until 800) {
                val pixel = bitmap.getPixel(x, y)
                val n = random.nextInt(-6, 7)
                bitmap.setPixel(
                    x,
                    y,
                    Color.rgb(
                        (Color.red(pixel) + n).coerceIn(0, 255),
                        (Color.green(pixel) + n).coerceIn(0, 255),
                        (Color.blue(pixel) + n).coerceIn(0, 255),
                    ),
                )
            }
        }
        val file = File(context.cacheDir, "SRC_${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val source = uriFor(file)
        val jpeg = compress(source, JetImagePickerConfig(compressionQuality = 75))!!
        val webp = compress(source, JetImagePickerConfig(compressionQuality = 75, outputFormat = ImageOutputFormat.WEBP))!!
        val jpegSize = bytesOf(jpeg).size
        val webpSize = bytesOf(webp).size
        Log.i("CompressImageTest", "photo-like 800x600 at q75: jpeg $jpegSize, webp $webpSize bytes")
        assertTrue("webp $webpSize >= jpeg $jpegSize", webpSize < jpegSize)
    }

    @Test
    fun pngAndWebpInputs_areAccepted() {
        val png = sourceImage(300, 200, format = Bitmap.CompressFormat.PNG, extension = "png")
        assertEquals(300 to 200, decode(compress(png)!!).let { it.width to it.height })
        @Suppress("DEPRECATION")
        val webp = sourceImage(300, 200, format = Bitmap.CompressFormat.WEBP, extension = "webp")
        assertEquals(300 to 200, decode(compress(webp)!!).let { it.width to it.height })
    }

    @Test
    fun heicInput_isConvertedToJpeg() {
        // HEIC decoding arrived in API 28; older devices can't produce HEIC photos either.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val file = File(context.cacheDir, "SRC_${UUID.randomUUID()}.heic")
        InstrumentationRegistry.getInstrumentation().context.assets.open("sample_400x300.heic").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val output = compress(uriFor(file), JetImagePickerConfig(targetWidth = 200, targetHeight = 200))
        assertNotNull(output)
        assertTrue(output.toString().endsWith(".jpg"))
        val bitmap = decode(output!!)
        assertEquals(200 to 150, bitmap.width to bitmap.height)
        assertEquals(Corner.TOP_LEFT, redCorner(bitmap))
    }

    @Test
    fun outOfRangeQuality_isClampedInsteadOfFailing() {
        assertNotNull(compress(sourceImage(200, 200), JetImagePickerConfig(compressionQuality = 150)))
        assertNotNull(compress(sourceImage(200, 200), JetImagePickerConfig(compressionQuality = -5)))
    }

    @Test
    fun corruptFile_returnsNullWithoutThrowing() {
        val file = File(context.cacheDir, "SRC_${UUID.randomUUID()}.jpg")
        file.writeBytes(ByteArray(2048) { (it * 31).toByte() })
        assertNull(compress(uriFor(file)))
        assertNull(compress(uriFor(file), JetImagePickerConfig(targetWidth = 100, targetHeight = 100)))
    }

    @Test
    fun emptyFile_returnsNullWithoutThrowing() {
        val file = File(context.cacheDir, "SRC_${UUID.randomUUID()}.jpg").apply { createNewFile() }
        assertNull(compress(uriFor(file), JetImagePickerConfig(targetWidth = 100, targetHeight = 100)))
    }

    @Test
    fun missingFile_returnsNullWithoutThrowing() {
        val file = File(context.cacheDir, "SRC_${UUID.randomUUID()}.jpg")
        assertNull(compress(uriFor(file)))
    }

    @Test
    fun corruptExifBlock_stillProducesTheImage() {
        // A JPEG with a garbage APP1 (EXIF) segment spliced in after SOI.
        val good = bytesOf(sourceImage(320, 240))
        val junkExif = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x00, 0x10) + "Exif\u0000\u0000".toByteArray() + ByteArray(8) { 0x7F }
        val file = File(context.cacheDir, "SRC_${UUID.randomUUID()}.jpg")
        file.writeBytes(good.copyOfRange(0, 2) + junkExif + good.copyOfRange(2, good.size))
        val output = compress(uriFor(file), JetImagePickerConfig(targetWidth = 160, targetHeight = 160))
        assertNotNull(output)
        assertEquals(160 to 120, decode(output!!).let { it.width to it.height })
    }

    @Test
    fun manyImagesInARow_allGetDistinctFiles() {
        val outputs = (1..10).map { compress(sourceImage(200, 150))!! }
        assertEquals(10, outputs.toSet().size)
    }

    @Test
    fun cropIntermediate_isWrittenAsJpeg() {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val uri = Utils.writeBitmapToCache(context, bitmap)
        assertNotNull(uri)
        assertEquals(120 to 80, decode(uri!!).let { it.width to it.height })
    }
}
