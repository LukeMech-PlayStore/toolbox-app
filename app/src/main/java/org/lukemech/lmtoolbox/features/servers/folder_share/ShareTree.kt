package org.lukemech.lmtoolbox.features.servers.folder_share

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * What FolderShareServer serves: a folder's files and folders, found by the names of a path (only ever down from
 * the folder shared: the names come from Http.segments, which has no ".."), read and written. The app's is
 * [ShareTree], the folder the system's picker gave.
 */
interface ShareFiles {
    /** A file or folder of it: its id, name, type, size and when it last changed (0: not known). */
    data class Node(val id: String, val name: String, val mime: String, val size: Long, val modified: Long) {
        val folder get() = mime == FOLDER
    }

    /** A file opened: its size as it is now, and what reads it (a FileInputStream seeks; any other is skipped through). */
    class Opened(val size: Long, val stream: InputStream)

    /** The folder shared itself; null when it's gone, or not allowed anymore. */
    fun root(): Node?

    /** What's in [folder]. */
    fun children(folder: Node): List<Node>

    /** What [path] names, down from the folder shared (none: that one); null if there's nothing by that name. */
    fun resolve(path: List<String>): Node? {
        var node = root() ?: return null
        for (name in path) {
            if (!node.folder) return null
            node = children(node).firstOrNull { it.name == name } ?: return null
        }
        return node
    }

    /** Its folders changed (a file added): looked up afresh. */
    fun changed() {}

    /** [node]'s file to read; null if it can't be. */
    fun open(node: Node): Opened?

    /** A new file [name] (of type [mime]) in [folder]: its node and what writes it; null if it can't be made. */
    fun create(folder: Node, name: String, mime: String): Pair<Node, OutputStream>?

    /** Deletes [node] (an upload cut off). */
    fun delete(node: Node)

    companion object {
        /** A folder's type, as documents providers have it. */
        const val FOLDER = Document.MIME_TYPE_DIR
    }
}

/**
 * The folder shared, as its documents provider has it (the system's picker gave its tree, see ShareSettings);
 * a tree's documents are its own, none outside it. Straight through DocumentsContract, one query a folder
 * (DocumentFile asks again for each of a document's columns).
 */
class ShareTree(private val resolver: ContentResolver, private val treeUri: Uri) : ShareFiles {
    /** Paths looked up a moment ago, each a folder's query less as a video seeks (each seek a request of its own). */
    private val cache = ConcurrentHashMap<List<String>, Pair<Long, ShareFiles.Node>>()

    override fun root(): ShareFiles.Node? = runCatching {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        query(DocumentsContract.buildDocumentUriUsingTree(treeUri, id)).firstOrNull()
    }.getOrNull()

    override fun children(folder: ShareFiles.Node): List<ShareFiles.Node> = runCatching {
        query(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder.id))
    }.getOrDefault(emptyList())

    override fun resolve(path: List<String>): ShareFiles.Node? {
        val now = System.currentTimeMillis()
        cache[path]?.takeIf { now - it.first < CACHE_MS }?.let { return it.second }
        var node = root() ?: return null
        for ((i, name) in path.withIndex()) {
            if (!node.folder) return null
            val key = path.take(i + 1)
            node = cache[key]?.takeIf { now - it.first < CACHE_MS }?.second
                ?: children(node).firstOrNull { it.name == name }?.also { cache[key] = now to it }
                ?: return null
        }
        cache[path] = now to node
        return node
    }

    override fun changed() = cache.clear()

    private fun uri(node: ShareFiles.Node): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.id)

    override fun open(node: ShareFiles.Node): ShareFiles.Opened? = runCatching {
        val fd = resolver.openFileDescriptor(uri(node), "r") ?: return null
        // The file's own size, the provider's may be missing
        ShareFiles.Opened(fd.statSize.takeIf { it >= 0 } ?: node.size, ParcelFileDescriptor.AutoCloseInputStream(fd))
    }.getOrNull()

    override fun create(folder: ShareFiles.Node, name: String, mime: String): Pair<ShareFiles.Node, OutputStream>? = runCatching {
        val uri = DocumentsContract.createDocument(resolver, uri(folder), mime, name) ?: return null
        val node = query(uri).firstOrNull() ?: return null
        changed()
        val fd = resolver.openFileDescriptor(uri, "w") ?: return null
        node to ParcelFileDescriptor.AutoCloseOutputStream(fd)
    }.getOrNull()

    override fun delete(node: ShareFiles.Node) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri(node)) }
        changed()
    }

    private fun query(uri: Uri): List<ShareFiles.Node> =
        resolver.query(uri, COLUMNS, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ShareFiles.Node(
                            id = c.getString(0) ?: continue,
                            name = c.getString(1) ?: continue,
                            mime = c.getString(2) ?: Http.OCTET_STREAM,
                            size = if (c.isNull(3)) 0 else c.getLong(3),
                            modified = if (c.isNull(4)) 0 else c.getLong(4),
                        )
                    )
                }
            }
        }.orEmpty()

    private companion object {
        const val CACHE_MS = 5_000L
        val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED
        )
    }
}
