package org.lukemech.lmtoolbox.features.media.video_enc

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.lukemech.lmtoolbox.features.ffmpeg.R
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.OutputFolder
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.helpers.SavedFile
import org.lukemech.lmtoolbox.helpers.displayName
import org.lukemech.lmtoolbox.helpers.safeFileName
import org.lukemech.lmtoolbox.helpers.saveToMediaStore
import org.lukemech.lmtoolbox.tasks.AppTasks

/** What ffprobe tells of a file: enough to show it, cut it and pick what to make of it. */
data class Probe(
    val durationMs: Long,
    val size: Long,
    val video: Video?,
    val audio: Audio?,
    /** A picture comes with it (e.g. a song's cover): a video made of its sound shows that. */
    val cover: Boolean = false,
) {
    /** [width] x [height] as it's shown (its rotation applied). */
    data class Video(val codec: String, val width: Int, val height: Int, val fps: Double?)
    data class Audio(val codec: String, val channels: Int)

    companion object {
        /** The JSON of ffprobe -show_format -show_streams; null if it has neither video nor sound. */
        fun parse(json: JSONObject): Probe? {
            val streams = json.optJSONArray("streams") ?: return null
            val all = (0 until streams.length()).map { streams.getJSONObject(it) }
            // A cover comes as a video stream too
            fun JSONObject.isCover() = optJSONObject("disposition")?.optInt("attached_pic") == 1
            val v = all.firstOrNull { it.optString("codec_type") == "video" && !it.isCover() }
            val cover = all.any { it.optString("codec_type") == "video" && it.isCover() }
            val a = all.firstOrNull { it.optString("codec_type") == "audio" }
            if (v == null && a == null) return null
            val format = json.optJSONObject("format")
            fun seconds(o: JSONObject?) = o?.optString("duration")?.toDoubleOrNull()
            val duration = seconds(format) ?: seconds(v) ?: seconds(a) ?: 0.0
            val video = v?.let {
                // Phones film sideways and say so: as it's shown, it's turned
                val rotation = it.optJSONArray("side_data_list")?.let { list ->
                    (0 until list.length()).map { i -> list.getJSONObject(i) }.firstOrNull { d -> d.has("rotation") }?.optInt("rotation")
                } ?: it.optJSONObject("tags")?.optString("rotate")?.toIntOrNull() ?: 0
                val turned = abs(rotation) % 180 == 90
                val w = it.optInt("width")
                val h = it.optInt("height")
                Video(it.optString("codec_name"), if (turned) h else w, if (turned) w else h, rate(it.optString("avg_frame_rate")))
            }
            return Probe(
                durationMs = (duration * 1000).toLong(),
                size = format?.optString("size")?.toLongOrNull() ?: 0,
                video = video?.takeIf { it.width > 0 && it.height > 0 },
                audio = a?.let { Audio(it.optString("codec_name"), it.optInt("channels")) },
                cover = cover
            )
        }

        /** "30000/1001" as 29.97; null for "0/0". */
        private fun rate(text: String): Double? {
            val (n, d) = text.split('/').map { it.toDoubleOrNull() ?: 0.0 }.let { it.getOrElse(0) { 0.0 } to it.getOrElse(1) { 1.0 } }
            return if (n > 0 && d > 0) n / d else null
        }
    }
}

/** The part of the file to keep, in ms. */
data class Cut(val startMs: Long, val endMs: Long) {
    val lengthMs get() = endMs - startMs
    fun isWhole(probe: Probe) = startMs <= 0 && endMs >= probe.durationMs
}

/** What the file becomes: its [kind] of output, with what FFmpeg encodes it with. */
enum class Format(val ext: String, val mimeType: String, val kind: Kind, private val encoder: String? = null) {
    MP4("mp4", "video/mp4", Kind.Video),
    MKV("mkv", "video/x-matroska", Kind.Video),
    WEBM("webm", "video/webm", Kind.Video),
    GIF("gif", "image/gif", Kind.Gif, "gif"),
    MP3("mp3", "audio/mpeg", Kind.Audio, "libmp3lame"),
    M4A("m4a", "audio/mp4", Kind.Audio, "aac"),
    OPUS("opus", "audio/ogg", Kind.Audio, "libopus"),
    FLAC("flac", "audio/flac", Kind.Audio, "flac"),
    WAV("wav", "audio/wav", Kind.Audio, "pcm_s16le");

    enum class Kind { Video, Gif, Audio }

