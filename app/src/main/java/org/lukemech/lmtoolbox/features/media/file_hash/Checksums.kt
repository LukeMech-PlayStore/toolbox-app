package org.lukemech.lmtoolbox.features.media.file_hash

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * A checksum the tool makes: [label] as people write it, [javaName] its MessageDigest name (none: CRC32,
 * java.util.zip's; see [newDigest]), [hexLength] its hex digits; [fileTag] in checksum files' names (e.g. "sha256" in SHA256SUMS,
 * "file.sha256"). In the order they're offered, the usual ones first.
 */
enum class HashAlgorithm(val label: String, val javaName: String?, val hexLength: Int, val fileTag: String) {
    MD5("MD5", "MD5", 32, "md5"),
    SHA1("SHA-1", "SHA-1", 40, "sha1"),
    SHA224("SHA-224", "SHA-224", 56, "sha224"),
    SHA256("SHA-256", "SHA-256", 64, "sha256"),
    SHA384("SHA-384", "SHA-384", 96, "sha384"),
    SHA512("SHA-512", "SHA-512", 128, "sha512"),
    SHA3_256("SHA3-256", "SHA3-256", 64, "sha3-256"),
    SHA3_512("SHA3-512", "SHA3-512", 128, "sha3-512"),
    CRC32("CRC32", null, 8, "crc32");

    /** A new digest of it (none: CRC32): the platform's, but SHA-3's the app's own ([SHA3]): Android's providers don't have it. */
    fun newDigest(): MessageDigest? = when (this) {
        SHA3_256, SHA3_512 -> SHA3(hexLength * 4)
        else -> javaName?.let(MessageDigest::getInstance)
    }

    /** As BSD's and OpenSSL's lines name it, e.g. "SHA256" in "SHA256 (file) = …": [label] without its dashes. */
    private val tag get() = label.replace("-", "").uppercase()

    companion object {
        /** What's computed when nothing's been picked yet. */
        val DEFAULTS = setOf(MD5, SHA1, SHA256)

        /** The ones a hash of [hexLength] digits may be (SHA-256 before SHA3-256: the likelier first). */
        fun byLength(hexLength: Int) = entries.filter { it.hexLength == hexLength }

        /** The one a BSD or OpenSSL line names ("SHA256", "SHA-256", "sha3-256", "MD5"...). */
        fun byTag(tag: String): HashAlgorithm? = tag.replace("-", "").uppercase().let { t -> entries.firstOrNull { it.tag == t } }

        /**
         * The one a checksum file's [name] tells (`SHA256SUMS`, `file.sha256`, `MD5SUMS`, `sha1sum.txt`...), if any. The
         * longer tags first, so "sha3-256" isn't taken for SHA-256.
         */
        fun byFileName(name: String): HashAlgorithm? {
            val lower = name.lowercase()
            return entries.sortedByDescending { it.fileTag.length }.firstOrNull { alg ->
                alg.fileTag in lower || alg.label.lowercase() in lower
            }
        }
    }
}

/** [bytes] as lowercase hex, two digits each. */
fun toHex(bytes: ByteArray): String {
    val digits = "0123456789abcdef"
    val out = CharArray(bytes.size * 2)
    bytes.forEachIndexed { i, b ->
        val v = b.toInt() and 0xff
        out[i * 2] = digits[v ushr 4]
        out[i * 2 + 1] = digits[v and 0x0f]
    }
    return String(out)
}

/**
 * All of [algorithms] in one go over what's fed in ([update]): each piece read once, into every digest. Nothing
 * of it is kept, so a file of any size goes through in [BUFFER] bytes at a time.
 */
class MultiDigest(algorithms: Collection<HashAlgorithm>) {
    private val digests = algorithms.mapNotNull { alg -> alg.newDigest()?.let { alg to it } }.toMap()
    private val crc = if (HashAlgorithm.CRC32 in algorithms) CRC32() else null

    fun update(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size) {
        digests.values.forEach { it.update(buffer, offset, length) }
        crc?.update(buffer, offset, length)
    }

    /** Each one's hash, as lowercase hex (CRC32: 8 digits, as SFV files and 7-Zip show it). */
    fun finish(): Map<HashAlgorithm, String> = buildMap {
        digests.forEach { (alg, digest) -> put(alg, toHex(digest.digest())) }
        crc?.let { put(HashAlgorithm.CRC32, "%08x".format(it.value)) }
    }

    companion object {
        /** How much is read at a time: big enough for speed, small next to the memory there is. */
        const val BUFFER = 1 shl 20

        /**
         * [input] read to its end through all of [algorithms]; [onRead] after each piece with how many bytes
         * so far (it can throw to stop, e.g. a canceled coroutine's ensureActive()). Doesn't close [input].
         */
        fun digest(input: InputStream, algorithms: Collection<HashAlgorithm>, onRead: (Long) -> Unit = {}): Map<HashAlgorithm, String> {
            val multi = MultiDigest(algorithms)
            val buffer = ByteArray(BUFFER)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                multi.update(buffer, 0, n)
                total += n
                onRead(total)
            }
            return multi.finish()
        }

        /** [text]'s hashes, of its UTF-8 bytes (as `printf %s text | sha256sum` has them). */
        fun ofText(text: String, algorithms: Collection<HashAlgorithm>): Map<HashAlgorithm, String> =
            MultiDigest(algorithms).apply { update(text.toByteArray(Charsets.UTF_8)) }.finish()
    }
}

/**
 * A hash to check against: [hash] (lowercase hex) of the file [name] (none: a hash alone, for whatever's
 * checked), by [algorithm] when its line or file says which; else its length tells (see [candidates]).
 */
