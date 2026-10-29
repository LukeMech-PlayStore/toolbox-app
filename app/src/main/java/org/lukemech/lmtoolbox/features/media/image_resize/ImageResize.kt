package org.lukemech.lmtoolbox.features.media.image_resize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.OutputFolder
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.helpers.displayName
import org.lukemech.lmtoolbox.helpers.safeFileName
import org.lukemech.lmtoolbox.helpers.saveToMediaStore
import org.lukemech.lmtoolbox.tasks.AppTasks
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.lukemech.lmtoolbox.tasks.TaskRun

/** How a picture goes into a box of another shape. */
enum class Fit {
    /** Whole, as big as fits: one side shorter than the box's. */
    Fit,
    /** Covering the box, what's over cut off. */
    Fill,
    /** Whole, as big as fits, the rest of the box a background (white, or clear where the format can be). */
    Pad,
    /** Pulled to the box's shape. */
    Stretch
}

/** What the new picture is saved as; [Original]: as the picked one is (JPEG for what can't be written, e.g. HEIC). */
enum class ImageFormat(val ext: String, val mimeType: String) {
    Original("", ""),
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    /** Whether it has a quality to pick (PNG is lossless: only a smaller picture makes it smaller). */
    val isLossy get() = this == JPEG || this == WEBP

    /** What [Original] is for a picture of [mimeType]. */
    fun resolve(mimeType: String?): ImageFormat = if (this != Original) this else when (mimeType) {
        "image/png" -> PNG
        "image/webp" -> WEBP
        else -> JPEG
    }
}

/**
 * How a picture is resized: to [percent] of it, or (with [width] or [height]) into that box, as [fit] says;
 * saved as [format], at [quality] or, with [targetMb], as good as fits in that file size.
 */
data class ResizeOptions(
    /** Of each side, when there's no box. */
    val percent: Int = 50,
    /** The box, in pixels; 0: none (one side alone: the other follows the picture's shape). */
    val width: Int = 0,
    val height: Int = 0,
    val fit: Fit = Fit.Fit,
    /** The box turned to the picture's way (a portrait one into 1920×1080 gets 1080×1920). */
    val matchOrientation: Boolean = true,
    /** Made bigger too, when it's smaller than the box (else kept at its size). */
    val enlarge: Boolean = false,
    val format: ImageFormat = ImageFormat.Original,
    /** 1..100, of the lossy formats. */
    val quality: Int = 90,
    /** The file's size to aim at, in MB; 0: none. */
    val targetMb: Double = 0.0,
    /** The original's EXIF copied over (date, camera, location...); else none at all. */
    val keepMetadata: Boolean = true,
) {
    val hasBox get() = width > 0 || height > 0

    fun toJson(): String = JSONObject()
        .put("percent", percent).put("width", width).put("height", height).put("fit", fit.name)
        .put("matchOrientation", matchOrientation).put("enlarge", enlarge).put("format", format.name)
        .put("quality", quality).put("targetMb", targetMb).put("keepMetadata", keepMetadata)
        .toString()

    companion object {
        val PERCENTS = listOf(100, 75, 50, 25)
        /** Boxes for screens and posts: 4K, Full HD, HD, a square. */
        val BOXES = listOf(3840 to 2160, 1920 to 1080, 1280 to 720, 1080 to 1080)
        val TARGET_SIZES = listOf(0.5, 1.0, 2.0, 5.0, 10.0)
        const val DEFAULT_TARGET_MB = 1.0
        const val MIN_QUALITY = 30

        /** The last resizing's choices, for the next one. */
        fun load(context: Context): ResizeOptions {
            val d = ResizeOptions()
            val json = runCatching { JSONObject(PreferenceHelper.get(context, ImageResizerFeature.S_OPTIONS, "")) }.getOrNull() ?: return d
            return ResizeOptions(
                percent = json.optInt("percent", d.percent).coerceIn(1, 100),
                width = json.optInt("width", d.width).coerceAtLeast(0),
                height = json.optInt("height", d.height).coerceAtLeast(0),
                fit = runCatching { Fit.valueOf(json.getString("fit")) }.getOrDefault(d.fit),
                matchOrientation = json.optBoolean("matchOrientation", d.matchOrientation),
                enlarge = json.optBoolean("enlarge", d.enlarge),
                format = runCatching { ImageFormat.valueOf(json.getString("format")) }.getOrDefault(d.format),
                quality = json.optInt("quality", d.quality).coerceIn(MIN_QUALITY, 100),
                targetMb = json.optDouble("targetMb", d.targetMb).takeIf { !it.isNaN() && it >= 0 } ?: d.targetMb,
                keepMetadata = json.optBoolean("keepMetadata", d.keepMetadata),
            )
        }

        fun save(context: Context, o: ResizeOptions) = PreferenceHelper.put(context, ImageResizerFeature.S_OPTIONS, o.toJson())
    }
}