    /**
     * Whether it can be made of [probe] (null: any file) with these [encoders] (null: unknown, all).
     * A video of sound alone shows its cover (or black), sound of a silent video is silence; a GIF
     * takes a moving picture.
     */
    fun isAvailable(probe: Probe?, encoders: Set<String>?): Boolean = when (kind) {
        Kind.Video -> VideoCodec.forFormat(this, probe, encoders).isNotEmpty()
        Kind.Gif -> (probe == null || probe.video != null) && (encoders == null || encoder in encoders)
        Kind.Audio -> encoders == null || encoder in encoders
    }

    /** The lossless ones have no quality to pick. */
    val hasQuality get() = this != FLAC && this != WAV

    internal val audioEncoder get() = encoder
}

/** The video's codec, or [Copy]: as it is, not encoded again. */
enum class VideoCodec(val encoder: String?, val label: String) {
    Copy(null, ""),
    H264("libx264", "H.264"),
    H265("libx265", "H.265"),
    VP9("libvpx-vp9", "VP9"),
    AV1("libsvtav1", "AV1"),
    MPEG4("mpeg4", "MPEG-4");

    companion object {
        private val IN = mapOf(
            Format.MP4 to listOf(H264, H265, AV1, MPEG4),
            Format.MKV to listOf(H264, H265, VP9, AV1, MPEG4),
            Format.WEBM to listOf(VP9, AV1),
        )

        /**
         * What [format] can hold of [probe]'s video: copied (WebM only takes its own codecs; nothing to
         * copy of sound alone), or encoded with what there is.
         */
        fun forFormat(format: Format, probe: Probe?, encoders: Set<String>?): List<VideoCodec> {
            val copy = (probe == null || probe.video != null) &&
                (format != Format.WEBM || probe?.video?.codec.let { it == null || it in setOf("vp8", "vp9", "av1") })
            return listOfNotNull(Copy.takeIf { copy }) + IN[format].orEmpty().filter { encoders == null || it.encoder in encoders }
        }
    }
}

enum class Quality { High, Medium, Small }

