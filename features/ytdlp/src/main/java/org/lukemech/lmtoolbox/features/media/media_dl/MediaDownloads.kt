package org.lukemech.lmtoolbox.features.media.media_dl

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale
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
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.helpers.SavedFile
import org.lukemech.lmtoolbox.helpers.displayName
import org.lukemech.lmtoolbox.helpers.safeFileName
import org.lukemech.lmtoolbox.helpers.saveToMediaStore
import org.lukemech.lmtoolbox.tasks.AppTasks

/** What yt-dlp found at a link: enough to pick what to download. */
data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val site: String?,
    val durationSec: Int?,
    val thumbnail: String?,
    val formats: List<Format>,
    /** yt-dlp's whole -J output, handed back to it for the download (see [MediaDownloads.start]). */
    val json: String? = null,
    /** When it was looked up (its format links only last a few hours). */
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    data class Format(val height: Int?, val hasVideo: Boolean, val hasAudio: Boolean, val size: Long?, val bitrate: Double)

    val hasVideo get() = formats.any { it.hasVideo }

    /** Video heights on offer, highest first. */
    val heights get() = formats.mapNotNull { if (it.hasVideo) it.height else null }.filter { it > 0 }.distinct().sortedDescending()

    /** Roughly how big the video at [maxHeight] (null: the best) gets with its sound; null if unknown. */
    fun videoSize(maxHeight: Int?): Long? {
        val height = heights.firstOrNull { maxHeight == null || it <= maxHeight } ?: return null
        val video = formats.filter { it.hasVideo && it.height == height }.maxByOrNull { it.bitrate } ?: return null
        val videoSize = video.size ?: return null
        if (video.hasAudio) return videoSize
        val audio = formats.filter { it.hasAudio && !it.hasVideo }.maxByOrNull { it.bitrate }
        return videoSize + (audio?.size ?: 0)
    }

    companion object {
        /** yt-dlp's -J output; null if it isn't a single video or track (e.g. a playlist). */
        fun parse(url: String, json: JSONObject): MediaInfo? {
            if (json.optString("_type", "video") != "video") return null
            val formats = json.optJSONArray("formats")?.let { array ->
                (0 until array.length()).map { array.getJSONObject(it) }.map { f ->
                    val vcodec = f.optString("vcodec", "")
                    val acodec = f.optString("acodec", "")
                    Format(
                        height = f.optInt("height", 0).takeIf { it > 0 },
                        // Unknown codecs (e.g. a site's single file) count as having both
                        hasVideo = vcodec != "none" && (vcodec.isNotEmpty() || f.optInt("height", 0) > 0 || acodec.isEmpty()),
                        hasAudio = acodec != "none",
                        size = f.optLong("filesize", 0).takeIf { it > 0 } ?: f.optLong("filesize_approx", 0).takeIf { it > 0 },
                        bitrate = f.optDouble("tbr", 0.0).takeIf { !it.isNaN() } ?: 0.0
                    )
                }
            }.orEmpty()
            return MediaInfo(
                url = json.optString("webpage_url", url).ifBlank { url },
                title = json.optString("title", "").ifBlank { json.optString("id", "media") },
                uploader = json.optString("uploader", "").ifBlank { json.optString("channel", "") }.ifBlank { null },
                site = json.optString("extractor_key", "").ifBlank { null },
                durationSec = json.optDouble("duration", 0.0).takeIf { !it.isNaN() && it > 0 }?.toInt(),
                thumbnail = json.optString("thumbnail", "").ifBlank { null },
                formats = formats,
                json = json.toString()
            )
        }
    }
}