/**
 * Resizes pictures with Android's own decoders (no Linux): [open] reads one, [resize] makes the new one and
 * saves it in Pictures (or the folder picked for it).
 */
object ImageResize {
    /** Its failure for a picture too big to make (the bottom bar tells it, see AppTasks.reportFailure). */
    const val TOO_BIG = "Too big to fit in memory"

    /** A picked picture: its size as it's shown (its EXIF turning applied), and a small copy to show. */
    data class Opened(val uri: Uri, val name: String, val width: Int, val height: Int, val size: Long, val mimeType: String?, val preview: Bitmap) {
        val baseName get() = name.substringBeforeLast('.').ifBlank { name }
    }

    /** Where the picture goes: [content] of the [width]×[height] picture is [crop] of the original. */
    data class Plan(val width: Int, val height: Int, val content: RectF, val crop: RectF)

    /** The saved picture, in [folder] (its name). */
    data class Saved(
        val uri: Uri, val name: String, val mimeType: String, val folder: String,
        val width: Int, val height: Int, val bytes: Long,
        /** Bigger than the size aimed at, even as small as it got. */
        val missedTarget: Boolean,
        val preview: Bitmap,
    )

    /** The open picture, its options, the new name and the last result: back to them after the page was left. */
    var opened by mutableStateOf<Opened?>(null)
    var options by mutableStateOf<ResizeOptions?>(null)
    var fileName by mutableStateOf("")
    var saved by mutableStateOf<Saved?>(null)

    /** Making the picture, a task: it goes on as the page is left. */
    val run = TaskRun(ImageResizerFeature)

    private val output = OutputFolder(ImageResizerFeature.S_OUTPUT)
    private const val PREVIEW_PX = 720
    /** Steps of making the picture smaller to get under the size aimed at. */
    private const val SHRINKS = 8
    /** The most pixels it makes (as Android's software bitmaps can hold). */
    private const val MAX_PIXELS = 100_000_000L
    /** Bytes kept free of a size aimed at for the EXIF, at most. */
    private const val EXIF_ROOM = 64_000L

    fun outputFolder() = output