/** How the file is made: its format, and for video what it's encoded with and how (the cut is apart, see [Cut]). */
data class EncodeOptions(
    val format: Format = Format.MP4,
    val codec: VideoCodec = VideoCodec.H264,
    /** 0: as it is. */
    val height: Int = 0,
    val quality: Quality = Quality.Medium,
    /** 0: as it is. */
    val fps: Int = 0,
    val sound: Boolean = true,
    val speed: Float = 1f,
    /** Clockwise. */
    val rotate: Int = 0,
    /** The file's size to aim at, in MB (bitrates follow from it instead of [quality]); 0: none. */
    val targetMb: Double = 0.0,
) {
    /** Whether it makes sound alone (else a picture: a video or a GIF). */
    val isAudio get() = format.kind == Format.Kind.Audio

    /** Whether a size can be aimed at: an encoded video, or lossy sound (not a GIF, a copy, FLAC or WAV). */
    val canTargetSize get() = when (format.kind) {
        Format.Kind.Video -> codec != VideoCodec.Copy
        Format.Kind.Audio -> format.hasQuality
        Format.Kind.Gif -> false
    }

    /** With a size to aim at ([targetMb], or [DEFAULT_TARGET_MB] when none yet): a format and codec that can. */
    fun withTargetSize(probe: Probe?, encoders: Set<String>?): EncodeOptions {
        val format = when {
            format.kind == Format.Kind.Gif -> Format.MP4
            !format.hasQuality -> Format.MP3
            else -> format
        }
        val codec = codec.takeIf { it != VideoCodec.Copy }
            ?: VideoCodec.forFormat(format, probe, encoders).firstOrNull { it != VideoCodec.Copy } ?: codec
        return copy(format = format, codec = codec, targetMb = targetMb.takeIf { it > 0 } ?: DEFAULT_TARGET_MB)
    }

    /**
     * Switched to making sound alone ([audio]) or a picture: the first format of that kind
     * [probe] and the [encoders] allow, unless it already is one.
     */
    fun withMode(audio: Boolean, probe: Probe?, encoders: Set<String>?): EncodeOptions {
        if (isAudio == audio) return this
        val format = Format.entries.firstOrNull { (it.kind == Format.Kind.Audio) == audio && it.isAvailable(probe, encoders) }
            ?: return this
        return copy(format = format)
    }

    /** These, as far as [probe] and the [encoders] allow: e.g. no codec it can't use, nothing to change of a copied video. */
    fun effective(probe: Probe?, encoders: Set<String>?): EncodeOptions {
        val format = format.takeIf { it.isAvailable(probe, encoders) }
            ?: Format.entries.firstOrNull { it.isAvailable(probe, encoders) } ?: return this
        return when (format.kind) {
            Format.Kind.Video -> {
                val codecs = VideoCodec.forFormat(format, probe, encoders)
                // The usual one, else the first it has
                val codec = codec.takeIf { it in codecs } ?: codecs.firstOrNull { it != VideoCodec.Copy } ?: codecs.first()
                val copy = codec == VideoCodec.Copy
                copy(
                    format = format, codec = codec,
                    height = if (copy) 0 else height, fps = if (copy) 0 else fps,
                    speed = if (copy) 1f else speed, rotate = if (copy) 0 else rotate,
                    // Sound alone: that's what the video is of
                    sound = probe?.video == null || (sound && probe.audio != null),
                    // A copy is as big as it is
                    targetMb = if (copy) 0.0 else targetMb
                )
            }
            Format.Kind.Gif -> copy(
                format = format,
                height = height.takeIf { it in GIF_HEIGHTS } ?: GIF_HEIGHTS[1],
                fps = fps.takeIf { it in GIF_FPS } ?: GIF_FPS[1],
                sound = false,
                targetMb = 0.0
            )
            Format.Kind.Audio -> copy(format = format, height = 0, fps = 0, rotate = 0, targetMb = if (format.hasQuality) targetMb else 0.0)
        }
    }

    fun toJson(): String = JSONObject()
        .put("format", format.name).put("codec", codec.name).put("height", height).put("quality", quality.name)
        .put("fps", fps).put("sound", sound).put("speed", speed.toDouble()).put("rotate", rotate)
        .put("targetMb", targetMb)
        .toString()

    companion object {
        val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)
        val FPS = listOf(60, 30, 24)
        val GIF_HEIGHTS = listOf(480, 360, 240)
        val GIF_FPS = listOf(24, 15, 10)
        val SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)
        val ROTATIONS = listOf(0, 90, 180, 270)
        /** File sizes to aim at, in MB (e.g. what messengers and mail take). */
        val TARGET_SIZES = listOf(8.0, 10.0, 25.0, 50.0, 100.0)
        const val DEFAULT_TARGET_MB = 10.0

        /** The last encoding's choices, for the next one. */
        fun load(context: Context): EncodeOptions {
            val d = EncodeOptions()
            val json = runCatching { JSONObject(PreferenceHelper.get(context, VideoEncoderFeature.S_OPTIONS, "")) }.getOrNull() ?: return d
            return EncodeOptions(
                format = runCatching { Format.valueOf(json.getString("format")) }.getOrDefault(d.format),
                codec = runCatching { VideoCodec.valueOf(json.getString("codec")) }.getOrDefault(d.codec),
                height = json.optInt("height", d.height),
                quality = runCatching { Quality.valueOf(json.getString("quality")) }.getOrDefault(d.quality),
                fps = json.optInt("fps", d.fps),
                sound = json.optBoolean("sound", d.sound),
                speed = json.optDouble("speed", d.speed.toDouble()).toFloat().takeIf { it in SPEEDS } ?: d.speed,
                rotate = json.optInt("rotate", d.rotate).takeIf { it in ROTATIONS } ?: d.rotate,
                targetMb = json.optDouble("targetMb", d.targetMb).takeIf { !it.isNaN() && it >= 0 } ?: d.targetMb,
            )
        }

        fun save(context: Context, o: EncodeOptions) = PreferenceHelper.put(context, VideoEncoderFeature.S_OPTIONS, o.toJson())
    }
}

/**
 * Runs FFmpeg: [open] copies a file in and reads what's in it, [start] makes the new one, which goes
 * on (shown in the bottom bar) when the page is left and ends up in Movies, Music or Pictures. One at a
 * time, in [state].
 */
object VideoJobs {
    private const val PROBE_TIMEOUT_MS = 60_000L

    /** Of a size to aim at, what's for the streams (the container takes the rest). */
    private const val TARGET_MARGIN = 0.95
    /** Tries to get under it, the first one included. */
    private const val TARGET_ATTEMPTS = 3
    /** Of the bitrate that would have hit it, the retry's: to land under it, not on it. */
    private const val RETRY_MARGIN = 0.97
    private const val MIN_VIDEO_KBPS = 30
    private const val MIN_AUDIO_KBPS = 32
    private const val MAX_AUDIO_KBPS = 320
    /** A video's sound, at most, with a size to aim at. */
    private const val VIDEO_AUDIO_KBPS = 128

    /** A file being worked on: where it's from, its name, its copy FFmpeg reads and what's in it. */
    data class Opened(val uri: Uri, val name: String, val copy: File, val probe: Probe) {
        val baseName get() = name.substringBeforeLast('.').ifBlank { name }
    }