/** What to make of a [MediaInfo]: a video (up to [maxHeight], in [container]) or its sound (as [audioFormat]). */
data class MediaOptions(
    val kind: Kind = Kind.Video,
    /** 0: the best there is. */
    val maxHeight: Int = 0,
    val container: String = "mp4",
    val audioFormat: String = "mp3",
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val subtitles: Boolean = false,
    val sponsorBlock: Boolean = true,
) {
    enum class Kind { Video, Audio }

    /** Whether the file can get a cover: WAV has nowhere to keep one; Opus and FLAC would need mutagen, which Termux's yt-dlp doesn't bring. */
    val hasCover get() = !(kind == Kind.Audio && audioFormat in setOf("wav", "opus", "flac"))

    companion object {
        /** The last download's choices, for the next one. */
        fun load(context: Context) = MediaOptions().let { d ->
            MediaOptions(
                kind = runCatching { Kind.valueOf(PreferenceHelper.get(context, MediaDownloaderFeature.S_KIND, d.kind.name)) }.getOrDefault(d.kind),
                maxHeight = PreferenceHelper.get(context, MediaDownloaderFeature.S_HEIGHT, d.maxHeight),
                container = PreferenceHelper.get(context, MediaDownloaderFeature.S_CONTAINER, d.container).takeIf { it in CONTAINERS } ?: d.container,
                audioFormat = PreferenceHelper.get(context, MediaDownloaderFeature.S_AUDIO, d.audioFormat).takeIf { it in AUDIO_FORMATS } ?: d.audioFormat,
                embedThumbnail = PreferenceHelper.get(context, MediaDownloaderFeature.S_THUMB, d.embedThumbnail),
                embedMetadata = PreferenceHelper.get(context, MediaDownloaderFeature.S_META, d.embedMetadata),
                subtitles = PreferenceHelper.get(context, MediaDownloaderFeature.S_SUBS, d.subtitles),
                sponsorBlock = PreferenceHelper.get(context, MediaDownloaderFeature.S_SPONSOR, d.sponsorBlock),
            )
        }

        fun save(context: Context, o: MediaOptions) {
            PreferenceHelper.put(context, MediaDownloaderFeature.S_KIND, o.kind.name)
            PreferenceHelper.put(context, MediaDownloaderFeature.S_HEIGHT, o.maxHeight)
            PreferenceHelper.put(context, MediaDownloaderFeature.S_CONTAINER, o.container)
            PreferenceHelper.put(context, MediaDownloaderFeature.S_AUDIO, o.audioFormat)
            PreferenceHelper.put(context, MediaDownloaderFeature.S_THUMB, o.embedThumbnail)
            PreferenceHelper.put(context, MediaDownloaderFeature.S_META, o.embedMetadata)
            PreferenceHelper.put(context, MediaDownloaderFeature.S_SUBS, o.subtitles)
            PreferenceHelper.put(context, MediaDownloaderFeature.S_SPONSOR, o.sponsorBlock)
        }

        val CONTAINERS = listOf("mp4", "mkv")
        val AUDIO_FORMATS = listOf("mp3", "m4a", "opus", "flac", "wav")
        val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)
    }
}

/**
 * Runs yt-dlp: [fetchInfo] for a link's details, [start] for the download, which goes on (shown in
 * the bottom bar) when the page is left and ends up in Downloads. One download at a time, in [state].
 */
object MediaDownloads {
    /** yt-dlp's options for every run: QuickJS for YouTube's player challenges, plain output. */
    private val BASE = listOf("yt-dlp", "--js-runtimes", "quickjs", "--no-playlist", "--no-colors")
    private const val INFO_TIMEOUT_MS = 180_000L
    /** How long a lookup's format links are used for the download, instead of looking it up again. */
    private const val INFO_REUSE_MS = 60 * 60_000L
    private const val PROGRESS_PREFIX = "[dl] "

    /** Post-processors (their output's `[Name]` prefix) that run once the download is done. */
    private val POST_PROCESSORS = setOf(
        "Merger", "ExtractAudio", "EmbedThumbnail", "Metadata", "VideoRemuxer", "VideoConvertor",
        "SponsorBlock", "ModifyChapters", "EmbedSubtitle", "ThumbnailsConvertor", "SubtitlesConvertor",
        "FixupM3u8", "FixupM4a", "FixupStretched", "FixupDuplicateMoov", "FixupTimestamp", "MoveFiles"
    )

    data class Download(
        val info: MediaInfo,
        val options: MediaOptions,
        val fileName: String,
        val taskId: Long? = null,
        val phase: Phase = Phase.Starting,
        /** 0..1 of the current stream, null while unknown. */
        val fraction: Float? = null,
        val downloaded: Long? = null,
        val total: Long? = null,
        val speed: Double? = null,
        val eta: Int? = null,
        /** Which of [streams] (e.g. the video, then its sound) is downloading, from 1. */
        val stream: Int = 0,
        val streams: Int = 1,
        /** The post-processor running, e.g. "Merger". */
        val step: String? = null,
        val saved: SavedFile? = null,
        val error: String? = null,
    ) {
        val isActive get() = phase == Phase.Starting || phase == Phase.Downloading || phase == Phase.Processing

        /** 0..100 over all the streams, for the task's progress. */
        val overall: Float? get() = fraction?.let { (((stream - 1).coerceAtLeast(0) + it) / streams.coerceAtLeast(1)) * 100 }
    }

