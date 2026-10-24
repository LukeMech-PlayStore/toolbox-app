package org.lukemech.lmtoolbox.helpers

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.OutputStream
import org.lukemech.lmtoolbox.R

/** A file a tool made, in [folder] (its name, as the user knows it: e.g. "Downloads" or a picked one). */
data class SavedFile(val uri: Uri, val name: String, val mimeType: String, val folder: String)

/** [fileName] (picked by the user) as a file's name: no path or odd characters; [fallback] when nothing's left. */
fun safeFileName(fileName: String, fallback: String = "") =
    fileName.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().trim('.').take(150).ifEmpty { fallback }

/** [uri]'s file name, e.g. what a saved file got ("name (1).mp4" next to an earlier one); a file:// one's own. */
fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.takeIf { uri.scheme == "file" }

/**
 * A new file [name] in MediaStore's [collection] (e.g. MediaStore.Downloads'), under [relativePath] (e.g.
 * Environment.DIRECTORY_DOWNLOADS), as the gallery and other apps find it: hidden while [write] fills it, deleted
 * when that fails (not left as an empty, pending one).
 */
fun saveToMediaStore(
    context: Context, collection: Uri, relativePath: String, name: String, mimeType: String, write: (OutputStream) -> Unit
): Uri {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(collection, values) ?: error("Couldn't create the file in $relativePath")
    try {
        resolver.openOutputStream(uri)!!.use(write)
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
    return uri
}

/** Opens [uri] (of [mimeType]) in an app picked for it. */
fun openFile(context: Context, uri: Uri, mimeType: String) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(view, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_opens, Toast.LENGTH_SHORT).show()
    }
}

/** Shares [uri] (of [mimeType]) with other apps, but [alias] (the tool's own "Share to" entry: not back to it). */
fun shareFile(context: Context, uri: Uri, mimeType: String, alias: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        Intent.createChooser(send, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, alias)))
    )
}
