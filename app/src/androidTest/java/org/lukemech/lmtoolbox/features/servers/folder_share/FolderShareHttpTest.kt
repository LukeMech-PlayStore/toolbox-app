package org.lukemech.lmtoolbox.features.servers.folder_share

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The folder share's HTTP as its server reads and writes it (Http, Listing, ChunkedOutputStream), nothing on the
 * screen: a path never leaving the folder shared, byte ranges as players ask for them, types, Basic
 * authentication, the folder's page, and what's sent.
 */
class FolderShareHttpTest {
    private fun request(text: String) = Http.readRequest(ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1)))

    @Test
    fun readsARequest() {
        val r = request("GET /Movies/a%20b.mp4?download HTTP/1.1\r\nHost: phone:8080\r\nRange: bytes=0-\r\nAccept: a\r\nAccept: b\r\n\r\n")!!
        assertEquals("GET", r.method)
        assertEquals("/Movies/a%20b.mp4", r.path)
        assertEquals("download", r.query)
        assertEquals("bytes=0-", r.header("RANGE"))
        assertEquals("a, b", r.header("accept"))
        assertTrue(r.keepAlive)
        assertFalse(request("GET / HTTP/1.1\r\nConnection: close\r\n\r\n")!!.keepAlive)
        assertFalse(request("GET / HTTP/1.0\r\n\r\n")!!.keepAlive)
        assertTrue(request("GET / HTTP/1.0\r\nConnection: keep-alive\r\n\r\n")!!.keepAlive)
        // Bare LFs too; the stream's end before a request: none
        assertEquals("/x", request("GET /x HTTP/1.1\n\n")!!.path)
        assertNull(request(""))
    }

    @Test
    fun refusesWhatIsntARequest() {
        assertThrows(BadRequest::class.java) { request("GET /\r\n\r\n") }
        assertThrows(BadRequest::class.java) { request("GET / HTTP/1.1\r\nNoColon\r\n\r\n") }
        assertThrows(BadRequest::class.java) { request("GET / HTTP/1.1\r\nHost: x\r\n") }
        assertThrows(BadRequest::class.java) { request("GET /" + "a".repeat(Http.MAX_LINE) + " HTTP/1.1\r\n\r\n") }
        assertThrows(BadRequest::class.java) { request("GET / HTTP/1.1\r\n" + "X: y\r\n".repeat(Http.MAX_HEADERS + 1) + "\r\n") }
    }

    @Test
    fun pathsStayInTheFolder() {
        assertEquals(emptyList<String>(), Http.segments("/"))
        assertEquals(listOf("Movies", "a b.mp4"), Http.segments("/Movies/a%20b.mp4"))
        assertEquals(listOf("Movies"), Http.segments("/Movies/"))
        assertEquals(listOf("Zdjęcia", "żółw.jpg"), Http.segments("/Zdj%C4%99cia/%C5%BC%C3%B3%C5%82w.jpg"))
        assertEquals(listOf("a+b"), Http.segments("/a+b"))
        // Up, here, empty, escaped slashes and backslashes, NULs, broken escapes, not UTF-8, not absolute
        listOf(
            "/..", "/../etc/passwd", "/a/../../b", "/%2e%2e/x", "/%2E%2E", "/a/%2e%2e%2f%2e%2e", "/.", "/a/./b", "//etc",
            "/a//b", "/a%2fb", "/a%5c..%5cb", "/a%00b", "/%", "/%4", "/%zz", "/%C3", "/%FF", "a/b", "", "..",
        ).forEach { assertNull(it, Http.segments(it)) }
        assertTrue(Http.isFolderPath("/a/"))
        assertFalse(Http.isFolderPath("/a"))
    }

    @Test
    fun linksBackToPaths() {
        assertEquals("a%20b%2B%23%3F%25", Http.encodeSegment("a b+#?%"))
        assertEquals("%C5%BC%C3%B3%C5%82w.jpg", Http.encodeSegment("żółw.jpg"))
        assertEquals("/", Http.path(emptyList(), folder = true))
        assertEquals("/Movies/a%20b/", Http.path(listOf("Movies", "a b"), folder = true))
        assertEquals("/a.txt", Http.path(listOf("a.txt"), folder = false))
        // What's escaped comes back as it was
        listOf("a b", "100%", "żółw #1?.jpg", "😀.png").forEach { name ->
            assertEquals(listOf(name), Http.segments("/" + Http.encodeSegment(name)))
        }
    }

    @Test
    fun ranges() {
        assertEquals(Http.Range.Whole, Http.range(null, 1000))
        assertEquals(Http.Range.Part(0, 499), Http.range("bytes=0-499", 1000))
        assertEquals(Http.Range.Part(500, 999), Http.range("bytes=500-", 1000))
        assertEquals(Http.Range.Part(900, 999), Http.range("bytes=-100", 1000))
        assertEquals(Http.Range.Part(0, 999), Http.range("bytes=-5000", 1000))
        assertEquals(Http.Range.Part(990, 999), Http.range("bytes=990-5000", 1000))
        assertEquals(Http.Range.Part(0, 0), Http.range("Bytes=0-0", 1000))
        assertEquals(500L, (Http.range("bytes=0-499", 1000) as Http.Range.Part).length)
        // Past its end, the last nothing of it
        assertEquals(Http.Range.Unsatisfiable, Http.range("bytes=1000-", 1000))
        assertEquals(Http.Range.Unsatisfiable, Http.range("bytes=-0", 1000))
        assertEquals(Http.Range.Unsatisfiable, Http.range("bytes=0-", 0))
        // Not understood, several, another unit: the whole file
        listOf("bytes=", "bytes=a-b", "bytes=5-1", "bytes=0-1,5-6", "items=0-1", "bytes=99999999999999999999-", "bytes=1").forEach {
            assertEquals(it, Http.Range.Whole, Http.range(it, 1000))
        }
        assertEquals("bytes 0-499/1000", Http.contentRange(Http.Range.Part(0, 499), 1000))
    }

    @Test
    fun types() {
        assertEquals("video/mp4", Http.mime("Holiday.MP4"))
        assertEquals("video/x-matroska", Http.mime("a.mkv"))
        assertEquals("audio/mpeg", Http.mime("song.mp3"))
        assertEquals("image/jpeg", Http.mime("photo.jpeg"))
        assertEquals("application/pdf", Http.mime("doc.pdf"))
        assertEquals("text/plain; charset=utf-8", Http.mime("notes.txt"))
        assertEquals("text/html; charset=utf-8", Http.mime("index.html"))
        assertEquals("application/vnd.android.package-archive", Http.mime("app.apk"))
        // Unknown: the provider's type, else a download
        assertEquals("application/octet-stream", Http.mime("data.bin"))
        assertEquals("application/octet-stream", Http.mime("README"))
        assertEquals("application/x-thing", Http.mime("a.thing", "application/x-thing"))
        assertEquals("application/octet-stream", Http.mime("a.thing", "application/octet-stream"))
        assertTrue(Http.isActiveContent("text/html; charset=utf-8"))
        assertTrue(Http.isActiveContent("image/svg+xml"))
        assertFalse(Http.isActiveContent("video/mp4"))
        assertTrue(Http.isCompressible("text/plain; charset=utf-8"))
        assertFalse(Http.isCompressible("video/mp4"))
        assertFalse(Http.isCompressible("application/zip"))
    }

    private fun basic(credentials: String) = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())

    @Test
    fun basicAuthentication() {
        assertTrue(Http.authorized(basic("ola:sekret"), "ola", "sekret"))
        assertTrue(Http.authorized("basic " + basic("ola:sek:ret").substring(6), "ola", "sek:ret"))
        assertTrue(Http.authorized(basic("żółw:hasło"), "żółw", "hasło"))
        // No name set: any
        assertTrue(Http.authorized(basic("anyone:sekret"), "", "sekret"))
        assertFalse(Http.authorized(basic("ola:wrong"), "ola", "sekret"))
        assertFalse(Http.authorized(basic("ela:sekret"), "ola", "sekret"))
        assertFalse(Http.authorized(basic("ola"), "ola", "sekret"))
        assertFalse(Http.authorized(null, "ola", "sekret"))
        assertFalse(Http.authorized("Bearer abc", "ola", "sekret"))
        assertFalse(Http.authorized("Basic !!!", "ola", "sekret"))
        assertEquals("Basic realm=\"My \\\"files\\\"\", charset=\"UTF-8\"", Http.challenge("My \"files\""))
    }

    @Test
    fun namesForDownloadsAndUploads() {
        assertEquals("attachment; filename=\"a b.txt\"; filename*=UTF-8''a%20b.txt", Http.contentDisposition("a b.txt", attachment = true))
        assertEquals("inline; filename=\"__w.jpg\"; filename*=UTF-8''%C5%BC%C3%B3w.jpg", Http.contentDisposition("żów.jpg", attachment = false))
        assertEquals("attachment; filename=\"a\\\"b\"; filename*=UTF-8''a%22b", Http.contentDisposition("a\"b", attachment = true))
        val taken = setOf("photo.jpg", "photo (1).jpg", "notes", ".bashrc")
        assertEquals("new.jpg", Http.freeName("new.jpg") { it in taken })
        assertEquals("photo (2).jpg", Http.freeName("photo.jpg") { it in taken })
        assertEquals("notes (1)", Http.freeName("notes") { it in taken })
        assertEquals(".bashrc (1)", Http.freeName(".bashrc") { it in taken })
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", Http.date(784111777000))
        assertEquals("Partial Content", Http.reason(206))
    }

    @Test
    fun chunked() {
        val out = ByteArrayOutputStream()
        ChunkedOutputStream(out).apply {
            write("Hello".toByteArray())
            write(ByteArray(0))
            write(", world!".toByteArray(), 0, 8)
            write('!'.code)
            close()
            close()
        }
        assertEquals("5\r\nHello\r\n8\r\n, world!\r\n1\r\n!\r\n0\r\n\r\n", out.toString("US-ASCII"))
    }

    private val labels = Listing.Labels(
        lang = "en", home = "My <files>", empty = "Nothing here", zip = "ZIP", download = "Download", upload = "Upload",
        uploading = "Uploading {name}", uploaded = "Done", uploadFailed = "Failed", footer = "Shared"
    )

    @Test
    fun aFoldersPage() {
        val entries = listOf(
            Listing.Entry("b.mp4", folder = false, size = 1_500_000, modified = 0),
            Listing.Entry("Zdjęcia", folder = true, modified = 1_761_487_500_000),
            Listing.Entry("a <script>.txt", folder = false, size = 12, modified = 1_761_487_500_000),
        )
        val page = Listing.page(listOf("Movies", "x & y"), entries, labels, uploads = false, TimeZone.getTimeZone("UTC"), Locale.US)
        // Where it is: back to the folder shared and to Movies, itself not a link
        assertTrue(page.contains("<a href=\"/\">My &lt;files&gt;</a>"))
        assertTrue(page.contains("<a href=\"/Movies/\">Movies</a>"))
        assertTrue(page.contains("<b>x &amp; y</b>"))
        assertTrue(page.contains("<title>x &amp; y</title>"))
        // Folders first, then by name; names escaped, links relative and encoded
        val folder = page.indexOf("Zdjęcia")
        val a = page.indexOf("a &lt;script&gt;.txt")
        val b = page.indexOf("b.mp4")
        assertTrue(folder in 0 until a && a < b)
        assertFalse(page.contains("<script>.txt"))
        assertTrue(page.contains("href=\"Zdj%C4%99cia/\""))
        assertTrue(page.contains("href=\"a%20%3Cscript%3E.txt?download\" download"))
        // Sizes and dates
        assertTrue(page.contains("1.5 MB"))
        assertTrue(page.contains("12 B · 2025-10-26 14:05"))
        assertTrue(page.contains("href=\"?zip\""))
        // No uploads: no form, no script
        assertFalse(page.contains("<form"))
        assertFalse(page.contains("<script>"))
    }

    @Test
    fun anEmptyFolderWithUploads() {
        val page = Listing.page(emptyList(), emptyList(), labels, uploads = true, TimeZone.getTimeZone("UTC"), Locale.US)
        assertTrue(page.contains("<b>My &lt;files&gt;</b>"))
        assertTrue(page.contains("Nothing here"))
        // Nothing to ZIP
        assertFalse(page.contains("?zip"))
        assertTrue(page.contains("data-uploading=\"Uploading {name}\""))
        assertTrue(page.contains("<input id=\"files\" type=\"file\" multiple hidden>"))
        assertTrue(page.contains("x.open('PUT',encodeURIComponent(file.name))"))
    }

    @Test
    fun sizesAndDates() {
        assertEquals("0 B", Listing.size(0, Locale.US))
        assertEquals("999 B", Listing.size(999, Locale.US))
        assertEquals("1.0 kB", Listing.size(1000, Locale.US))
        assertEquals("1.5 MB", Listing.size(1_500_000, Locale.US))
        assertEquals("123 MB", Listing.size(123_400_000, Locale.US))
        assertEquals("1.0 GB", Listing.size(999_999_999, Locale.US))
        assertEquals("4,7 GB", Listing.size(4_700_000_000, Locale.forLanguageTag("pl")))
        assertEquals("1970-01-01 00:00", Listing.date(0, TimeZone.getTimeZone("UTC")))
        assertEquals("&lt;a href=&quot;x&quot;&gt;&amp;&#39;", Listing.escape("<a href=\"x\">&'"))
    }
}