    enum class Phase { Starting, Downloading, Processing, Done, Failed, Cancelled }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The last link looked up (and the file name picked for it): back to it after the page was left. */
    var found: Pair<MediaInfo, String>? = null

    private val _state = MutableStateFlow<Download?>(null)
    val state: StateFlow<Download?> = _state.asStateFlow()

    /** The first link in [text] (shared text is often a title and a link), or [text] itself. */
    fun linkIn(text: String): String = Regex("""https?://\S+""").find(text)?.value ?: text.trim()

    /** The link's details, or the error yt-dlp gave (or null when it said nothing useful). */
    suspend fun fetchInfo(context: Context, url: String): Result<MediaInfo> {
        val result = LinuxTasks.run(
            context, BASE + listOf("-J", "--", url), MediaDownloaderFeature, timeoutMs = INFO_TIMEOUT_MS
        )
        if (result.isEnvNotReady) return Result.failure(Exception())
        val out = result.stdout
        val json = out.indexOf('{').takeIf { it >= 0 }?.let { runCatching { JSONObject(out.substring(it)) }.getOrNull() }
        val info = json?.let { MediaInfo.parse(url, it) }
        return when {
            info != null -> Result.success(info)
            // Not a command's failure (a playlist): told in the one place errors are, the bottom bar
            json != null -> Result.failure<MediaInfo>(UnsupportedOperationException()).also {
                AppTasks.reportFailure(MediaDownloaderFeature, url, "Playlists aren't supported")
            }
            // What yt-dlp said is in the task's log (the bottom bar's)
            else -> Result.failure(Exception())
        }
    }

