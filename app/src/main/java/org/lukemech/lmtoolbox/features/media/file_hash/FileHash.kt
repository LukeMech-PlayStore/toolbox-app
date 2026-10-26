package org.lukemech.lmtoolbox.features.media.file_hash

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.helpers.displayName

/** What's hashed: files picked or shared in, or text typed. */
enum class HashMode { Files, Text }

/**
 * Checksums of files (read through Android's content resolver, never whole in memory: see [MultiDigest]) and
 * of text; no Linux. What's open stays while the page is left (kept here, as the image resizer keeps its).
 */
object FileHash {
    /** A file to hash: its [name] and [size] (0: unknown), and its [hashes] so far. */
    data class Item(
        val uri: Uri,
        val name: String,
        val size: Long,
        val hashes: Map<HashAlgorithm, String> = emptyMap(),
        val state: State = State.Waiting,
        /** Why it couldn't be read, when it [State.Failed]. */
        val error: String? = null,
    )

    enum class State { Waiting, Hashing, Done, Failed, Stopped }

    // The page's, while it's left (see FileHashPage)
    var mode = HashMode.Files
    var files: List<Item> = emptyList()
    var text = ""
    var textHashes: Map<HashAlgorithm, String> = emptyMap()
    var showResults = false
    /** The hashes to check against, as pasted or loaded (and the checksum file's name, which can tell their algorithm). */
    var expectedText = ""
    var expectedFile: String? = null

    /** [uri] as a file to hash: its name and size, as its provider has them. */
    suspend fun describe(context: Context, uri: Uri): Item = withContext(Dispatchers.IO) {
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
            }
        }.getOrNull() ?: 0L
        Item(uri, displayName(context, uri) ?: uri.lastPathSegment ?: uri.toString(), size)
    }

    /**
     * [uri]'s hashes by [algorithms], in one pass over it; [onRead] with the bytes read so far. Stops (throwing
     * the CancellationException) as soon as its coroutine is cancelled, between pieces.
     */
    suspend fun hash(context: Context, uri: Uri, algorithms: Collection<HashAlgorithm>, onRead: (Long) -> Unit): Map<HashAlgorithm, String> =
        withContext(Dispatchers.IO) {
            val scope = this
            val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
            input.use { MultiDigest.digest(it, algorithms) { read -> scope.ensureActive(); onRead(read) } }
        }

    /** A checksum file's text (at most [MAX_CHECKSUM_FILE], it's a list of lines) and its name. */
    suspend fun readChecksumFile(context: Context, uri: Uri): Pair<String, String?> = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
        val text = input.use { it.reader(Charsets.UTF_8).buffered().readText(MAX_CHECKSUM_FILE) }
        text to displayName(context, uri)
    }

    /** [text] written to [uri] (an exported checksum file). */
    suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't write the file")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** The algorithms picked last (the [HashAlgorithm.DEFAULTS] at first), of those this device has. */
    fun algorithms(context: Context): Set<HashAlgorithm> {
        if (!PreferenceHelper.contains(context, FileHashFeature.S_ALGORITHMS)) return HashAlgorithm.DEFAULTS
        val names = PreferenceHelper.getStringList(context, FileHashFeature.S_ALGORITHMS)
        return HashAlgorithm.available.filter { it.name in names }.toSet()
    }

    fun saveAlgorithms(context: Context, algorithms: Set<HashAlgorithm>) =
        PreferenceHelper.putStringList(context, FileHashFeature.S_ALGORITHMS, algorithms.map { it.name })

    /** Bigger than any checksum file (in characters), small enough to read at once. */
    private const val MAX_CHECKSUM_FILE = 4 shl 20

    /** What's here, up to [max] characters: not a whole big file picked by mistake. */
    private fun Reader.readText(max: Int): String {
        val out = StringBuilder()
        val buffer = CharArray(8192)
        while (out.length < max) {
            val n = read(buffer, 0, minOf(buffer.size, max - out.length))
            if (n < 0) break
            out.appendRange(buffer, 0, n)
        }
        return out.toString()
    }
}
