package org.lukemech.lmtoolbox.e2e

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import java.io.File
import org.hamcrest.Matchers.not

/**
 * What the app asks other apps for, answered here instead (Espresso-Intents): a file picked is the one
 * the test put up ([pick]), a document created is a file in the app's cache ([created]); anything else
 * (browser, share sheet, settings) is backed out of. The files go through the app's own FileProvider
 * (res/xml/file_paths.xml), as a picker's would: content URIs with a name and a size.
 */
object Pickers {
    /** The files offered when a file is picked, by MIME type prefix ("image/", "video/", "" for any), newest first. */
    private val picks = ArrayDeque<Pair<String, File>>()

    /** Documents the app created (ACTION_CREATE_DOCUMENT), by the name it suggested. */
    val created = mutableListOf<File>()

    private fun shareDir(context: Context) = File(context.cacheDir, "metadata/share/e2e").apply { mkdirs() }

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun install(context: Context) {
        picks.clear()
        created.clear()
        // First the catch-all: the stubs added later are matched first
        intending(not(isInternal())).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWithFunction { intent -> pickResult(context, intent) }
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWithFunction { intent -> pickResult(context, intent) }
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWithFunction { intent ->
            val name = intent.getStringExtra(Intent.EXTRA_TITLE) ?: "document"
            val file = File(shareDir(context), "created/$name").apply { parentFile?.mkdirs(); delete(); createNewFile() }
            created += file
            ActivityResult(Activity.RESULT_OK, Intent().setData(uri(context, file)).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        }
    }

    private fun pickResult(context: Context, intent: Intent): ActivityResult {
        val wanted = (intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList().orEmpty() + listOfNotNull(intent.type))
            .ifEmpty { listOf("*/*") }
        val file = picks.firstOrNull { (prefix, _) -> prefix.isEmpty() || wanted.any { it == "*/*" || it.startsWith(prefix) } }?.second ?: return ActivityResult(Activity.RESULT_CANCELED, null)
        return ActivityResult(Activity.RESULT_OK, Intent().setData(uri(context, file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** [file] (copied where the FileProvider serves it) is what's picked next for [mimePrefix] (e.g. "image/"). */
    fun pick(context: Context, file: File, mimePrefix: String = ""): File {
        val shared = if (file.parentFile == shareDir(context)) file else file.copyTo(File(shareDir(context), file.name), overwrite = true)
        picks.addFirst(mimePrefix to shared)
        return shared
    }

    /** A picture to pick: a gradient with shapes, [width] by [height]. */
    fun samplePicture(context: Context, name: String = "sample.jpg", width: Int = 1600, height: Int = 1200): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 90, 160))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 12) {
            paint.color = Color.HSVToColor(floatArrayOf(i * 30f, 0.7f, 0.9f))
            canvas.drawCircle(width * (i + 1) / 13f, height / 2f, height / 6f, paint)
        }
        val file = File(shareDir(context), name)
        val format = if (name.endsWith(".png")) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        file.outputStream().use { bitmap.compress(format, 90, it) }
        return file
    }
}