    fun start(context: Context, info: MediaInfo, options: MediaOptions, fileName: String) {
        if (_state.value?.isActive == true) return
        val app = context.applicationContext
        _state.value = Download(info, options, fileName)
        scope.launch {
            val dir = MediaDownloaderFeature.cacheDir(app).apply { deleteRecursively(); mkdirs() }
            suspend fun download(infoJson: File?): Boolean =
                LinuxTasks.runInBackground(
                    app, command(info, options, fileName, dir, infoJson), MediaDownloaderFeature,
                    label = fileName,
                    timeoutMs = Long.MAX_VALUE,
                    onStart = { id -> _state.update { it?.copy(taskId = id) } },
                    onLine = { line ->
                        val updated = _state.updateAndGet { it?.let { d -> onLine(d, line) } }
                        val percent = updated?.overall?.toInt() ?: return@runInBackground
                        updated.taskId?.let { AppTasks.setProgress(it, percent.toFloat()) }
                    }
                ).isSuccess
            fun isCancelled() = AppTasks.isCancelled(_state.value?.taskId)

            val infoJson = infoFile(info, dir)
            var success = download(infoJson)
            // The lookup's links didn't do (e.g. they're for the network the phone was on then): from the link, as
            // if looked up just now
            if (!success && infoJson != null && !isCancelled()) {
                dir.listFiles()?.forEach { it.delete() }
                _state.update { it?.copy(phase = Phase.Starting, stream = 0, streams = 1, fraction = null, step = null) }
                success = download(null)
            }
            val cancelled = isCancelled()
            val file = dir.listFiles()?.filter { it.isFile && it.name != "info.json" && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
            when {
                cancelled -> _state.update { it?.copy(phase = Phase.Cancelled) }
                // The card says it failed; what yt-dlp said is in the task's log
                !success || file == null -> _state.update { it?.copy(phase = Phase.Failed) }
                else -> {
                    _state.update { it?.copy(phase = Phase.Processing, step = null) }
                    val saved = runCatching { save(app, file) }.getOrNull()
                    _state.update {
                        if (saved != null) it?.copy(phase = Phase.Done, saved = saved)
                        else it?.copy(phase = Phase.Failed, error = "Couldn't save the file")
                    }
                    if (saved == null) AppTasks.reportFailure(MediaDownloaderFeature, "", "Couldn't save the file")
                }
            }
            dir.deleteRecursively()
        }
    }

    fun cancel() {
        _state.value?.taskId?.let { AppTasks.cancel(it) }
    }

    /** Forgets a finished download (e.g. to download another). */
    fun clear() {
        if (_state.value?.isActive != true) _state.value = null
    }

    /**
     * The lookup's JSON as a file for --load-info-json, while its links are fresh: the download then starts
     * right away, without yt-dlp fetching the page (and solving YouTube's challenges) all over again.
     */
    private fun infoFile(info: MediaInfo, dir: File): File? {
        val json = info.json?.takeIf { System.currentTimeMillis() - info.fetchedAt < INFO_REUSE_MS } ?: return null
        return runCatching { File(dir, "info.json").apply { writeText(json) } }.getOrNull()
    }

    internal fun command(info: MediaInfo, o: MediaOptions, fileName: String, dir: File, infoJson: File?) = buildList {
        addAll(BASE)
        add("--newline")
        addAll(listOf("--progress-template", "download:$PROGRESS_PREFIX%(progress.downloaded_bytes)s %(progress.total_bytes)s %(progress.total_bytes_estimate)s %(progress.speed)s %(progress.eta)s"))
        addAll(listOf("-P", dir.absolutePath, "-o", outputTemplate(fileName)))
        // The file's date is today's, not the upload's
        add("--no-mtime")
        when (o.kind) {
            MediaOptions.Kind.Video -> {
                addAll(listOf("-f", "bv*+ba/b"))
                // The best up to the height; at it, for mp4 what plays everywhere (H.264, AAC) first
                val sort = listOfNotNull(
                    if (o.maxHeight > 0) "res:${o.maxHeight}" else "res",
                    if (o.container == "mp4") "vcodec:h264,acodec:aac" else null
                )
                addAll(listOf("-S", sort.joinToString(",")))
                addAll(listOf("--merge-output-format", o.container, "--remux-video", o.container))
                if (o.subtitles) {
                    addAll(listOf("--embed-subs", "--sub-langs", "${Locale.getDefault().language}.*,en.*"))
                }
            }
            MediaOptions.Kind.Audio -> {
                addAll(listOf("-f", "ba/b", "-x", "--audio-format", o.audioFormat, "--audio-quality", "0"))
            }
        }
        // WAV has nowhere to keep a cover
        if (o.embedThumbnail && o.hasCover) add("--embed-thumbnail")
        if (o.embedMetadata) add("--embed-metadata")
        if (o.sponsorBlock) addAll(listOf("--sponsorblock-remove", "sponsor,selfpromo,interaction"))
        if (infoJson != null) addAll(listOf("--load-info-json", infoJson.absolutePath))
        else {
            add("--")
            add(info.url)
        }
    }

    /** [fileName] as yt-dlp's output template: no path or odd characters, its own extension. */
    internal fun outputTemplate(fileName: String): String {
        val safe = safeFileName(fileName)
        return if (safe.isEmpty()) "%(title).150B.%(ext)s" else safe.replace("%", "%%") + ".%(ext)s"
    }

    /** The download's state after yt-dlp printed [line]. */
    internal fun onLine(d: Download, line: String): Download {
        if (line.startsWith(PROGRESS_PREFIX)) {
            val v = line.removePrefix(PROGRESS_PREFIX).split(' ')
            fun num(i: Int) = v.getOrNull(i)?.toDoubleOrNull()
            val done = num(0)?.toLong()
            val total = (num(1) ?: num(2))?.toLong()
            return d.copy(
                phase = Phase.Downloading,
                stream = d.stream.coerceAtLeast(1),
                downloaded = done,
                total = total,
                fraction = if (done != null && total != null && total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null,
                // Not told now and then ("NA"): the last ones stand, so what shows of them doesn't come and go
                speed = num(3) ?: d.speed,
                eta = num(4)?.toInt() ?: d.eta
            )
        }
        // e.g. "[info] abc: Downloading 1 format(s): 137+140"
        Regex("""^\[info] .*Downloading \d+ format\(s\): (\S+)""").find(line)?.let { m ->
            return d.copy(streams = m.groupValues[1].count { it == '+' } + 1)
        }
        if (line.startsWith("[download] Destination:")) {
            return d.copy(phase = Phase.Downloading, stream = (d.stream + 1).coerceAtMost(d.streams), fraction = 0f, downloaded = null, total = null, eta = null)
        }
        val name = Regex("""^\[(\w+)]""").find(line)?.groupValues?.get(1)
        if (name in POST_PROCESSORS && d.phase != Phase.Starting) {
            return d.copy(phase = Phase.Processing, step = name, fraction = null)
        }
        return d
    }

    /** Copies [file] where downloads go (see [MediaOutput]); streamed, it can be big. */
    private suspend fun save(context: Context, file: File): SavedFile = withContext(Dispatchers.IO) {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val uri = MediaOutput.save(context, file, mimeType) ?: saveToMediaStore(
            context, MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), Environment.DIRECTORY_DOWNLOADS,
            file.name, mimeType
        ) { out -> file.inputStream().use { it.copyTo(out) } }
        SavedFile(uri, displayName(context, uri) ?: file.name, mimeType, MediaOutput.outputFolderName(context))
    }
}
