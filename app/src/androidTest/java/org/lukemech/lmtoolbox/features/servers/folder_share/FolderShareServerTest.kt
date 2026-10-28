package org.lukemech.lmtoolbox.features.servers.folder_share

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.file.Files
import java.util.Base64
import java.util.Collections
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The folder share's server (FolderShareServer) over a socket, as a browser or a player talks to it, serving a
 * folder of files made here (a plain folder in place of the picker's, see [DirFiles]): pages, files and their
 * ranges, ZIPs, uploads, the password, and nothing outside the folder whatever the path.
 */
class FolderShareServerTest {
    private lateinit var root: File
    private lateinit var outside: File
    private lateinit var server: FolderShareServer
    private var settings = ShareSettings(folder = null)
    private val served: MutableList<FolderShareServer.Access> = Collections.synchronizedList(mutableListOf())
    private val video = ByteArray(300_000) { (it * 31 % 251).toByte() }

    @Before
    fun start() {
        root = Files.createTempDirectory("share").toFile()
        outside = Files.createTempDirectory("outside").toFile().apply { File(this, "secret.txt").writeText("not yours") }
        File(root, "Movies").mkdirs()
        File(root, "Movies/clip.mp4").writeBytes(video)
        File(root, "Movies/Żółw & co.txt").writeText("hello")
        File(root, "Empty").mkdirs()
        File(root, "page.html").writeText("<script>alert(1)</script>")
        val labels = Listing.Labels("en", "Shared", "Nothing here", "ZIP", "Download", "Upload", "Uploading {name}", "Done", "Failed", "Footer")
        server = FolderShareServer(DirFiles(root), 0, labels, settings = { settings }, onStarted = {}, onServed = { served += it })
        server.start()
    }

    @After
    fun stop() {
        server.stop()
        root.deleteRecursively()
        outside.deleteRecursively()
    }

    private class Response(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
        val text get() = String(body, Charsets.UTF_8)
        fun header(name: String) = headers[name.lowercase()]
    }

    /** [method] [path] as sent (not normalized, as a browser would), [headers] and [body] with it; the connection closed after. */
    private fun call(method: String, path: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): Response =
        Socket("127.0.0.1", server.localPort).use { socket ->
            val head = StringBuilder("$method $path HTTP/1.1\r\nHost: phone\r\nConnection: close\r\n")
            headers.forEach { (k, v) -> head.append("$k: $v\r\n") }
            body?.let { head.append("Content-Length: ${it.size}\r\n") }
            head.append("\r\n")
            socket.getOutputStream().apply {
                write(head.toString().toByteArray(Charsets.UTF_8))
                body?.let { write(it) }
                flush()
            }
            read(socket.getInputStream().readBytes())
        }

    private fun read(raw: ByteArray): Response {
        val end = (0 until raw.size - 3).first { raw[it] == '\r'.code.toByte() && raw[it + 1] == '\n'.code.toByte() && raw[it + 2] == '\r'.code.toByte() && raw[it + 3] == '\n'.code.toByte() }
        val lines = String(raw, 0, end, Charsets.ISO_8859_1).split("\r\n")
        val headers = lines.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        var body = raw.copyOfRange(end + 4, raw.size)
        if (headers["transfer-encoding"] == "chunked") body = unchunk(body)
        return Response(lines[0].split(' ')[1].toInt(), headers, body)
    }

    private fun unchunk(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (true) {
            val lineEnd = (i until data.size).first { data[it] == '\r'.code.toByte() }
            val size = String(data, i, lineEnd - i, Charsets.US_ASCII).toInt(16)
            if (size == 0) return out.toByteArray()
            out.write(data, lineEnd + 2, size)
            i = lineEnd + 2 + size + 2
        }
    }

    @Test
    fun aFoldersPage() {
        val r = call("GET", "/")
        assertEquals(200, r.status)
        assertEquals("text/html; charset=utf-8", r.header("Content-Type"))
        assertTrue(r.header("Content-Security-Policy")!!.contains("default-src 'none'"))
        assertTrue(r.text.contains("href=\"Movies/\""))
        assertTrue(r.text.contains("href=\"page.html\""))
        assertTrue(call("GET", "/Movies/").text.contains("href=\"%C5%BB%C3%B3%C5%82w%20%26%20co.txt\""))
        // A folder without its slash: to it with one (its links are relative to it)
        val redirect = call("GET", "/Movies?zip")
        assertEquals(301, redirect.status)
        assertEquals("/Movies/?zip", redirect.header("Location"))
        assertEquals(404, call("GET", "/Nope/").status)
        assertEquals("GET", served.last().method)
    }

    @Test
    fun filesWholeAndInRanges() {
        val whole = call("GET", "/Movies/clip.mp4")
        assertEquals(200, whole.status)
        assertEquals("video/mp4", whole.header("Content-Type"))
        assertEquals("bytes", whole.header("Accept-Ranges"))
        assertEquals(video.size.toString(), whole.header("Content-Length"))
        assertArrayEquals(video, whole.body)
        assertTrue(whole.header("Content-Disposition")!!.startsWith("inline"))

        val part = call("GET", "/Movies/clip.mp4", mapOf("Range" to "bytes=1000-1999"))
        assertEquals(206, part.status)
        assertEquals("bytes 1000-1999/${video.size}", part.header("Content-Range"))
        assertArrayEquals(video.copyOfRange(1000, 2000), part.body)
        val tail = call("GET", "/Movies/clip.mp4", mapOf("Range" to "bytes=-10"))
        assertArrayEquals(video.copyOfRange(video.size - 10, video.size), tail.body)
        assertEquals(416, call("GET", "/Movies/clip.mp4", mapOf("Range" to "bytes=${video.size}-")).status)
        // Changed since (another ETag): the whole of it
        assertEquals(200, call("GET", "/Movies/clip.mp4", mapOf("Range" to "bytes=0-1", "If-Range" to "\"other\"")).status)
        assertEquals(206, call("GET", "/Movies/clip.mp4", mapOf("Range" to "bytes=0-1", "If-Range" to whole.header("ETag")!!)).status)
        assertEquals(304, call("GET", "/Movies/clip.mp4", mapOf("If-None-Match" to whole.header("ETag")!!)).status)

        val head = call("HEAD", "/Movies/clip.mp4")
        assertEquals(video.size.toString(), head.header("Content-Length"))
        assertEquals(0, head.body.size)

        val named = call("GET", "/Movies/%C5%BB%C3%B3%C5%82w%20%26%20co.txt?download")
        assertEquals("hello", named.text)
        assertEquals("text/plain; charset=utf-8", named.header("Content-Type"))
        assertTrue(named.header("Content-Disposition")!!.startsWith("attachment"))
        // A page of the folder's: sandboxed, not the share's own
        assertEquals("sandbox", call("GET", "/page.html").header("Content-Security-Policy"))
        assertEquals(301, call("GET", "/page.html/").status)
    }

    @Test
    fun nothingOutsideTheFolder() {
        val up = "../" + outside.name + "/secret.txt"
        listOf(
            "/$up", "/Movies/../../${outside.name}/secret.txt", "/%2e%2e/${outside.name}/secret.txt", "/..%2f${outside.name}%2fsecret.txt",
            "/Movies/%2e%2e%2f%2e%2e%2f${outside.name}%2fsecret.txt", "/..\\${outside.name}\\secret.txt", "/${outside.absolutePath}", "//etc/passwd",
            "/%00", "/Movies/%",
        ).forEach { path ->
            val r = call("GET", path)
            assertTrue("$path: ${r.status}", r.status == 400 || r.status == 404)
            assertFalse(path, r.text.contains("not yours"))
        }
    }

    @Test
    fun aZipOfAFolder() {
        val r = call("GET", "/?zip")
        assertEquals(200, r.status)
        assertEquals("application/zip", r.header("Content-Type"))
        assertEquals("chunked", r.header("Transfer-Encoding"))
        assertTrue(r.header("Content-Disposition")!!.contains("Shared.zip"))
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(r.body)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entries[e.name] = zip.readBytes()
            }
        }
        assertArrayEquals(video, entries["Movies/clip.mp4"])
        assertEquals("hello", String(entries["Movies/Żółw & co.txt"]!!))
        assertTrue("Empty/" in entries)
        assertTrue(call("GET", "/Movies/?zip").header("Content-Disposition")!!.contains("Movies.zip"))
    }

    @Test
    fun uploadsOnlyWhenOn() {
        assertEquals(403, call("PUT", "/Movies/new.txt", body = "x".toByteArray()).status)
        assertFalse(File(root, "Movies/new.txt").exists())
        settings = settings.copy(uploads = true)
        val r = call("PUT", "/Movies/new.txt", body = "first".toByteArray())
        assertEquals(201, r.status)
        assertEquals("first", File(root, "Movies/new.txt").readText())
        // Never over another: next to it
        assertEquals(201, call("PUT", "/Movies/new.txt", body = "second".toByteArray()).status)
        assertEquals("first", File(root, "Movies/new.txt").readText())
        assertEquals("second", File(root, "Movies/new (1).txt").readText())
        assertEquals(404, call("PUT", "/Nope/a.txt", body = "x".toByteArray()).status)
        assertEquals(400, call("PUT", "/../a.txt", body = "x".toByteArray()).status)
        assertEquals(411, call("PUT", "/a.txt").status)
        assertFalse(File(root.parentFile, "a.txt").exists())
        assertTrue(call("GET", "/").text.contains("<form id=\"upload\""))
    }

    @Test
    fun thePassword() {
        settings = settings.copy(auth = true, user = "ola", password = "sekret")
        val asked = call("GET", "/")
        assertEquals(401, asked.status)
        assertTrue(asked.header("WWW-Authenticate")!!.startsWith("Basic realm=\"Shared\""))
        fun auth(credentials: String) = mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray()))
        assertEquals(401, call("GET", "/Movies/clip.mp4", auth("ola:wrong")).status)
        assertEquals(200, call("GET", "/Movies/clip.mp4", auth("ola:sekret")).status)
        // On with no password set: open still (the page says so)
        settings = settings.copy(password = "")
        assertEquals(200, call("GET", "/").status)
    }

    @Test
    fun keepsTheConnectionForTheNext() {
        Socket("127.0.0.1", server.localPort).use { socket ->
            val out = socket.getOutputStream()
            out.write("GET /Movies/%C5%BB%C3%B3%C5%82w%20%26%20co.txt HTTP/1.1\r\nHost: phone\r\n\r\nGET /Movies/clip.mp4 HTTP/1.1\r\nHost: phone\r\nRange: bytes=0-4\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            val all = String(socket.getInputStream().readBytes(), Charsets.ISO_8859_1)
            assertTrue(all.startsWith("HTTP/1.1 200 OK"))
            assertTrue(all.contains("hello"))
            assertTrue(all.contains("HTTP/1.1 206 Partial Content"))
        }
        assertEquals(400, read(Socket("127.0.0.1", server.localPort).use { s ->
            s.getOutputStream().write("NONSENSE\r\n\r\n".toByteArray())
            s.getInputStream().readBytes()
        }).status)
    }

    /** A plain folder as the share's ([ShareFiles]): its paths as the ids. */
    private class DirFiles(private val root: File) : ShareFiles {
        private fun node(f: File) = ShareFiles.Node(
            f.relativeTo(root).path, f.name, if (f.isDirectory) ShareFiles.FOLDER else Http.mime(f.name).substringBefore(';'),
            if (f.isDirectory) 0 else f.length(), f.lastModified()
        )

        private fun file(n: ShareFiles.Node) = File(root, n.id)

        override fun root() = node(root)
        override fun children(folder: ShareFiles.Node) = file(folder).listFiles().orEmpty().sortedBy { it.name }.map { node(it) }
        override fun open(node: ShareFiles.Node) = ShareFiles.Opened(file(node).length(), FileInputStream(file(node)) as InputStream)
        override fun create(folder: ShareFiles.Node, name: String, mime: String): Pair<ShareFiles.Node, OutputStream> {
            val f = File(file(folder), name)
            return node(f.apply { createNewFile() }) to FileOutputStream(f)
        }
        override fun delete(node: ShareFiles.Node) {
            file(node).delete()
        }
    }
}