    suspend fun open(context: Context, uri: Uri): Result<Opened> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var name: String? = null
            var size = 0L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0)
                    size = if (it.isNull(1)) 0 else it.getLong(1)
                }
            }
            var headerWidth = 0
            var headerHeight = 0
            val preview = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                headerWidth = info.size.width
                headerHeight = info.size.height
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(previewSampleSize(max(headerWidth, headerHeight)))
            }
            // The decoder turns it as its EXIF says, the header doesn't: as it's shown
            val turned = headerWidth != headerHeight && (preview.width > preview.height) != (headerWidth > headerHeight)
            Opened(
                uri, name ?: "image", if (turned) headerHeight else headerWidth, if (turned) headerWidth else headerHeight,
                size, runCatching { resolver.getType(uri) }.getOrNull(), preview
            )
        }
    }

    /** The largest power of two at which [side] still is at least [PREVIEW_PX]. */
    private fun previewSampleSize(side: Int): Int {
        var sample = 1
        while (side / (sample * 2) >= PREVIEW_PX) sample *= 2
        return sample
    }

    /** What [o] makes of a [srcWidth]×[srcHeight] picture. */
    fun plan(srcWidth: Int, srcHeight: Int, o: ResizeOptions): Plan {
        val w = srcWidth.toFloat()
        val h = srcHeight.toFloat()
        val whole = RectF(0f, 0f, w, h)
        if (!o.hasBox) {
            val outW = (w * o.percent / 100).roundToInt().coerceAtLeast(1)
            val outH = (h * o.percent / 100).roundToInt().coerceAtLeast(1)
            return Plan(outW, outH, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), whole)
        }
        var boxW = o.width
        var boxH = o.height
        if (o.matchOrientation && boxW > 0 && boxH > 0 && srcWidth != srcHeight && (boxW > boxH) != (srcWidth > srcHeight)) {
            boxW = o.height
            boxH = o.width
        }
        // One side alone: the other one follows
        if (boxW == 0) boxW = (boxH * w / h).roundToInt().coerceAtLeast(1)
        if (boxH == 0) boxH = (boxW * h / w).roundToInt().coerceAtLeast(1)
        val bw = boxW.toFloat()
        val bh = boxH.toFloat()
        return when (o.fit) {
            Fit.Fit -> {
                val s = min(bw / w, bh / h).let { if (o.enlarge) it else min(it, 1f) }
                val outW = (w * s).roundToInt().coerceAtLeast(1)
                val outH = (h * s).roundToInt().coerceAtLeast(1)
                Plan(outW, outH, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), whole)
            }
            Fit.Pad -> {
                val s = min(bw / w, bh / h).let { if (o.enlarge) it else min(it, 1f) }
                val cw = w * s
                val ch = h * s
                Plan(boxW, boxH, RectF((bw - cw) / 2, (bh - ch) / 2, (bw + cw) / 2, (bh + ch) / 2), whole)
            }
            Fit.Fill -> {
                val s = max(bw / w, bh / h)
                val cropW = bw / s
                val cropH = bh / s
                Plan(boxW, boxH, RectF(0f, 0f, bw, bh), RectF((w - cropW) / 2, (h - cropH) / 2, (w + cropW) / 2, (h + cropH) / 2))
            }
            Fit.Stretch -> Plan(boxW, boxH, RectF(0f, 0f, bw, bh), whole)
        }
    }

    /**
     * Makes [opened] as [o] says and saves it as [fileName]. With a size to aim at: the best quality under
     * it, and while even the lowest is over, the picture made smaller.
     */
    suspend fun resize(context: Context, opened: Opened, o: ResizeOptions, fileName: String): Result<Saved> = withContext(Dispatchers.Default) {
        runCatching {
            val format = o.format.resolve(opened.mimeType)
            val plan = plan(opened.width, opened.height, o)
            if (plan.width.toLong() * plan.height > MAX_PIXELS) error(TOO_BIG)
            var picture = render(context, opened, plan, format)
            // Room left for the EXIF copied over (a phone's, with its maker notes, takes up to tens of kB)
            val target = (o.targetMb * 1_000_000).toLong().takeIf { it > 0 }
                ?.let { if (o.keepMetadata) it - min(EXIF_ROOM, it / 10) else it }
            var bytes = if (target == null) compress(picture, format, o.quality) else ByteArray(0)
            var missed = false
            if (target != null) {
                var shrinks = 0
                while (true) {
                    bytes = bestUnder(picture, format, o.quality, target) ?: compress(picture, format, ResizeOptions.MIN_QUALITY.takeIf { format.isLossy } ?: 100)
                    if (bytes.size <= target) break
                    if (shrinks++ >= SHRINKS || picture.width <= 16 || picture.height <= 16) {
                        missed = true
                        break
                    }
                    // Its size goes with its pixels: as many fewer as it's over, and a bit
                    val factor = sqrt(target.toDouble() / bytes.size) * 0.9
                    val smaller = picture.scale((picture.width * factor).roundToInt().coerceAtLeast(16), (picture.height * factor).roundToInt().coerceAtLeast(16))
                    picture.recycle()
                    picture = smaller
                }
            }
            val preview = scaledPreview(picture)
            val width = picture.width
            val height = picture.height
            picture.recycle()
            save(
                context, bytes, "${safeFileName(fileName, "image")}.${format.ext}", format, width, height, missed, preview,
                metadataFrom = opened.uri.takeIf { o.keepMetadata }
            )
        }
    }

    /** The new picture: [plan]'s part of the original, read just big enough (a power-of-two of it), drawn smooth. */
    private fun render(context: Context, opened: Opened, plan: Plan, format: ImageFormat): Bitmap {
        val scaleX = plan.content.width() / plan.crop.width()
        val scaleY = plan.content.height() / plan.crop.height()
        val needed = max(scaleX, scaleY).coerceAtMost(1f)
        var sample = 1
        while (needed * sample * 2 <= 1f) sample *= 2
        val source = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, opened.uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sample)
        }
        val fx = source.width / opened.width.toFloat()
        val fy = source.height / opened.height.toFloat()
        val out = createBitmap(plan.width, plan.height)
        val canvas = Canvas(out)
        // JPEG has no transparency: white, not black, where there's none of the picture
        if (format == ImageFormat.JPEG) canvas.drawColor(Color.WHITE)
        val crop = Rect(
            (plan.crop.left * fx).roundToInt(), (plan.crop.top * fy).roundToInt(),
            (plan.crop.right * fx).roundToInt().coerceAtMost(source.width), (plan.crop.bottom * fy).roundToInt().coerceAtMost(source.height)
        )
        canvas.drawBitmap(source, crop, plan.content, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG))
        source.recycle()
        return out
    }

    @Suppress("DEPRECATION") // WEBP: the lossy one before Android 11
    private fun compress(bitmap: Bitmap, format: ImageFormat, quality: Int): ByteArray {
        val compressFormat = when (format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        return ByteArrayOutputStream().also { bitmap.compress(compressFormat, quality, it) }.toByteArray()
    }

    /** The best quality (up to [quality]) at which [bitmap] fits in [target] bytes; null if none does (or it's PNG). */
    private fun bestUnder(bitmap: Bitmap, format: ImageFormat, quality: Int, target: Long): ByteArray? {
        compress(bitmap, format, quality).let { if (it.size <= target) return it }
        if (!format.isLossy) return null
        var low = ResizeOptions.MIN_QUALITY
        var high = quality - 1
        var best: ByteArray? = null
        while (low <= high) {
            val mid = (low + high) / 2
            val bytes = compress(bitmap, format, mid)
            if (bytes.size <= target) {
                best = bytes
                low = mid + 1
            } else high = mid - 1
        }
        return best
    }

    private fun scaledPreview(bitmap: Bitmap): Bitmap {
        val s = min(1f, PREVIEW_PX.toFloat() / max(bitmap.width, bitmap.height))
        return bitmap.scale((bitmap.width * s).roundToInt().coerceAtLeast(1), (bitmap.height * s).roundToInt().coerceAtLeast(1))
    }

    /**
     * [source]'s EXIF into [file] (a [width]×[height] picture, upright already): all of it, but what's of the
     * old pixels (their size, turning, layout and the small preview picture). Nothing when it has none.
     */
    private fun copyMetadata(context: Context, source: Uri, file: File, width: Int, height: Int) {
        val from = context.contentResolver.openInputStream(source)?.use { ExifInterface(it) } ?: return
        val to = ExifInterface(file)
        var copied = false
        for (tag in EXIF_TAGS) {
            val value = from.getAttribute(tag) ?: continue
            to.setAttribute(tag, value)
            copied = true
        }
        if (!copied) return
        to.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        to.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        to.saveAttributes()
    }

    /** Every EXIF tag ExifInterface knows (its TAG_ constants), but those of the old pixels. */
    private val EXIF_TAGS: List<String> by lazy {
        val pixels = setOf(
            "ImageWidth", "ImageLength", "PixelXDimension", "PixelYDimension", "Orientation",
            "Compression", "BitsPerSample", "SamplesPerPixel", "PhotometricInterpretation", "PlanarConfiguration",
            "YCbCrSubSampling", "YCbCrPositioning", "StripOffsets", "StripByteCounts", "RowsPerStrip",
            "JPEGInterchangeFormat", "JPEGInterchangeFormatLength", "ThumbnailImageWidth", "ThumbnailImageLength",
            "ThumbnailOrientation", "NewSubfileType", "SubfileType", "DefaultCropSize", "DNGVersion",
            "PreviewImageStart", "PreviewImageLength", "JpgFromRaw",
        )
        ExifInterface::class.java.fields
            .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
            .mapNotNull { runCatching { it.get(null) as String }.getOrNull() }
            .filter { it !in pixels }
            .distinct()
    }

    /** Into the folder picked for pictures, else Pictures (as the gallery finds them), with [metadataFrom]'s EXIF. */
    private suspend fun save(
        context: Context, bytes: ByteArray, name: String, format: ImageFormat,
        width: Int, height: Int, missed: Boolean, preview: Bitmap, metadataFrom: Uri?
    ): Saved = withContext(Dispatchers.IO) {
        val dir = ImageResizerFeature.cacheDir(context).apply { mkdirs() }
        val file = File(dir, name)
        try {
            file.writeBytes(bytes)
            // Without it when it can't be copied: the picture is fine all the same
            if (metadataFrom != null) runCatching { copyMetadata(context, metadataFrom, file, width, height) }
            val bytes = if (metadataFrom != null) file.readBytes() else bytes
            output.save(context, file, format.mimeType)?.let { uri ->
                return@withContext Saved(uri, displayName(context, uri) ?: name, format.mimeType, output.name(context, ""), width, height, bytes.size.toLong(), missed, preview)
            }
            val uri = saveToMediaStore(
                context, MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), Environment.DIRECTORY_PICTURES,
                name, format.mimeType
            ) { it.write(bytes) }
            Saved(uri, displayName(context, uri) ?: name, format.mimeType, context.getString(R.string.f_ImgRes_pictures), width, height, bytes.size.toLong(), missed, preview)
        } finally {
            file.delete()
        }
    }
}