    data class Job(
        val opened: Opened,
        val cut: Cut,
        val options: EncodeOptions,
        val fileName: String,
        val taskId: Long? = null,
        val phase: Phase = Phase.Starting,
        /** 0..1, null while unknown. */
        val fraction: Float? = null,
        /** How much faster than playing it FFmpeg goes. */
        val speed: Double? = null,
        val saved: SavedFile? = null,
        val error: String? = null,
        /** Encoding again, at lower bitrates: the last try came out bigger than [EncodeOptions.targetMb]. */
        val retry: Boolean = false,
        /** Saved, but still bigger than [EncodeOptions.targetMb] (e.g. too long a file for so small a size). */
        val missedTarget: Boolean = false,
    ) {
        val isActive get() = phase == Phase.Starting || phase == Phase.Encoding || phase == Phase.Saving

        /** How long the new file is: the cut, at its speed. */
        val outputMs get() = (cut.lengthMs / options.speed).toLong()

        /** Seconds left at this pace; null while unknown. */
        val eta: Int? get() {
            val f = fraction ?: return null
            val s = speed?.takeIf { it > 0 } ?: return null
            return ((1 - f) * outputMs / 1000 / s).toInt()
        }
    }

    enum class Phase { Starting, Encoding, Saving, Done, Failed, Cancelled }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The open file, its cut, the new name and how it's to be made: back to them after the page was
     * left, or redrawn afresh (e.g. back from the file picker). No [options] yet: the last encoding's.
     */
    var opened: Opened? = null
    var cut: Cut? = null
    var fileName: String = ""
    var options: EncodeOptions? = null

    private val _state = MutableStateFlow<Job?>(null)
    val state: StateFlow<Job?> = _state.asStateFlow()

    /** What FFmpeg can encode with (e.g. libx264), once asked. */
    var encoders: Set<String>? = null
        private set

    private fun inputDir(context: Context) = File(VideoEncoderFeature.cacheDir(context), "in")
    private fun outputDir(context: Context) = File(VideoEncoderFeature.cacheDir(context), "out")

