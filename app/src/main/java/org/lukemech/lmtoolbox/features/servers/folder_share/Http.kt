package org.lukemech.lmtoolbox.features.servers.folder_share

import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A request's head as it came: its method, target (its path still encoded, and query), version and headers (names in lowercase). */
data class Request(val method: String, val target: String, val version: String, val headers: Map<String, String>) {
    val path get() = target.substringBefore('?')
    val query get() = target.substringAfter('?', "")

    fun header(name: String): String? = headers[name.lowercase()]

    /** Whether the client keeps its connection for the next request: HTTP/1.1's unless it says close, 1.0's if it asks for keep-alive. */
    val keepAlive: Boolean
        get() {
            val connection = header("connection").orEmpty()
            return if (version == "HTTP/1.0") connection.contains("keep-alive", ignoreCase = true)
            else !connection.contains("close", ignoreCase = true)
        }
}

/** A request the server can't make sense of (answered 400, the connection closed). */
class BadRequest(message: String) : IOException(message)

/**
 * The folder share's HTTP, what of it needs no Android (tested as it is): reading a request, its path as the
 * folder's names (never out of it), byte ranges, file types, Basic authentication, and what's written back.
 */
object Http {
    /** A request line or header's longest (browsers' cookies included). */
    const val MAX_LINE = 16 * 1024
    const val MAX_HEADERS = 100

    /**
     * Reads a request's head from [input], up to the empty line after its headers; null if the client closed
     * the connection before another. A malformed one throws [BadRequest].
     */
    fun readRequest(input: InputStream): Request? {
        var line = readLine(input) ?: return null
        // A stray empty line between requests is allowed before one
        if (line.isEmpty()) line = readLine(input) ?: return null
        val parts = line.split(' ')
        if (parts.size != 3 || parts[0].isEmpty() || !parts[2].startsWith("HTTP/1.")) throw BadRequest("Bad request line")
        val headers = mutableMapOf<String, String>()
        var count = 0
        while (true) {
            val h = readLine(input) ?: throw BadRequest("Headers cut off")
            if (h.isEmpty()) break
            if (++count > MAX_HEADERS) throw BadRequest("Too many headers")
            val colon = h.indexOf(':')
            if (colon <= 0) throw BadRequest("Bad header")
            val name = h.substring(0, colon).trim().lowercase()
            val value = h.substring(colon + 1).trim()
            // Repeated: as one, comma-separated (as HTTP allows)
            headers[name] = headers[name]?.let { "$it, $value" } ?: value
        }
        return Request(parts[0], parts[1], parts[2], headers)
    }

