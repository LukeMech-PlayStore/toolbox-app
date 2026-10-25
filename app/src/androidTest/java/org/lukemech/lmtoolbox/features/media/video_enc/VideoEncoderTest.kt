package org.lukemech.lmtoolbox.features.media.video_enc

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.lukemech.lmtoolbox.e2e.LinuxToolTest
import org.lukemech.lmtoolbox.linux.LinuxTasks

/** The video encoder, installed and gone through (see LinuxToolTest) with a video to encode. */
class VideoEncoderTest : LinuxToolTest(VideoEncoderFeature) {
    /** A 3-second video with sound, made by the subsystem's ffmpeg (the encoder's own, installed by now). */
    override fun files(): List<Pair<String, File>> {
        val out = File(context.cacheDir, "e2e/sample.mp4").apply { parentFile?.mkdirs(); delete() }
        val result = runBlocking {
            LinuxTasks.run(
                context,
                listOf(
                    "ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=duration=3:size=640x360:rate=25",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-shortest",
                    "-c:v", "mpeg4", "-c:a", "aac", out.canonicalPath
                ),
                VideoEncoderFeature
            )
        }
        assertTrue("ffmpeg couldn't make a sample video: ${result.stderr.takeLast(2000)}", result.isSuccess && out.length() > 0)
        return listOf("video/" to out)
    }
}