    /** Copies [uri] in (FFmpeg reads files) and reads what's in it; the failure's message, if it says something. */
    suspend fun open(context: Context, uri: Uri): Result<Opened> {
        val name = displayName(context, uri) ?: "video"
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 5 }
            ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        // A plain name keeps odd characters out of its command
        val copy = withContext(Dispatchers.IO) {
            runCatching {
                val dir = inputDir(context).apply { deleteRecursively(); mkdirs() }
                File(dir, if (ext == null) "input" else "input.$ext").also { file ->
                    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                }
            }.getOrNull()
        } ?: return Result.failure<Opened>(Exception()).also {
            // Not a command's: told in the one place errors are, the bottom bar
            AppTasks.reportFailure(VideoEncoderFeature, name, "Couldn't read the file")
        }
        val result = LinuxTasks.run(
            context,
            listOf("ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", copy.absolutePath),
            VideoEncoderFeature, timeoutMs = PROBE_TIMEOUT_MS
        )
        if (result.isEnvNotReady) return Result.failure(Exception())
        val json = result.stdout.indexOf('{').takeIf { it >= 0 }?.let { runCatching { JSONObject(result.stdout.substring(it)) }.getOrNull() }
        val probe = json?.let(Probe::parse)
            // What FFprobe said is in the task's log (the bottom bar's); read fine, but nothing to encode in it: told there too
            ?: return Result.failure<Opened>(if (json != null) UnsupportedOperationException() else Exception()).also {
                if (json != null) AppTasks.reportFailure(VideoEncoderFeature, name, "No video or sound in the file")
            }
        if (encoders == null) encoders = readEncoders(context)
        return Result.success(Opened(uri, name, copy, probe))
    }

    /** `ffmpeg -encoders`: e.g. " V....D lib264 H.264 ..." */
    private suspend fun readEncoders(context: Context): Set<String>? {
        val result = LinuxTasks.run(context, listOf("ffmpeg", "-hide_banner", "-encoders"), VideoEncoderFeature, timeoutMs = PROBE_TIMEOUT_MS)
        if (!result.isSuccess) return null
        return result.stdout.lineSequence()
            .mapNotNull { Regex("""^\s*[VAS][A-Z.]{5}\s+(\S+)""").find(it)?.groupValues?.get(1) }
            .filter { it != "=" }
            .toSet().takeIf { it.isNotEmpty() }
    }

    fun start(context: Context, opened: Opened, cut: Cut, options: EncodeOptions, fileName: String) {
        if (_state.value?.isActive == true) return
        val app = context.applicationContext
        val o = options.effective(opened.probe, encoders)
        _state.value = Job(opened, cut, o, fileName)
        scope.launch {
            val dir = outputDir(app).apply { deleteRecursively(); mkdirs() }
            val output = File(dir, "${safeFileName(fileName, "video")}.${o.format.ext}")
            // With a size to aim at: encoded again, at lower bitrates, while it comes out bigger (encoders overshoot)
            val target = (o.targetMb * 1_000_000).toLong().takeIf { it > 0 && o.canTargetSize }
            var scale = 1.0
            var attempt = 1
            while (true) {
                val rates = bitrates(opened.probe, cut, o, scale)
                val encoded = rates?.videoKbps?.let { o.copy(height = autoHeight(opened.probe, o, it)) } ?: o
                val result = LinuxTasks.runInBackground(
                    app, command(opened.probe, opened.copy, cut, encoded, output, rates), VideoEncoderFeature,
                    label = output.name,
                    timeoutMs = Long.MAX_VALUE,
                    onStart = { id -> _state.update { it?.copy(taskId = id) } },
                    onLine = { line ->
                        val updated = _state.updateAndGet { it?.let { j -> onLine(j, line) } }
                        val percent = updated?.fraction?.let { (it * 100).toInt() } ?: return@runInBackground
                        updated.taskId?.let { AppTasks.setProgress(it, percent.toFloat()) }
                    }
                )
                val cancelled = AppTasks.isCancelled(_state.value?.taskId)
                val size = output.takeIf { it.exists() }?.length() ?: 0L
                if (!cancelled && result.isSuccess && target != null && size > target && attempt < TARGET_ATTEMPTS) {
                    attempt++
                    scale *= target.toDouble() / size * RETRY_MARGIN
                    _state.update { it?.copy(phase = Phase.Starting, fraction = null, speed = null, retry = true) }
                    continue
                }
                if (target != null && size > target) _state.update { it?.copy(missedTarget = true) }
                finish(app, result.isSuccess, cancelled, output, o)
                break
            }
            dir.deleteRecursively()
        }
    }

    /** The job once FFmpeg is done with [output]: saved, else failed or canceled. */
    private suspend fun finish(app: Context, success: Boolean, cancelled: Boolean, output: File, o: EncodeOptions) {
        when {
            cancelled -> _state.update { it?.copy(phase = Phase.Cancelled) }
            !success || !output.exists() || output.length() == 0L ->
                // The card says it failed; what FFmpeg said is in the task's log
                _state.update { it?.copy(phase = Phase.Failed) }
            else -> {
                _state.update { it?.copy(phase = Phase.Saving, fraction = null) }
                val saved = runCatching { save(app, output, o.format) }.getOrNull()
                _state.update {
                    if (saved != null) it?.copy(phase = Phase.Done, saved = saved)
                    else it?.copy(phase = Phase.Failed, error = "Couldn't save the file")
                }
                if (saved == null) AppTasks.reportFailure(VideoEncoderFeature, "", "Couldn't save the file")
            }
        }
    }

    fun cancel() {
        _state.value?.taskId?.let { AppTasks.cancel(it) }
    }

    /** Forgets a finished job (e.g. to make another). */
    fun clear() {
        if (_state.value?.isActive != true) _state.value = null
    }

    /** Whether a job reads [opened]'s copy now: it can't be replaced meanwhile. */
    fun isBusy() = _state.value?.isActive == true

    /**
     * The FFmpeg command making [output] of [input]'s [cut] ([probe]: what's in it): progress as key=value lines on
     * stdout (see [onLine]), only errors on stderr.
     */
    internal fun command(probe: Probe, input: File, cut: Cut, o: EncodeOptions, output: File, rates: Bitrates? = null): List<String> = buildList {
        addAll(listOf("ffmpeg", "-hide_banner", "-nostdin", "-y", "-loglevel", "error", "-nostats", "-progress", "pipe:1"))
        // Before the input: it seeks there (to the keyframe before it when copying)
        if (cut.startMs > 0) addAll(listOf("-ss", seconds(cut.startMs)))
        addAll(listOf("-i", input.absolutePath))
        // What it lacks, as a second input (1): silence for sound of a silent video; for a video of sound
        // alone its cover (the file again, its picture repeated) or black
        val length = seconds((cut.lengthMs / o.speed).toLong())
        val silent = o.format.kind == Format.Kind.Audio && probe.audio == null
        val still = o.format.kind == Format.Kind.Video && probe.video == null
        when {
            silent -> addAll(listOf("-f", "lavfi", "-t", length, "-i", "anullsrc=r=48000:cl=stereo"))
            still && probe.cover -> addAll(listOf("-i", input.absolutePath))
            still -> addAll(listOf("-f", "lavfi", "-i", "color=c=black:s=1280x720:r=25"))
        }
        if (!cut.isWhole(probe)) addAll(listOf("-t", seconds(cut.lengthMs)))
        addAll(listOf("-map_metadata", "0", "-sn", "-dn"))
        when (o.format.kind) {
            Format.Kind.Audio -> {
                addAll(listOf("-map", if (silent) "1:a:0" else "0:a:0", "-vn"))
                addAll(audioArgs(o.format.audioEncoder!!, o.quality, rates?.audioKbps))
                if (!silent) atempo(o.speed)?.let { addAll(listOf("-af", it)) }
            }
            Format.Kind.Video if still -> {
                // The picture as long as the sound; a cover repeated (it's one frame), at 25 fps
                val picture = buildList {
                    if (probe.cover) { add("loop=loop=-1:size=1"); add("fps=25") }
                    if (o.height > 0) add("scale=-2:${o.height}:flags=lanczos")
                    add("scale=trunc(iw/2)*2:trunc(ih/2)*2")
                    add("format=yuv420p")
                }
                addAll(listOf("-filter_complex", "[1:v:0]${picture.joinToString(",")}[v]", "-map", "[v]", "-map", "0:a:0", "-shortest"))
                addAll(listOf("-c:v", o.codec.encoder!!))
                addAll(videoArgs(o, rates?.videoKbps))
                // A still picture: as few frames as it takes
                if (o.codec == VideoCodec.H264) addAll(listOf("-tune", "stillimage"))
                addAll(audioArgs(if (o.format == Format.WEBM) "libopus" else "aac", o.quality, rates?.audioKbps))
                atempo(o.speed)?.let { addAll(listOf("-af", it)) }
                if (o.codec == VideoCodec.H265 && o.format == Format.MP4) addAll(listOf("-tag:v", "hvc1"))
                if (o.format == Format.MP4) addAll(listOf("-movflags", "+faststart"))
            }
            Format.Kind.Gif -> {
                addAll(listOf("-map", "0:v:0", "-an"))
                val colors = when (o.quality) { Quality.High -> 256; Quality.Medium -> 128; Quality.Small -> 64 }
                val chain = videoFilters(probe, o, even = false) +
                    "split[a][b];[a]palettegen=max_colors=$colors:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle"
                addAll(listOf("-vf", chain.joinToString(","), "-loop", "0"))
            }
            Format.Kind.Video -> {
                add("-map"); add("0:v:0")
                val audio = probe.audio?.takeIf { o.sound }
                if (audio != null) { add("-map"); add("0:a:0") } else add("-an")
                if (o.codec == VideoCodec.Copy) {
                    addAll(listOf("-c:v", "copy"))
                    // Copied from a keyframe before the start: its timestamps from 0
                    addAll(listOf("-avoid_negative_ts", "make_zero"))
                } else {
                    addAll(listOf("-c:v", o.codec.encoder!!))
                    addAll(videoArgs(o, rates?.videoKbps))
                    // What plays everywhere (8 bits, 4:2:0), also from 10-bit HDR phone videos
                    addAll(listOf("-pix_fmt", "yuv420p"))
                    if (o.codec == VideoCodec.H265 && o.format == Format.MP4) addAll(listOf("-tag:v", "hvc1"))
                    addAll(listOf("-vf", videoFilters(probe, o, even = true).joinToString(",")))
                }
                if (audio != null) {
                    // Not with a size to aim at: its bitrate is part of it
                    val keep = o.speed == 1f && audioFits(o.format, audio.codec) && rates == null
                    if (keep) addAll(listOf("-c:a", "copy"))
                    else {
                        addAll(audioArgs(if (o.format == Format.WEBM) "libopus" else "aac", o.quality, rates?.audioKbps))
                        atempo(o.speed)?.let { addAll(listOf("-af", it)) }
                    }
                }
                if (o.format == Format.MP4) addAll(listOf("-movflags", "+faststart"))
            }
        }
        add(output.absolutePath)
    }

    /** Speed, frame rate, turning and size, in that order; [even] sizes for codecs that need them. */
    private fun videoFilters(probe: Probe, o: EncodeOptions, even: Boolean): List<String> = buildList {
        val video = probe.video
        if (o.speed != 1f) add("setpts=PTS/${o.speed}")
        if (o.fps > 0 && (video?.fps == null || o.fps < video.fps - 0.5)) add("fps=${o.fps}")
        when (o.rotate) {
            90 -> add("transpose=1")
            180 -> { add("transpose=1"); add("transpose=1") }
            270 -> add("transpose=2")
        }
        // Its height once turned (it's shown upright already: FFmpeg turns it as it decodes)
        val shownHeight = video?.let { if (o.rotate % 180 == 90) it.width else it.height }
        when {
            o.height > 0 && (shownHeight == null || o.height < shownHeight) -> add("scale=-2:${o.height}:flags=lanczos")
            even -> add("scale=trunc(iw/2)*2:trunc(ih/2)*2")
        }
    }

    /**
     * The picture's width and height as [videoFilters] makes it (of [o], [EncodeOptions.effective]). Null when
     * there's none to tell: sound alone, a still picture, copied as it is, or a size to aim at picking it.
     */
    fun outputSize(probe: Probe, o: EncodeOptions): Pair<Int, Int>? {
        val video = probe.video ?: return null
        if (o.format.kind == Format.Kind.Audio || o.codec == VideoCodec.Copy && o.format.kind == Format.Kind.Video) return null
        if (o.format.kind == Format.Kind.Video && o.height == 0 && o.targetMb > 0) return null
        val turned = o.rotate % 180 == 90
        val width = if (turned) video.height else video.width
        val height = if (turned) video.width else video.height
        val even = o.format.kind == Format.Kind.Video
        return when {
            // scale=-2:h: the width to its ratio, even
            o.height in 1..<height -> (width.toDouble() * o.height / height / 2).roundToInt() * 2 to o.height
            even -> width / 2 * 2 to height / 2 * 2
            else -> width to height
        }
    }

    /** Bitrates (kb/s) making a file of about [EncodeOptions.targetMb]; none: quality levels instead. */
    data class Bitrates(val videoKbps: Int?, val audioKbps: Int?)

    /**
     * What [o]'s size to aim at comes to for [cut] (at its speed): of a video, a tenth for its sound (32 to
     * 128 kb/s), the rest for the picture. [scale]: lower, after a try came out too big. Null without a size.
     */
    internal fun bitrates(probe: Probe, cut: Cut, o: EncodeOptions, scale: Double = 1.0): Bitrates? {
        if (o.targetMb <= 0 || !o.canTargetSize) return null
        val seconds = (cut.lengthMs / o.speed / 1000.0).coerceAtLeast(0.1)
        // kb/s, less what the container takes
        val total = o.targetMb * 8_000 / seconds * TARGET_MARGIN * scale
        if (o.format.kind == Format.Kind.Audio) return Bitrates(null, total.toInt().coerceIn(MIN_AUDIO_KBPS, MAX_AUDIO_KBPS))
        // Of sound alone the video is of it; of a video, its sound when it's kept
        val sound = probe.video == null || (o.sound && probe.audio != null)
        val audio = if (sound) (total / 10).toInt().coerceIn(MIN_AUDIO_KBPS, VIDEO_AUDIO_KBPS) else 0
        return Bitrates((total - audio).toInt().coerceAtLeast(MIN_VIDEO_KBPS), audio.takeIf { sound })
    }

    /**
     * The height to encode at: the one picked; with none ("Original"), the biggest of [EncodeOptions.HEIGHTS] at
     * which [videoKbps] still gives each pixel enough bits not to turn blocky (the original, 0, if it does).
     */
    internal fun autoHeight(probe: Probe, o: EncodeOptions, videoKbps: Int): Int {
        val video = probe.video ?: return o.height
        if (o.height > 0) return o.height
        val turned = o.rotate % 180 == 90
        val width = (if (turned) video.height else video.width).toDouble()
        val height = if (turned) video.width else video.height
        val fps = (if (o.fps > 0) o.fps.toDouble() else video.fps ?: 30.0).coerceAtLeast(1.0)
        // Bits per pixel and frame each codec looks fine at (the newer ones pack more in)
        val needed = when (o.codec) {
            VideoCodec.MPEG4 -> 0.1
            VideoCodec.H264 -> 0.07
            else -> 0.045
        }
        val heights = listOf(height) + EncodeOptions.HEIGHTS.filter { it < height }
        val fits = heights.firstOrNull { h -> videoKbps * 1000.0 / (h * (width / height * h) * fps) >= needed } ?: heights.last()
        return if (fits == height) 0 else fits
    }

    private fun videoArgs(o: EncodeOptions, kbps: Int? = null): List<String> {
        // To a size: its bitrate, kept from peaking far over it (x264's and x265's VBV)
        if (kbps != null) return when (o.codec) {
            VideoCodec.H264, VideoCodec.H265 -> listOf("-preset", "veryfast", "-b:v", "${kbps}k", "-maxrate", "${kbps * 3 / 2}k", "-bufsize", "${kbps * 2}k")
            VideoCodec.VP9 -> listOf("-deadline", "realtime", "-cpu-used", "8", "-row-mt", "1", "-b:v", "${kbps}k")
            VideoCodec.AV1 -> listOf("-preset", "10", "-b:v", "${kbps}k")
            VideoCodec.MPEG4 -> listOf("-b:v", "${kbps}k")
            VideoCodec.Copy -> emptyList()
        }
        fun pick(high: Int, medium: Int, small: Int) = when (o.quality) { Quality.High -> high; Quality.Medium -> medium; Quality.Small -> small }.toString()
        // Phones encode slowly: the fast settings, quality set by its level (CRF)
        return when (o.codec) {
            VideoCodec.H264 -> listOf("-preset", "veryfast", "-crf", pick(18, 23, 28))
            VideoCodec.H265 -> listOf("-preset", "veryfast", "-crf", pick(20, 26, 31))
            VideoCodec.VP9 -> listOf("-deadline", "realtime", "-cpu-used", "8", "-row-mt", "1", "-b:v", "0", "-crf", pick(30, 36, 42))
            VideoCodec.AV1 -> listOf("-preset", "10", "-crf", pick(28, 35, 42))
            VideoCodec.MPEG4 -> listOf("-q:v", pick(2, 5, 9))
            VideoCodec.Copy -> emptyList()
        }
    }

    private fun audioArgs(encoder: String, quality: Quality, kbps: Int? = null): List<String> {
        if (kbps != null && encoder in setOf("aac", "libopus", "libmp3lame")) return listOf("-c:a", encoder, "-b:a", "${kbps}k")
        fun pick(high: String, medium: String, small: String) = when (quality) { Quality.High -> high; Quality.Medium -> medium; Quality.Small -> small }
        return listOf("-c:a", encoder) + when (encoder) {
            "aac" -> listOf("-b:a", pick("256k", "192k", "128k"))
            "libopus" -> listOf("-b:a", pick("160k", "128k", "64k"))
            "libmp3lame" -> listOf("-q:a", pick("0", "2", "5"))
            else -> emptyList()
        }
    }

    /** Whether [format] takes sound in [codec] as it is. */
    private fun audioFits(format: Format, codec: String) = when (format) {
        Format.MP4 -> codec in setOf("aac", "mp3", "alac", "ac3", "eac3", "opus", "flac")
        Format.WEBM -> codec in setOf("opus", "vorbis")
        else -> true
    }

    private fun atempo(speed: Float) = if (speed == 1f) null else "atempo=$speed"

    private fun seconds(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)

    /** The job after FFmpeg printed [line] (its -progress output, e.g. "out_time_us=1234567", "speed=1.5x"). */
    private fun onLine(j: Job, line: String): Job {
        val key = line.substringBefore('=', "")
        val value = line.substringAfter('=', "").trim()
        return when (key) {
            "out_time_us" -> value.toLongOrNull()?.takeIf { it >= 0 }?.let { us ->
                j.copy(phase = Phase.Encoding, fraction = (us / 1000f / j.outputMs.coerceAtLeast(1)).coerceIn(0f, 1f))
            } ?: j.copy(phase = Phase.Encoding)
            "speed" -> j.copy(speed = value.removeSuffix("x").toDoubleOrNull())
            else -> j
        }
    }

    /** Where new files of [kind] go: a folder picked by the user, else where their kind goes (see [save]). */
    fun output(kind: Format.Kind) = when (kind) {
        Format.Kind.Video -> videoOutput
        Format.Kind.Audio -> audioOutput
        Format.Kind.Gif -> gifOutput
    }
    private val videoOutput = OutputFolder(VideoEncoderFeature.S_OUTPUT)
    private val audioOutput = OutputFolder(VideoEncoderFeature.S_OUTPUT_AUDIO)
    private val gifOutput = OutputFolder(VideoEncoderFeature.S_OUTPUT_GIF)

    /** Where [kind] goes when no folder is picked (Movies, Music, Pictures). */
    fun kindFolder(kind: Format.Kind) = when (kind) {
        Format.Kind.Video -> R.string.f_VidEnc_movies
        Format.Kind.Audio -> R.string.f_VidEnc_music
        Format.Kind.Gif -> R.string.f_VidEnc_pictures
    }

    /**
     * Copies [file] into the folder picked for its kind ([output]), else where its kind goes (Movies, Music,
     * Pictures), as the gallery and music apps find it.
     */
    private suspend fun save(context: Context, file: File, format: Format): SavedFile = withContext(Dispatchers.IO) {
        val output = output(format.kind)
        output.save(context, file, format.mimeType)?.let { uri ->
            return@withContext SavedFile(uri, displayName(context, uri) ?: file.name, format.mimeType, output.name(context, ""))
        }
        val (collection, dir) = when (format.kind) {
            Format.Kind.Video -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
            Format.Kind.Audio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MUSIC
            Format.Kind.Gif -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_PICTURES
        }
        val uri = saveToMediaStore(context, collection, dir, file.name, format.mimeType) { out -> file.inputStream().use { it.copyTo(out) } }
        SavedFile(uri, displayName(context, uri) ?: file.name, format.mimeType, context.getString(kindFolder(format.kind)))
    }
}
