package org.lukemech.lmtoolbox.features.media.media_dl

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadsTest {
    private val info = MediaInfo("https://example.com/v", "Title", null, null, null, null, emptyList())

    private fun List<String>.after(flag: String) = getOrNull(indexOf(flag) + 1)

    @Test
    fun `a video's details and sizes`() {
        val parsed = MediaInfo.parse(
            "https://youtu.be/x",
            JSONObject(
                """{"title":"T","uploader":"","channel":"C","duration":61.7,"webpage_url":"https://www.youtube.com/watch?v=x",
                "formats":[
                {"vcodec":"none","acodec":"opus","tbr":130,"filesize":1000000},
                {"vcodec":"avc1","acodec":"none","height":1080,"tbr":4000,"filesize":50000000},
                {"vcodec":"avc1","acodec":"none","height":720,"tbr":2000,"filesize_approx":20000000},
                {"vcodec":"","acodec":""}]}"""
            )
        )!!
        assertEquals("https://www.youtube.com/watch?v=x", parsed.url)
        assertEquals("C", parsed.uploader)
        assertEquals(61, parsed.durationSec)
        assertEquals(listOf(1080, 720), parsed.heights)
        // The video and the best sound alone
        assertEquals(51_000_000L, parsed.videoSize(null))
        assertEquals(21_000_000L, parsed.videoSize(720))
        assertNull(parsed.videoSize(480))
        // A site's single file, codecs untold: video and sound
        assertTrue(parsed.formats.last().hasVideo && parsed.formats.last().hasAudio)
    }

    @Test
    fun `no playlists, and a title of its id at least`() {
        assertNull(MediaInfo.parse("u", JSONObject("""{"_type":"playlist"}""")))
        assertEquals("abc", MediaInfo.parse("u", JSONObject("""{"id":"abc","title":""}"""))!!.title)
    }

    @Test
    fun `the link in shared text`() {
        assertEquals("https://youtu.be/abc?t=1", MediaDownloads.linkIn("Look at this https://youtu.be/abc?t=1 wow"))
        assertEquals("plain", MediaDownloads.linkIn("  plain "))
    }

    @Test
    fun `the file name as yt-dlp's template`() {
        assertEquals("My_ video_1.%(ext)s", MediaDownloads.outputTemplate("My: video/1"))
        assertEquals("50%% off.%(ext)s", MediaDownloads.outputTemplate("50% off"))
        // Nothing left of it: the title
        assertEquals("%(title).150B.%(ext)s", MediaDownloads.outputTemplate(".."))
    }

    @Test
    fun `progress over a video and its sound`() {
        var d = MediaDownloads.Download(info, MediaOptions(), "f")
        // Post-processors' lines before it starts are something else's
        assertEquals(d, MediaDownloads.onLine(d, "[Merger] Merging formats"))
        d = MediaDownloads.onLine(d, "[info] abc: Downloading 1 format(s): 137+140")
        assertEquals(2, d.streams)
        d = MediaDownloads.onLine(d, "[download] Destination: f.f137.mp4")
        assertEquals(1, d.stream)
        assertEquals(MediaDownloads.Phase.Downloading, d.phase)
        d = MediaDownloads.onLine(d, "[dl] 500 1000 NA 2048.5 3")
        assertEquals(0.5f, d.fraction)
        assertEquals(25f, d.overall)
        // Speed and time left not told now and then: the last ones stand
        d = MediaDownloads.onLine(d, "[dl] 600 NA NA NA NA")
        assertNull(d.fraction)
        assertEquals(2048.5, d.speed!!, 0.0)
        assertEquals(3, d.eta)
        d = MediaDownloads.onLine(d, "[download] Destination: f.f140.m4a")
        assertEquals(2, d.stream)
        d = MediaDownloads.onLine(d, "[Merger] Merging formats into \"f.mp4\"")
        assertEquals(MediaDownloads.Phase.Processing, d.phase)
        assertEquals("Merger", d.step)
    }

    @Test
    fun `a video up to a height, as plays everywhere`() {
        val cmd = MediaDownloads.command(info, MediaOptions(maxHeight = 720), "f", File("dl"), null)
        assertEquals("res:720,vcodec:h264,acodec:aac", cmd.after("-S"))
        assertEquals("mp4", cmd.after("--merge-output-format"))
        assertTrue("--embed-thumbnail" in cmd)
        assertTrue("--sponsorblock-remove" in cmd)
        assertEquals(listOf("--", info.url), cmd.takeLast(2))
    }

    @Test
    fun `sound as WAV, from the lookup`() {
        val json = File("info.json")
        val cmd = MediaDownloads.command(info, MediaOptions(kind = MediaOptions.Kind.Audio, audioFormat = "wav"), "f", File("dl"), json)
        assertEquals("wav", cmd.after("--audio-format"))
        // Nowhere to keep a cover
        assertFalse("--embed-thumbnail" in cmd)
        assertEquals(json.absolutePath, cmd.after("--load-info-json"))
        assertFalse("--" in cmd)
    }
}
