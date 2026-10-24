package org.lukemech.lmtoolbox.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import java.io.File

/**
 * Where a tool saves what it makes: its own place (e.g. Downloads), or a folder picked by the user,
 * kept in [key] with the app's permission to write there. Each tool has its own.
 */
class OutputFolder(private val key: String) {
    /** The picked folder, while the app may still write there; null: the tool's own place. */
    fun uri(context: Context): Uri? {
        val uri = PreferenceHelper.get(context, key, "").ifEmpty { null }?.toUri() ?: return null
        return uri.takeIf { context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission } }
    }

    /** Saves to [tree] (picked with OpenDocumentTree) from now on, or to the tool's own place when null. */
    fun set(context: Context, tree: Uri?) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val previous = PreferenceHelper.get(context, key, "")
        tree?.let { resolver.takePersistableUriPermission(it, flags) }
        PreferenceHelper.put(context, key, tree?.toString().orEmpty())
        // The previous folder's permission goes, unless another tool saves there too
        if (previous.isNotEmpty() && previous != tree?.toString() && previous !in PreferenceHelper.values(context)) {
            runCatching { resolver.releasePersistableUriPermission(previous.toUri(), flags) }
        }
    }

    /** The picked folder's name as the files app shows it, or [default] (the tool's own place). */
    fun name(context: Context, default: String): String {
        val tree = uri(context) ?: return default
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return runCatching {
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/')
    }

    /** Copies [file] into the picked folder (streamed, it can be big); null when none is picked. */
    fun save(context: Context, file: File, mimeType: String): Uri? {
        val tree = uri(context) ?: return null
        val resolver = context.contentResolver
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val uri = DocumentsContract.createDocument(resolver, folder, mimeType, file.name)
            ?: error("Couldn't create the file in the chosen folder")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
        return uri
    }

    /**
     * Where it saves, as a setting: [default] (the tool's own place) or a folder picked here, and
     * back. [title]: e.g. "Save to"; [reset]: the description of going back to [default].
     */
    @Composable
    fun Setting(title: String, default: String, reset: String) {
        val context = LocalContext.current
        var picked by remember { mutableStateOf(uri(context)) }

        fun pick(tree: Uri?) {
            set(context, tree)
            picked = uri(context)
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(::pick) }

        FeatureButton(
            title = title,
            subtitle = remember(picked, default) { name(context, default) },
            icon = Icons.Default.FolderOpen,
            buttonContent = {
                if (picked != null) {
                    IconButton(onClick = { pick(null) }) {
                        Icon(Icons.AutoMirrored.Default.Undo, contentDescription = reset)
                    }
                }
            },
            onClick = { picker.launch(picked) }
        )
    }
}
