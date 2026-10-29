package org.lukemech.lmtoolbox.ui.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal image loading (replaces coil + okhttp): decodes off the main thread, downsamples to
 * roughly maxSizePx and keeps network images and PDF pages in a small memory cache.
 */
object Images {
    private const val TAG = "Images"

    // ~1/16 of the heap, measured in KB
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }

    suspend fun loadUrl(url: String, maxSizePx: Int): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    try {
                        inputStream.use { it.readBytes() }
                    } finally {
                        disconnect()
                    }
                }
                decode(maxSizePx) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
                    ?.also { cache.put(url, it) }
            } catch (e: Exception) {
                Log.w(TAG, "Loading $url failed: ${e.message}")
                null
            }
        }
    }

    suspend fun loadFile(file: File, maxSizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        decode(maxSizePx) { BitmapFactory.decodeFile(file.absolutePath, it) }
    }

    /** A picture of the phone's ([uri]: a gallery's, a picked file's), turned as its EXIF says, at most [maxSizePx] on its longer side. */
    suspend fun loadUri(context: Context, uri: Uri, maxSizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val side = maxOf(info.size.width, info.size.height)
                if (side > maxSizePx) decoder.setTargetSampleSize(Integer.highestOneBit(side / maxSizePx).coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Loading $uri failed: ${e.message}")
            null
        }
    }

    // Android's PdfRenderer draws one page at a time, of one file
    private val pdfLock = Mutex()

    /**
     * Page [index] (from 0) of the PDF [file] drawn on white, at most [maxSizePx] on its longer side: null if Android
     * can't read it (a protected one). Kept in the cache till the file changes.
     */
    suspend fun loadPdfPage(file: File, index: Int, maxSizePx: Int): ImageBitmap? {
        val key = "${file.absolutePath}#$index@$maxSizePx:${file.lastModified()}"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            pdfLock.withLock {
                try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            renderer.openPage(index).use { page ->
                                val scale = maxSizePx.toFloat() / maxOf(page.width, page.height)
                                val bitmap = Bitmap.createBitmap(
                                    (page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                                )
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap.asImageBitmap().also { cache.put(key, it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Drawing page $index of $file failed: ${e.message}")
                    null
                }
            }
        }
    }

    /** Two-pass decode: read bounds first, then decode with a power-of-two sample size. */
    private fun decode(maxSizePx: Int, decoder: (BitmapFactory.Options) -> Bitmap?): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decoder(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSizePx) sample *= 2
        return decoder(BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}

@Composable
fun rememberUrlImage(url: String, maxSizePx: Int = 1024): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, url) { value = Images.loadUrl(url, maxSizePx) }
    return image
}

@Composable
fun rememberFileImage(file: File?, maxSizePx: Int = 2048): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file) { value = file?.let { Images.loadFile(it, maxSizePx) } }
    return image
}

/** Page [index] (from 0) of the PDF [file], drawn (see [Images.loadPdfPage]); null while it's drawn, or if it can't be. */
@Composable
fun rememberPdfPage(file: File, index: Int, maxSizePx: Int = 360): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file, index) { value = Images.loadPdfPage(file, index, maxSizePx) }
    return image
}
