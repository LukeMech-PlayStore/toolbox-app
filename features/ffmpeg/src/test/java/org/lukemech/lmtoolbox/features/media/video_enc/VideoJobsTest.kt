package org.lukemech.lmtoolbox.features.media.video_enc

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoJobsTest {
    private val phoneVideo = Probe(60_000, 0, Probe.Video("h264", 1920, 1080, 30.0), Probe.Audio("aac", 2))
    private val silentVideo = phoneVideo.copy(audio = null)
    private val song = Probe(180_000, 0, null, Probe.Audio("mp3", 2), cover = true)

    private fun List<String>.after(flag: String) = getOrNull(indexOf(flag) + 1)

    @Test
    fun `a phone's sideways video is read upright`() {
        val probe = Probe.parse(
            JSONObject(
                """{"streams":[
                {"codec_type":"video","codec_name":"h264","width":1920,"height":1080,"avg_frame_rate":"30000/1001","side_data_list":[{"rotation":-90}]},
                {"codec_type":"audio","codec_name":"aac","channels":2}],
                "format":{"duration":"12.5","size":"1000"}}"""
            )
        )!!
        assertEquals(1080, probe.video!!.width)
        assertEquals(1920, probe.video.height)
        assertEquals(29.97, probe.video.fps!!, 0.01)
        assertEquals(12_500, probe.durationMs)
        assertEquals(1000, probe.size)
        assertEquals(Probe.Audio("aac", 2), probe.audio)
        assertFalse(probe.cover)
    }

    @Test
    fun `a song's cover is no video`() {
        val probe = Probe.parse(
            JSONObject(
                """{"streams":[
                {"codec_type":"audio","codec_name":"mp3","channels":2},
                {"codec_type":"video","codec_name":"mjpeg","width":500,"height":500,"disposition":{"attached_pic":1}}],
                "format":{"duration":"180"}}"""
            )
        )!!
        assertNull(probe.video)
        assertTrue(probe.cover)
    }

    @Test
    fun `nothing to encode, or no frame rate told`() {
        assertNull(Probe.parse(JSONObject("""{"streams":[{"codec_type":"subtitle"}]}""")))
        val probe = Probe.parse(JSONObject("""{"streams":[{"codec_type":"video","codec_name":"h264","width":2,"height":2,"avg_frame_rate":"0/0"}]}"""))
        assertNull(probe!!.video!!.fps)
    }

    @Test
    fun `what each format can hold`() {
        assertEquals(listOf(VideoCodec.VP9, VideoCodec.AV1), VideoCodec.forFormat(Format.WEBM, phoneVideo, null))
        val vp9 = phoneVideo.copy(video = phoneVideo.video!!.copy(codec = "vp9"))
        assertEquals(VideoCodec.Copy, VideoCodec.forFormat(Format.WEBM, vp9, null).first())
        // Nothing to copy of sound alone
        assertFalse(VideoCodec.Copy in VideoCodec.forFormat(Format.MP4, song, null))
        // Only what FFmpeg has
        assertEquals(listOf(VideoCodec.Copy, VideoCodec.H264), VideoCodec.forFormat(Format.MP4, phoneVideo, setOf("libx264", "aac")))
        assertFalse(Format.GIF.isAvailable(song, null))
    }

    @Test
    fun `a copied video keeps nothing to change`() {
        val o = EncodeOptions(Format.MP4, VideoCodec.Copy, height = 720, fps = 30, speed = 2f, rotate = 90, targetMb = 10.0)
            .effective(phoneVideo, null)
        assertEquals(VideoCodec.Copy, o.codec)
        assertEquals(0, o.height)
        assertEquals(0, o.fps)
        assertEquals(1f, o.speed)
        assertEquals(0, o.rotate)
        assertEquals(0.0, o.targetMb, 0.0)
        assertFalse(o.canTargetSize)
    }

    @Test
    fun `a GIF's size and frame rate are of its own`() {
        val o = EncodeOptions(Format.GIF, height = 1080, fps = 60).effective(phoneVideo, null)
        assertEquals(EncodeOptions.GIF_HEIGHTS[1], o.height)
        assertEquals(EncodeOptions.GIF_FPS[1], o.fps)
        assertFalse(o.sound)
    }

    @Test
    fun `a size to aim at picks a format that can`() {
        val gif = EncodeOptions(Format.GIF).withTargetSize(phoneVideo, null)
        assertEquals(Format.MP4, gif.format)
        assertEquals(EncodeOptions.DEFAULT_TARGET_MB, gif.targetMb, 0.0)
        assertEquals(Format.MP3, EncodeOptions(Format.WAV).withTargetSize(song, null).format)
    }

    @Test
    fun `bitrates for a size`() {
        // 1 MB in a minute: 8000 kb / 60 s, less the container's 5 %
        assertEquals(VideoJobs.Bitrates(null, 126), VideoJobs.bitrates(song, Cut(0, 60_000), EncodeOptions(Format.MP3, targetMb = 1.0)))
        // 10 MB in 100 s: 760 kb/s, a tenth for the sound
        assertEquals(VideoJobs.Bitrates(684, 76), VideoJobs.bitrates(phoneVideo, Cut(0, 100_000), EncodeOptions(targetMb = 10.0)))
        assertNull(VideoJobs.bitrates(phoneVideo, Cut(0, 100_000), EncodeOptions()))
    }

    @Test
    fun `a height the bitrate can carry`() {
        // 0.07 bits a pixel and frame for H.264: 684 kb/s carry 360p at 30 fps
        assertEquals(360, VideoJobs.autoHeight(phoneVideo, EncodeOptions(), 684))
        // Enough for the original: kept (0)
        assertEquals(0, VideoJobs.autoHeight(phoneVideo, EncodeOptions(), 10_000))
        // One picked stands
        assertEquals(720, VideoJobs.autoHeight(phoneVideo, EncodeOptions(height = 720), 684))
    }

    @Test
    fun `the new picture's size`() {
        assertEquals(1280 to 720, VideoJobs.outputSize(phoneVideo, EncodeOptions(height = 720)))
        val odd = phoneVideo.copy(video = Probe.Video("h264", 1001, 601, 30.0))
        // Even for video codecs, as it is for a GIF
        assertEquals(1000 to 600, VideoJobs.outputSize(odd, EncodeOptions()))
        assertEquals(1001 to 601, VideoJobs.outputSize(odd, EncodeOptions(Format.GIF)))
        assertNull(VideoJobs.outputSize(phoneVideo, EncodeOptions(codec = VideoCodec.Copy)))
        assertNull(VideoJobs.outputSize(phoneVideo, EncodeOptions(Format.MP3)))
    }

    @Test
    fun `a cut H264 video, its sound copied`() {
        val output = File("out.mp4")
        val cmd = VideoJobs.command(phoneVideo, File("input.mp4"), Cut(5_000, 15_000), EncodeOptions(), output)
        // Seeking before the input
        assertTrue(cmd.indexOf("-ss") in 0..<cmd.indexOf("-i"))
        assertEquals("5.000", cmd.after("-ss"))
        assertEquals("10.000", cmd.after("-t"))
        assertEquals("libx264", cmd.after("-c:v"))
        assertEquals("23", cmd.after("-crf"))
        assertEquals("copy", cmd.after("-c:a"))
        assertEquals("+faststart", cmd.after("-movflags"))
        assertEquals(output.absolutePath, cmd.last())
    }

    @Test
    fun `a whole video copied`() {
        val cmd = VideoJobs.command(phoneVideo, File("input.mp4"), Cut(0, 60_000), EncodeOptions(codec = VideoCodec.Copy), File("out.mp4"))
        assertFalse("-ss" in cmd)
        assertFalse("-t" in cmd)
        assertEquals("copy", cmd.after("-c:v"))
        assertEquals("make_zero", cmd.after("-avoid_negative_ts"))
        assertFalse("-vf" in cmd)
    }

    @Test
    fun `sound of a silent video is silence`() {
        val cmd = VideoJobs.command(silentVideo, File("input.mp4"), Cut(0, 60_000), EncodeOptions(Format.MP3), File("out.mp3"))
        assertTrue("anullsrc=r=48000:cl=stereo" in cmd)
        assertEquals("1:a:0", cmd.after("-map"))
        assertEquals("libmp3lame", cmd.after("-c:a"))
        assertEquals("2", cmd.after("-q:a"))
    }
}
