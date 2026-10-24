package org.lukemech.lmtoolbox.ui.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal image loading (replaces coil + okhttp): decodes off the main thread, downsamples to
 * roughly maxSizePx and keeps network images in a small memory cache.
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