data class ExpectedHash(val hash: String, val name: String? = null, val algorithm: HashAlgorithm? = null) {
    /** What it may be a hash by: the one it says, else those of its length. */
    val candidates get() = algorithm?.let { listOf(it) } ?: HashAlgorithm.byLength(hash.length)
}

/** Reading the hashes to check against: pasted ones, or a checksum file's. */
object Checksums {
    // BSD (`sha256 -r` aside) and OpenSSL: "SHA256 (name) = hash", "SHA256(name)= hash"
    private val BSD = Regex("""^([A-Za-z0-9-]+) ?\((.*)\) ?= ?([0-9A-Fa-f]+)$""")
    // GNU coreutils: "hash  name" (text mode) or "hash *name" (binary); a backslash first when the name is escaped
    private val GNU = Regex("""^\\?([0-9A-Fa-f]+) [ *](.+)$""")
    // A hash alone, as a .sha256 file or a web page may have it; spaces or colons between its bytes too
    private val BARE = Regex("""^(?:0x)?([0-9A-Fa-f]{2}(?:[ :]?[0-9A-Fa-f]{2})*)$""")

    /**
     * The hashes in [text] (a checksum file, or what was pasted): GNU coreutils lines (`sha256sum`'s), BSD or
     * OpenSSL tagged ones, or bare hashes; blank lines, comments and what's none of these left out. [fileName]
     * (the checksum file's, e.g. SHA256SUMS, file.md5) tells the algorithm of lines that don't say it.
     */
    fun parse(text: String, fileName: String? = null): List<ExpectedHash> {
        val fromName = fileName?.let(HashAlgorithm::byFileName)
        return text.lineSequence().map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }.mapNotNull { line ->
            BSD.matchEntire(line)?.let { m ->
                val alg = HashAlgorithm.byTag(m.groupValues[1]) ?: return@let null
                return@mapNotNull ExpectedHash(m.groupValues[3].lowercase(), m.groupValues[2], alg).takeIf { it.hash.length == alg.hexLength }
            }
            GNU.matchEntire(line)?.let { m ->
                val name = m.groupValues[2].let { if (line.startsWith("\\")) unescape(it) else it }
                return@mapNotNull ExpectedHash(m.groupValues[1].lowercase(), name, fromName).takeIf(::isKnownLength)
            }
            BARE.matchEntire(line)?.let { m ->
                ExpectedHash(m.groupValues[1].filter { it != ' ' && it != ':' }.lowercase(), null, fromName).takeIf(::isKnownLength)
            }
        }.toList()
    }

    /** Whether its length is one a hash has (the algorithm's own, when it says one). */
    private fun isKnownLength(expected: ExpectedHash) = expected.candidates.isNotEmpty()

    /** A GNU line's escaped name: "\\" and "\n" back to what they stand for. */
    private fun unescape(name: String) = buildString {
        var i = 0
        while (i < name.length) {
            val c = name[i]
            if (c == '\\' && i + 1 < name.length) {
                when (name[i + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(name[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    /**
     * The ones of [expected] for the file [name]: those naming it (by its name alone, as a checksum file's
     * "./dir/name" or "*name" lines have it; else ignoring case), else the hashes alone; a list of one named
     * hash stands for any file too (e.g. one renamed since). Null [name] (typed text): the hashes alone.
     */
    fun forName(expected: List<ExpectedHash>, name: String?): List<ExpectedHash> {
        if (name != null) {
            val named = expected.filter { it.name != null && baseName(it.name) == name }
                .ifEmpty { expected.filter { it.name != null && baseName(it.name).equals(name, ignoreCase = true) } }
            if (named.isNotEmpty()) return named
        }
        return expected.filter { it.name == null }.ifEmpty { if (expected.size == 1) expected else emptyList() }
    }

    private fun baseName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

    /**
     * What [computed] (a file's or text's hashes) comes to against [expected] (see [forName]): the
     * algorithms whose hash is one of them, those whose hash is none of them, and those still to be
     * computed to tell (an expected hash none computed could be by).
     */
    fun verify(computed: Map<HashAlgorithm, String>, expected: List<ExpectedHash>): Verdict {
        val matched = mutableSetOf<HashAlgorithm>()
        val mismatched = mutableSetOf<HashAlgorithm>()
        val missing = mutableSetOf<HashAlgorithm>()
        for (e in expected) {
            val tried = e.candidates.filter { it in computed }
            val hit = tried.filter { computed[it].equals(e.hash, ignoreCase = true) }
            when {
                hit.isNotEmpty() -> matched += hit
                tried.isNotEmpty() -> mismatched += tried
                else -> missing += e.candidates.take(1)
            }
        }
        // A row one hash matched isn't a mismatch for another (e.g. a checksum file with two lines for a name)
        return Verdict(matched, mismatched - matched, missing - matched - mismatched)
    }

    /** What [verify] found: [matched] and [mismatched] rows, and the [missing] ones to compute to tell. */
    data class Verdict(val matched: Set<HashAlgorithm>, val mismatched: Set<HashAlgorithm>, val missing: Set<HashAlgorithm>) {
        val isMatch get() = matched.isNotEmpty() && mismatched.isEmpty()
        val isMismatch get() = mismatched.isNotEmpty()
    }

    /**
     * [hashes] (a file name and its hash each) as a GNU coreutils checksum file, as `sha256sum` writes it
     * and checks it (`sha256sum -c`): "hash  name" lines, a name with a backslash or new line escaped.
     */
    fun format(hashes: List<Pair<String, String>>): String = hashes.joinToString("") { (name, hash) ->
        if ('\\' in name || '\n' in name || '\r' in name) {
            "\\$hash  " + name.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r") + "\n"
        } else "$hash  $name\n"
    }
}