    /** A line, ending at CRLF (or LF), as ISO-8859-1 (HTTP's own); null at the end of the stream before any of it. */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (line.isEmpty()) return null
                throw BadRequest("Line cut off")
            }
            if (b == '\n'.code) break
            if (line.length >= MAX_LINE) throw BadRequest("Line too long")
            line.append(b.toChar())
        }
        if (line.endsWith('\r')) line.setLength(line.length - 1)
        return line.toString()
    }

    /**
     * The names [path] goes down through from the folder shared, decoded ("/" is the folder itself: none); null
     * if it isn't one of its paths: not absolute, an empty name ("//"), a name of one dot or two, a name with a slash, a
     * backslash or a NUL in it once decoded, or an escape that's broken (or not UTF-8). A path ending in "/"
     * is a folder's; that's left to the caller (see [isFolderPath]).
     */
    fun segments(path: String): List<String>? {
        if (!path.startsWith('/')) return null
        val raw = path.substring(1).removeSuffix("/")
        if (raw.isEmpty()) return emptyList()
        return raw.split('/').map { part ->
            val name = decode(part) ?: return null
            if (name.isEmpty() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' }) return null
            name
        }
    }

    /** Whether [path] names a folder (ends in "/"). */
    fun isFolderPath(path: String) = path.endsWith('/')

    /** [s] with its %XX escapes decoded as UTF-8 ("+" stays: it's a query's space, not a path's); null if one's broken. */
    fun decode(s: String): String? {
        if ('%' !in s) return s
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                if (i + 2 >= s.length) return null
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi < 0 || lo < 0) return null
                bytes.write(hi * 16 + lo)
                i += 3
            } else {
                // What's between escapes as it is (a pair of surrogates whole)
                val next = s.indexOf('%', i).let { if (it < 0) s.length else it }
                bytes.write(s.substring(i, next).toByteArray(Charsets.UTF_8))
                i = next
            }
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /** A name as a path's segment: all but the unreserved characters (letters, digits, -._~) escaped, as UTF-8. */
    fun encodeSegment(name: String): String = buildString {
        for (b in name.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code) {
                append(c.toChar())
            } else {
                append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
    }

    /** The path of [segments] ([folder]: with a "/" at its end), each escaped. */
    fun path(segments: List<String>, folder: Boolean): String =
        "/" + segments.joinToString("/") { encodeSegment(it) } + if (folder && segments.isNotEmpty()) "/" else ""

    /** A Range header's answer, for a file of a size. */
    sealed interface Range {
        /** None asked for (or one not understood, which HTTP lets be): the whole file. */
        data object Whole : Range

        /** Bytes [start] to [end], both included. */
        data class Part(val start: Long, val end: Long) : Range {
            val length get() = end - start + 1
        }

        /** Nothing of it there (416). */
        data object Unsatisfiable : Range
    }

    /**
     * What [header] (e.g. "bytes=0-1023", "bytes=500-", "bytes=-500") asks of a file of [size] bytes. One range
     * alone: several (rare, a player asks for one) get the whole file, as HTTP lets a server do; so do other
     * units and what isn't a range at all.
     */
    fun range(header: String?, size: Long): Range {
        val spec = header?.trim() ?: return Range.Whole
        if (!spec.startsWith("bytes=", ignoreCase = true)) return Range.Whole
        val value = spec.substring(6).trim()
        if (',' in value) return Range.Whole
        val dash = value.indexOf('-')
        if (dash < 0) return Range.Whole
        val first = value.substring(0, dash).trim()
        val last = value.substring(dash + 1).trim()
        fun number(s: String) = s.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
        if (first.isEmpty()) {
            // The last n bytes
            val n = number(last) ?: return Range.Whole
            if (n == 0L || size == 0L) return Range.Unsatisfiable
            return Range.Part((size - n).coerceAtLeast(0), size - 1)
        }
        val start = number(first) ?: return Range.Whole
        val end = if (last.isEmpty()) null else number(last) ?: return Range.Whole
        if (end != null && end < start) return Range.Whole
        if (start >= size) return Range.Unsatisfiable
        return Range.Part(start, (end ?: Long.MAX_VALUE).coerceAtMost(size - 1))
    }

    /** "Content-Range" of [part] of a file of [size] bytes. */
    fun contentRange(part: Range.Part, size: Long) = "bytes ${part.start}-${part.end}/$size"

    /**
     * The media type of a file named [name], by its extension (any case), for browsers to show or play it; a text's
     * says it's UTF-8. One not known: [fallback], else application/octet-stream (downloaded).
     */
    fun mime(name: String, fallback: String? = null): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val type = MIME[ext] ?: fallback?.takeIf { it.isNotBlank() && it != OCTET_STREAM } ?: OCTET_STREAM
        return if (type.startsWith("text/") || type == "application/json" || type == "application/javascript") "$type; charset=utf-8" else type
    }

    const val OCTET_STREAM = "application/octet-stream"

    /** Types whose files are documents a browser runs scripts in: served sandboxed (see FolderShareServer). */
    fun isActiveContent(mime: String): Boolean = mime.substringBefore(';').trim().let {
        it == "text/html" || it == "application/xhtml+xml" || it == "image/svg+xml" || it == "text/xml" || it == "application/xml"
    }

    /** Whether a file of [mime] is worth compressing in a ZIP (not what's compressed already: pictures, video, sound, archives). */
    fun isCompressible(mime: String): Boolean = mime.substringBefore(';').trim().let { m ->
        m.startsWith("text/") || m in COMPRESSIBLE
    }

    /**
     * Whether an Authorization [header] ("Basic <base64 of user:password>") has [user] and [password]; an
     * empty [user] takes any name. Compared in constant time.
     */
    fun authorized(header: String?, user: String, password: String): Boolean {
        val h = header?.trim() ?: return false
        if (!h.startsWith("Basic ", ignoreCase = true)) return false
        val decoded = try {
            String(Base64.getDecoder().decode(h.substring(6).trim()), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val colon = decoded.indexOf(':')
        if (colon < 0) return false
        val userOk = user.isEmpty() || same(decoded.substring(0, colon), user)
        // Both compared always: how long it takes says nothing of which was wrong
        val passwordOk = same(decoded.substring(colon + 1), password)
        return userOk and passwordOk
    }

    private fun same(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /** The WWW-Authenticate header asking for [realm]'s user and password, as UTF-8. */
    fun challenge(realm: String) = "Basic realm=\"${quoted(realm)}\", charset=\"UTF-8\""

    /**
     * Content-Disposition for a file named [name], to download it ([attachment]) or show it: an ASCII name for
     * old clients (others as "_"), and the name itself (RFC 6266's filename*).
     */
    fun contentDisposition(name: String, attachment: Boolean): String {
        val ascii = name.map { if (it.code in 0x20..0x7E) it else '_' }.joinToString("")
        return (if (attachment) "attachment" else "inline") + "; filename=\"${quoted(ascii)}\"; filename*=UTF-8''${encodeSegment(name)}"
    }

    /** [s] in a quoted string: its quotes and backslashes escaped, line breaks gone. */
    private fun quoted(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace(Regex("[\\r\\n]"), " ")

    /**
     * [name], or one like it nothing has yet ([taken]): "photo (1).jpg", "photo (2).jpg"... (its extension kept;
     * a name starting with a dot has none).
     */
    fun freeName(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var n = 1
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!taken(candidate)) return candidate
            n++
        }
    }

    /** A date as HTTP writes it (RFC 9110's IMF-fixdate), e.g. "Sun, 06 Nov 1994 08:49:37 GMT". */
    fun date(millis: Long): String = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }.format(Date(millis))

    /** A status code's reason phrase. */
    fun reason(status: Int) = when (status) {
        100 -> "Continue"
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        206 -> "Partial Content"
        301 -> "Moved Permanently"
        304 -> "Not Modified"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        411 -> "Length Required"
        413 -> "Content Too Large"
        416 -> "Range Not Satisfiable"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        else -> "Status $status"
    }

    private const val HEX = "0123456789ABCDEF"

    private val COMPRESSIBLE = setOf(
        "application/json", "application/javascript", "application/xml", "image/svg+xml", "image/bmp", "image/tiff",
        "application/x-tar", "application/rtf", "application/x-sh", "audio/wav", "application/sql"
    )

    /** By extension: what's shown or played in a browser first (video, sound, pictures, documents), then what's common. */
    private val MIME = mapOf(
        // Video
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mov" to "video/quicktime", "webm" to "video/webm",
        "mkv" to "video/x-matroska", "avi" to "video/x-msvideo", "3gp" to "video/3gpp", "ts" to "video/mp2t",
        "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "ogv" to "video/ogg", "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv",
        // Sound
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac", "ogg" to "audio/ogg",
        "oga" to "audio/ogg", "opus" to "audio/ogg", "wav" to "audio/wav", "weba" to "audio/webm", "mid" to "audio/midi",
        "midi" to "audio/midi", "amr" to "audio/amr", "wma" to "audio/x-ms-wma",
        // Pictures
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
        "avif" to "image/avif", "heic" to "image/heic", "heif" to "image/heif", "bmp" to "image/bmp", "svg" to "image/svg+xml",
        "ico" to "image/x-icon", "tif" to "image/tiff", "tiff" to "image/tiff", "dng" to "image/x-adobe-dng",
        // Documents and text
        "pdf" to "application/pdf", "txt" to "text/plain", "log" to "text/plain", "md" to "text/markdown", "csv" to "text/csv",
        "html" to "text/html", "htm" to "text/html", "css" to "text/css", "js" to "application/javascript",
        "json" to "application/json", "xml" to "application/xml", "srt" to "text/plain", "vtt" to "text/vtt",
        "rtf" to "application/rtf", "epub" to "application/epub+zip",
        "doc" to "application/msword", "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text", "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        // Archives and the rest
        "zip" to "application/zip", "7z" to "application/x-7z-compressed", "rar" to "application/vnd.rar",
        "tar" to "application/x-tar", "gz" to "application/gzip", "tgz" to "application/gzip", "xz" to "application/x-xz",
        "bz2" to "application/x-bzip2", "apk" to "application/vnd.android.package-archive", "iso" to "application/x-iso9660-image",
        "jar" to "application/java-archive", "sh" to "application/x-sh", "sql" to "application/sql",
        "ttf" to "font/ttf", "otf" to "font/otf", "woff" to "font/woff", "woff2" to "font/woff2",
    )
}

/**
 * HTTP/1.1's chunked encoding over [out], for what's written as it's made (a folder's ZIP) with no length known
 * first; closing it writes the last chunk (and leaves [out] open, for the connection's next response).
 */
class ChunkedOutputStream(out: OutputStream) : FilterOutputStream(out) {
    private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len == 0) return
        check(!closed) { "Closed" }
        out.write("${Integer.toHexString(len)}\r\n".toByteArray(Charsets.US_ASCII))
        out.write(b, off, len)
        out.write(CRLF)
    }

    override fun close() {
        if (closed) return
        closed = true
        out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private companion object {
        val CRLF = "\r\n".toByteArray(Charsets.US_ASCII)
    }
}
