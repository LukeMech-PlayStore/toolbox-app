package org.lukemech.lmtoolbox.features.servers.folder_share

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

/**
 * The folder share's web server: HTTP/1.1 on [port] (every address of the phone's), [tree]'s files and folders
 * to whoever asks (as [settings] are when they ask: a password, uploads). A folder is a page of what's in it
 * (Listing), or with "?zip" all of it as a ZIP, made as it's sent; a file is sent as it's read (a range of it
 * when asked: video and sound seek), "?download" to save it. With uploads on, a file PUT into a folder is
 * written there (under a name nothing has yet). Each request, once answered, goes to [onServed]; [onStarted]
 * is told as one comes in (see FolderShare's idle stop).
 *
 * A thread a connection (up to [MAX_CONNECTIONS]: a browser opens six at most, a player one or two), kept for
 * the client's next request until it's quiet for [IDLE_TIMEOUT_MS].
 */
class FolderShareServer(
    private val tree: ShareFiles,
    private val port: Int,
    private val labels: Listing.Labels,
    private val settings: () -> ShareSettings,
    private val onStarted: () -> Unit,
    private val onServed: (Access) -> Unit,
) {
    /** Through what a request came: the phone's network, its tailnet, bore's tunnel or Cloudflare's. */
    enum class Via { Network, Tailscale, Bore, Cloudflare }

    /**
     * A request answered: who asked ([client], an address; bore's tunnel tells none), what, and how it went;
     * [id] is the share's, as it keeps them (see FolderShare.Run.recent).
     */
    data class Access(
        val at: Long, val client: String?, val via: Via, val method: String, val path: String, val status: Int, val bytes: Long,
        val id: Long = 0,
    )

    private val server = ServerSocket()
    private val pool = ThreadPoolExecutor(0, MAX_CONNECTIONS, 30, TimeUnit.SECONDS, SynchronousQueue())
    private val open: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var closed = false

    /** The port it listens on (0 asked for: the one it was given). */
    val localPort get() = server.localPort

    /** Listens, or throws (e.g. a BindException: the port's someone else's). */
    fun start() {
        server.reuseAddress = true
        server.bind(InetSocketAddress(port))
        thread(name = "FolderShare-accept", isDaemon = true) {
            while (!closed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                try {
                    pool.execute { serve(socket) }
                } catch (_: RejectedExecutionException) {
                    // As many as it takes already: told to come back
                    runCatching {
                        socket.getOutputStream().write("HTTP/1.1 503 Service Unavailable\r\nRetry-After: 2\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.close()
                    }
                }
            }
        }
    }

    /** Stops listening, and drops every connection (a download under way too). */
    fun stop() {
        closed = true
        runCatching { server.close() }
        open.forEach { runCatching { it.close() } }
        pool.shutdownNow()
    }

    private fun serve(socket: Socket) {
        open += socket
        try {
            socket.soTimeout = IDLE_TIMEOUT_MS
            socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream(), 16 * 1024)
            val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            while (!closed) {
                val request = try {
                    Http.readRequest(input)
                } catch (e: BadRequest) {
                    Exchange(socket, Request("GET", "/", "HTTP/1.1", emptyMap()), output).text(400, e.message ?: "Bad request", keepAlive = false)
                    output.flush()
                    break
                } ?: break
                onStarted()
                val exchange = Exchange(socket, request, output)
                val again = try {
                    handle(exchange, input)
                } finally {
                    // Told even when the client's gone meanwhile (the idle stop counts what's under way)
                    try {
                        output.flush()
                    } finally {
                        onServed(exchange.access())
                    }
                }
                if (!again || !request.keepAlive) break
            }
        } catch (_: IOException) {
            // The client gone (or quiet too long): nothing to tell it
        } catch (e: Exception) {
            Log.w(TAG, "A connection failed", e)
        } finally {
            if (!closed) linger(socket)
            runCatching { socket.close() }
            open -= socket
        }
    }

    /**
     * Before [socket]'s closed: the answer sent on, what the client's still sending (a body left unread, e.g. a PUT
     * turned down) read and dropped, up to [LINGER_BYTES] or [LINGER_MS]. Closed with it unread, the system resets the
     * connection, and the client can lose the answer it hadn't read yet.
     */
    private fun linger(socket: Socket) = runCatching {
        if (socket.isClosed || socket.isInputShutdown) return@runCatching
        socket.shutdownOutput()
        socket.soTimeout = LINGER_MS
        val input = socket.getInputStream()
        val buffer = ByteArray(BUFFER)
        var left = LINGER_BYTES
        val until = System.currentTimeMillis() + LINGER_MS
        while (left > 0 && System.currentTimeMillis() < until) {
            val n = input.read(buffer)
            if (n < 0) break
            left -= n
        }
    }

    /** Answers [ex]'s request; whether the connection can take another (not after a body left unread). */
    private fun handle(ex: Exchange, input: InputStream): Boolean {
        val s = settings()
        val method = ex.request.method
        if (s.protected && !Http.authorized(ex.request.header("authorization"), s.user, s.password)) {
            ex.text(401, "Unauthorized", headers = listOf("WWW-Authenticate" to Http.challenge(labels.home)), keepAlive = method != "PUT")
            return method != "PUT"
        }
        return when (method) {
            "GET", "HEAD" -> {
                get(ex, head = method == "HEAD")
                true
            }
            "PUT" -> put(ex, input, s)
            else -> {
                ex.text(405, "Method not allowed", headers = listOf("Allow" to if (s.uploads) "GET, HEAD, PUT" else "GET, HEAD"), keepAlive = false)
                false
            }
        }
    }

    private fun get(ex: Exchange, head: Boolean) {
        val path = ex.request.path
        val segments = Http.segments(path) ?: return ex.text(400, "Bad path")
        val node = tree.resolve(segments) ?: return ex.text(404, "Not found")
        val query = ex.request.query
        // A folder's page at its path with a slash (its links are relative to it), a file's without
        if (node.folder != Http.isFolderPath(path) && segments.isNotEmpty()) {
            val location = Http.path(segments, node.folder) + if (query.isNotEmpty()) "?$query" else ""
            return ex.text(301, location, headers = listOf("Location" to location))
        }
        when {
            !node.folder -> file(ex, node, attachment = query == "download", head = head)
            query == "zip" -> zip(ex, node, head)
            else -> {
                val entries = tree.children(node).map { Listing.Entry(it.name, it.folder, it.size, it.modified) }
                val page = Listing.page(segments, entries, labels, uploads = settings().uploads).toByteArray(Charsets.UTF_8)
                ex.send(
                    200, listOf("Content-Type" to "text/html; charset=utf-8", "Cache-Control" to "no-cache", "Content-Security-Policy" to PAGE_POLICY),
                    page, head
                )
            }
        }
    }

    /** [node]'s file, whole or the range asked for (seeking in it, not reading up to it). */
    private fun file(ex: Exchange, node: ShareFiles.Node, attachment: Boolean, head: Boolean) {
        val opened = tree.open(node) ?: return ex.text(404, "Not found")
        opened.stream.use { stream ->
            val size = opened.size
            val etag = "\"${size.toString(36)}-${node.modified.toString(36)}\""
            val modified = node.modified.takeIf { it > 0 }?.let { Http.date(it) }
            val mime = Http.mime(node.name, node.mime)
            val headers = mutableListOf(
                "Content-Type" to mime,
                "Accept-Ranges" to "bytes",
                "ETag" to etag,
                "Cache-Control" to "no-cache",
                "Content-Disposition" to Http.contentDisposition(node.name, attachment),
            )
            modified?.let { headers += "Last-Modified" to it }
            // A page (or an SVG) of the folder's runs in a sandbox of its own, not as the share (its uploads, its password)
            if (Http.isActiveContent(mime)) headers += "Content-Security-Policy" to "sandbox"
            if (ex.request.header("if-none-match") == etag) return ex.send(304, headers, null, head = true)

            var range = Http.range(ex.request.header("range"), size)
            // Changed since the part the client has: all of it again
            ex.request.header("if-range")?.let { if (it != etag && it != modified) range = Http.Range.Whole }
            val (status, start, length) = when (val r = range) {
                Http.Range.Unsatisfiable ->
                    return ex.text(416, "Range not satisfiable", headers = listOf("Content-Range" to "bytes */$size"))
                is Http.Range.Part -> {
                    headers += "Content-Range" to Http.contentRange(r, size)
                    Triple(206, r.start, r.length)
                }
                Http.Range.Whole -> Triple(200, 0L, size)
            }
            ex.stream(status, headers, length, head) { out ->
                if (start > 0) {
                    // A pipe can't seek: read past it
                    runCatching { (stream as FileInputStream).channel.position(start) }.onFailure { stream.skipFully(start) }
                }
                copy(stream, out, length)
            }
        }
    }

    /** All of [folder] as a ZIP, made as it's sent (chunked: its size isn't known first); what's compressed already is stored. */
    private fun zip(ex: Exchange, folder: ShareFiles.Node, head: Boolean) {
        val name = (if (folder.id == tree.root()?.id) labels.home else folder.name).ifEmpty { "folder" } + ".zip"
        val headers = listOf("Content-Type" to "application/zip", "Content-Disposition" to Http.contentDisposition(name, attachment = true))
        ex.stream(200, headers, length = null, head) { out ->
            val zip = ZipOutputStream(BufferedOutputStream(out, 64 * 1024))
            fun add(node: ShareFiles.Node, prefix: String) {
                val children = tree.children(node)
                // An empty folder kept as one
                if (children.isEmpty() && prefix.isNotEmpty()) {
                    zip.putNextEntry(ZipEntry(prefix))
                    zip.closeEntry()
                }
                for (child in children) {
                    if (closed) throw IOException("Stopped")
                    if (child.folder) {
                        add(child, prefix + child.name + "/")
                        continue
                    }
                    val entry = ZipEntry(prefix + child.name).apply { if (child.modified > 0) time = child.modified }
                    // Stored as it is: its size and CRC first, read once before (a STORED entry has them in its header).
                    // Not a deflate level of none: changing the level between entries (deflateParams) makes broken
                    // data on some Android versions' zlib (x86 API 30's: "invalid block type").
                    if (!Http.isCompressible(Http.mime(child.name, child.mime))) {
                        val crc = CRC32()
                        val size = tree.open(child)?.stream?.use { stream ->
                            val buffer = ByteArray(BUFFER)
                            var total = 0L
                            while (true) {
                                val n = stream.read(buffer)
                                if (n < 0) break
                                crc.update(buffer, 0, n)
                                total += n
                            }
                            total
                        } ?: continue
                        entry.method = ZipEntry.STORED
                        entry.size = size
                        entry.compressedSize = size
                        entry.crc = crc.value
                    }
                    val opened = tree.open(child) ?: continue
                    opened.stream.use { stream ->
                        zip.putNextEntry(entry)
                        // Changed since: closeEntry throws, its size or CRC another, the ZIP cut short
                        if (entry.method == ZipEntry.STORED) copy(stream, zip, entry.size) else stream.copyTo(zip, BUFFER)
                        zip.closeEntry()
                    }
                }
            }
            add(folder, "")
            zip.finish()
            zip.flush()
        }
    }

    /** A file PUT into a folder (uploads on): written there under its name, or one like it if that's taken. */
    private fun put(ex: Exchange, input: InputStream, s: ShareSettings): Boolean {
        if (!s.uploads) return false.also { ex.text(403, "Uploads are off", keepAlive = false) }
        val segments = Http.segments(ex.request.path)?.takeIf { it.isNotEmpty() && !Http.isFolderPath(ex.request.path) }
            ?: return false.also { ex.text(400, "Bad path", keepAlive = false) }
        val folder = tree.resolve(segments.dropLast(1))?.takeIf { it.folder }
            ?: return false.also { ex.text(404, "No such folder", keepAlive = false) }
        val length = ex.request.header("content-length")?.toLongOrNull()?.takeIf { it >= 0 && ex.request.header("transfer-encoding") == null }
            ?: return false.also { ex.text(411, "Length required", keepAlive = false) }
        // curl and the like wait to be told to send it
        if (ex.request.header("expect").equals("100-continue", ignoreCase = true)) {
            ex.out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
            ex.out.flush()
        }
        val taken = tree.children(folder).map { it.name }.toSet()
        val name = Http.freeName(segments.last()) { it in taken }
        val (node, stream) = tree.create(folder, name, Http.mime(name).substringBefore(';'))
            ?: return false.also { ex.text(500, "Couldn't create $name", keepAlive = false) }
        val complete = stream.use { out -> copy(input, out, length) == length }
        if (!complete) {
            // Cut off: nothing half there
            tree.delete(node)
            return false
        }
        ex.received = length
        ex.text(201, node.name, headers = listOf("Location" to Http.path(segments.dropLast(1) + node.name, folder = false)))
        return true
    }

    /** Copies [length] bytes (or until the end); how many it did. */
    private fun copy(from: InputStream, to: OutputStream, length: Long): Long {
        val buffer = ByteArray(BUFFER)
        var left = length
        while (left > 0) {
            val n = from.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) break
            to.write(buffer, 0, n)
            left -= n
        }
        return length - left
    }

    private fun InputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) throw IOException("Shorter than its range")
                left--
            } else left -= skipped
        }
    }

    /** A request and its answer as it's written: its status, and what went each way, for [access]. */
    private inner class Exchange(private val socket: Socket, val request: Request, val out: OutputStream) {
        private var status = 0
        private var sent = 0L
        var received = 0L

        fun access(): Access {
            val (client, via) = client()
            val shown = Http.segments(request.path)?.let { "/" + it.joinToString("/") } ?: request.path
            return Access(System.currentTimeMillis(), client, via, request.method, shown, status, maxOf(sent, received))
        }

        /** Who asked: through a tunnel it comes from the phone itself, cloudflared telling whose it is. */
        private fun client(): Pair<String?, Via> {
            val address = socket.inetAddress
            if (address.isLoopbackAddress) {
                return request.header("cf-connecting-ip")?.let { it to Via.Cloudflare } ?: (null to Via.Bore)
            }
            val ip = address.hostAddress?.removePrefix("::ffff:")
            val tailnet = (address as? Inet4Address)?.address?.let { it[0].toInt() and 0xFF == 100 && it[1].toInt() and 0xC0 == 64 } == true
            return ip to if (tailnet) Via.Tailscale else Via.Network
        }

        /** A short text answer (an error, a redirect's address). */
        fun text(status: Int, text: String, headers: List<Pair<String, String>> = emptyList(), keepAlive: Boolean = true) =
            send(status, headers + ("Content-Type" to "text/plain; charset=utf-8"), (text + "\n").toByteArray(Charsets.UTF_8), request.method == "HEAD", keepAlive)

        fun send(status: Int, headers: List<Pair<String, String>>, body: ByteArray?, head: Boolean, keepAlive: Boolean = true) {
            writeHead(status, headers, body?.size?.toLong() ?: 0L, keepAlive)
            if (!head && body != null) {
                out.write(body)
                sent += body.size
            }
        }

        /** An answer written as it's made: [length] bytes, or chunked when it isn't known (null). */
        fun stream(status: Int, headers: List<Pair<String, String>>, length: Long?, head: Boolean, body: (OutputStream) -> Unit) {
            writeHead(status, headers, length, keepAlive = true)
            if (head) return
            val counting = object : OutputStream() {
                override fun write(b: Int) {
                    out.write(b)
                    sent++
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len)
                    sent += len
                }
            }
            if (length != null) body(counting)
            else ChunkedOutputStream(counting).let { chunked ->
                body(chunked)
                chunked.close()
            }
        }

        private fun writeHead(status: Int, headers: List<Pair<String, String>>, length: Long?, keepAlive: Boolean) {
            this.status = status
            val head = StringBuilder("HTTP/1.1 ").append(status).append(' ').append(Http.reason(status)).append("\r\n")
            val all = headers + COMMON + listOf(
                "Date" to Http.date(System.currentTimeMillis()),
                "Connection" to if (keepAlive && request.keepAlive) "keep-alive" else "close",
            ) + if (length != null) listOf("Content-Length" to length.toString()) else listOf("Transfer-Encoding" to "chunked")
            all.forEach { (k, v) -> head.append(k).append(": ").append(v.replace(Regex("[\\r\\n]"), " ")).append("\r\n") }
            head.append("\r\n")
            // Headers are ISO-8859-1: a name outside it is in Content-Disposition's filename* already
            out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        }
    }

    private companion object {
        const val TAG = "FolderShareServer"
        const val MAX_CONNECTIONS = 32
        const val IDLE_TIMEOUT_MS = 30_000
        const val BUFFER = 64 * 1024
        const val LINGER_MS = 2_000
        const val LINGER_BYTES = 1024 * 1024

        /** Every answer's: nothing sniffed, no referrer passed on, not for search engines. */
        val COMMON = listOf(
            "Server" to "Toolbox",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
            "X-Robots-Tag" to "noindex, nofollow",
        )

        /** A folder's page: its own style and script, its icons inline, uploads to itself. */
        const val PAGE_POLICY = "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src data:; connect-src 'self'; form-action 'self'"
    }
}
