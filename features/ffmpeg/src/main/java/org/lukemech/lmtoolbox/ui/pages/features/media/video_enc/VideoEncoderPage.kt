package org.lukemech.lmtoolbox.ui.pages.features.media.video_enc

import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import org.lukemech.lmtoolbox.ui.components.ErrorText
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.R as AppR
import org.lukemech.lmtoolbox.features.media.video_enc.Cut
import org.lukemech.lmtoolbox.features.media.video_enc.EncodeOptions
import org.lukemech.lmtoolbox.features.media.video_enc.Format
import org.lukemech.lmtoolbox.features.media.video_enc.Probe
import org.lukemech.lmtoolbox.features.media.video_enc.Quality
import org.lukemech.lmtoolbox.features.ffmpeg.R
import org.lukemech.lmtoolbox.features.media.video_enc.VideoCodec
import org.lukemech.lmtoolbox.features.media.video_enc.VideoJobs
import org.lukemech.lmtoolbox.features.media.file_size.FileSizeRequest
import org.lukemech.lmtoolbox.ui.components.ActionButton
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.helpers.ResourceScope
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import kotlin.time.Duration.Companion.milliseconds
import org.lukemech.lmtoolbox.features.media.video_enc.VideoEncoderFeature
import org.lukemech.lmtoolbox.helpers.SavedFile
import org.lukemech.lmtoolbox.helpers.shareFile
import org.lukemech.lmtoolbox.helpers.openFile
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.tasks.AppTasks

private enum class Step { File, Output, Cut, Encode }

/**
 * Video encoder in four steps, like the media downloader: the file (copied in, FFmpeg reads what's
 * in it), what to make of it, the part of it to keep (played to find it), and the encoding, which
 * goes on in the background when the page is left (see [VideoJobs]).
 */
@Composable
fun VideoEncoderPage(providedInput: String? = "") {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val job by VideoJobs.state.collectAsState()

    // Back on the page while something's being encoded: straight to it; else its first step (the open
    // file, or the last encoding, a tap away there)
    var step by rememberSaveable {
        mutableStateOf(if (VideoJobs.state.value?.isActive == true) Step.Encode else Step.File)
    }
    // What's open, and what's picked for it, stays when the page is left or redrawn (VideoJobs.opened, cut,
    // fileName, options): kept there as it changes, not once the page settles
    var opened by remember { mutableStateOf(VideoJobs.opened) }
    var cut by remember { mutableStateOf(VideoJobs.cut ?: opened?.let { Cut(0, it.probe.durationMs) }) }
    var fileName by remember { mutableStateOf(VideoJobs.fileName) }
    var options by remember { mutableStateOf(VideoJobs.options ?: EncodeOptions.load(context)) }
    var encoders by remember { mutableStateOf(VideoJobs.encoders) }
    SideEffect {
        VideoJobs.opened = opened
        VideoJobs.cut = cut
        VideoJobs.fileName = fileName
        VideoJobs.options = options
    }
    var busy by remember { mutableStateOf(false) }

    fun open(uri: Uri) {
        // Its copy is what's being encoded
        if (VideoJobs.isBusy()) {
            // Not an error: one is being encoded (its card a tap away)
            Toast.makeText(context, R.string.f_VidEnc_busy_open, Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            busy = true
            val result = VideoJobs.open(context, uri)
            busy = false
            result.onSuccess {
                opened = it
                cut = Cut(0, it.probe.durationMs)
                fileName = it.baseName
                encoders = VideoJobs.encoders
                VideoJobs.clear()
                step = Step.Output
            }
            // Failed: the bottom bar tells (see VideoJobs.open)
        }
    }

    fun startEncoding() {
        focusManager.clearFocus()
        val file = opened ?: return
        val part = cut ?: return
        EncodeOptions.save(context, options)
        VideoJobs.start(context, file, part, options, fileName)
        step = Step.Encode
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::open) }

    // Shared to the encoder: straight to the file
    LaunchedEffect(providedInput) {
        if (!providedInput.isNullOrBlank()) open(providedInput.toUri())
    }
    // Opened from the file size shortcut: a size to aim at, from the start
    LaunchedEffect(Unit) {
        if (FileSizeRequest.take(VideoEncoderFeature)) options = options.withTargetSize(opened?.probe, encoders)
    }

    // Left while encoding: it goes on, shown in the bottom bar
    DisposableEffect(Unit) {
        onDispose { VideoJobs.state.value?.takeIf { it.isActive }?.taskId?.let(AppTasks::detach) }
    }

    BackHandler(enabled = step != Step.File && !busy) {
        step = when (step) {
            Step.Cut -> Step.Output
            Step.Encode -> if (opened != null) Step.Cut else Step.File
            else -> Step.File
        }
    }

    // The encoding's progress is the last step's bar
    val active = job?.takeIf { step == Step.Encode && it.isActive }
    // Its line under the bars: while it runs, and once it failed (its log a tap away)
    val shown = job?.takeIf { step == Step.Encode && (it.isActive || it.phase == VideoJobs.Phase.Failed) }
    val steps = Steps(
        Step.entries.size, step.ordinal, busy,
        working = active != null,
        progress = active?.takeIf { it.phase == VideoJobs.Phase.Encoding }?.fraction,
        caption = shown?.let { encodeCaption(it) },
        detail = active?.takeIf { it.phase == VideoJobs.Phase.Encoding }?.eta?.let { time(it * 1000L, tenths = false) },
        onCancel = active?.takeIf { it.taskId != null && it.phase != VideoJobs.Phase.Saving }?.let { { VideoJobs.cancel() } }
    )
    val title = stringResource(AppR.string.fp_VideoEnc)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val file = opened
            val part = cut
            val current = job
            when (step) {
                // What it makes and how it went up top; what it takes, or what to do next, at the bottom
                Step.Encode if current != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = if (file != null) Step.Cut else Step.File }) {
                            FileCard(current.opened)
                            Spacer(modifier = Modifier.height(24.dp))
                            EncodeResult(current)
                        }
                    },
                    buttons = {
                        EncodeButtons(
                            current,
                            onAnother = {
                                VideoJobs.clear()
                                step = Step.File
                            },
                            // The same file again, another way
                            onChange = {
                                VideoJobs.clear()
                                opened = current.opened
                                cut = current.cut
                                options = current.options
                                fileName = current.fileName
                                step = Step.Output
                            }
                        )
                    }
                )

                Step.Output if file != null && part != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = Step.File }) {
                            FileCard(file, part)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutputForm(file.probe, encoders, options, { options = it }, fileName, { fileName = it })
                        }
                    },
                    buttons = {
                        BigButton(Icons.AutoMirrored.Default.ArrowForward, stringResource(R.string.f_VidEnc_next), enabled = fileName.isNotBlank()) {
                            focusManager.clearFocus()
                            step = Step.Cut
                        }
                    }
                )

                Step.Cut if file != null && part != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = Step.Output }) {
                            FileCard(file)
                            Spacer(modifier = Modifier.height(16.dp))
                            CutEditor(file, part) { cut = it }
                            // What the size to aim at comes to for this part
                            VideoJobs.bitrates(file.probe, part, options.effective(file.probe, encoders))?.let { rates ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Note(stringResource(R.string.f_VidEnc_target_rate, (rates.videoKbps ?: 0) + (rates.audioKbps ?: 0)))
                                }
                            }
                        }
                    },
                    buttons = {
                        ErrorText(stringResource(R.string.f_VidEnc_busy).takeIf { current?.isActive == true })
                        BigButton(
                            Icons.Default.VideoSettings, stringResource(R.string.f_VidEnc_encode),
                            enabled = current?.isActive != true && fileName.isNotBlank()
                        ) { startEncoding() }
                    }
                )

                else -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, verticalArrangement = Arrangement.Center) {
                            ToolIntro(
                                Icons.Default.VideoSettings, stringResource(R.string.f_VidEnc_intro_title),
                                listOf(
                                    Icons.Default.FolderOpen to stringResource(R.string.f_VidEnc_intro_1),
                                    Icons.Default.Tune to stringResource(R.string.f_VidEnc_intro_3),
                                    Icons.Default.ContentCut to stringResource(R.string.f_VidEnc_intro_2),
                                ),
                                stringResource(R.string.f_VidEnc_intro_tip)
                            )
                        }
                    },
                    buttons = {
                        // The current (or last) encoding, or the open file, stays a tap away
                        when {
                            current != null -> SecondaryButton(stringResource(R.string.f_VidEnc_back_to, current.fileName), !busy) { step = Step.Encode }
                            file != null -> SecondaryButton(stringResource(R.string.f_VidEnc_back_to, file.name), !busy) { step = Step.Output }
                        }
                        BigButton(Icons.Default.FolderOpen, stringResource(R.string.f_VidEnc_pick), enabled = !busy) {
                            picker.launch(arrayOf("video/*", "audio/*"))
                        }
                    }
                )
            }
        }
    }
}

/** The file: its name, how long it is (or the part kept of it), its picture's size and codecs, its size. */
@Composable
private fun FileCard(file: VideoJobs.Opened, cut: Cut? = null) {
    val context = LocalContext.current
    val probe = file.probe
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Icon(
            if (probe.video != null) Icons.Default.VideoFile else Icons.Default.AudioFile,
            contentDescription = null, tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            val length = if (cut != null && !cut.isWhole(probe)) {
                stringResource(R.string.f_VidEnc_cut_of, time(cut.lengthMs), time(probe.durationMs))
            } else time(probe.durationMs)
            val details = listOfNotNull(
                length,
                probe.video?.let { "${it.width}×${it.height}" },
                listOfNotNull(probe.video?.codec, probe.audio?.codec).joinToString(" + ").uppercase().ifEmpty { null },
                probe.size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The part to keep: the file played (a video's picture, else just its sound), its frames along
 * the range to cut it to, and the start and end where it's at.
 */
@Composable
private fun CutEditor(file: VideoJobs.Opened, cut: Cut, onCut: (Cut) -> Unit) {
    val probe = file.probe
    val duration = probe.durationMs.coerceAtLeast(1)
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var position by remember(file) { mutableLongStateOf(cut.startMs) }
    val currentCut by rememberUpdatedState(cut)

    fun seek(ms: Long) {
        position = ms
        // To the frame, not the keyframe before it
        runCatching { player?.seekTo(ms, MediaPlayer.SEEK_CLOSEST) }
    }
    fun pause() {
        // Only a started one: pausing a prepared (never started) player puts it in its error
        // state, reported later, not thrown (VideoView then shows "Can't play this video")
        if (playing) runCatching { player?.pause() }
        playing = false
    }

    if (probe.video != null) {
        val ratio = probe.video.width.toFloat() / probe.video.height
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(file.copy.absolutePath)
                        setOnPreparedListener { mp ->
                            player = mp
                            mp.seekTo(position, MediaPlayer.SEEK_CLOSEST)
                        }
                        setOnCompletionListener { playing = false }
                        // Handled: it just stops (the frames and the range still work), no dialog over the editor
                        setOnErrorListener { _, _, _ ->
                            playing = false
                            true
                        }
                    }
                },
                onRelease = {
                    player = null
                    it.stopPlayback()
                },
                // Portrait ones fit the height, landscape ones the width
                modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1)
            )
        }
    } else {
        // Just its sound
        DisposableEffect(file) {
            val mp = MediaPlayer()
            runCatching {
                mp.setDataSource(file.copy.absolutePath)
                mp.setOnPreparedListener { player = it }
                mp.setOnCompletionListener { playing = false }
                mp.prepareAsync()
            }
            onDispose {
                player = null
                mp.release()
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        }
    }

    // Where it's at while it plays; it stops at the end of the part
    LaunchedEffect(playing, player) {
        val p = player ?: return@LaunchedEffect
        while (playing) {
            val at = runCatching { p.currentPosition.toLong() }.getOrNull() ?: break
            position = at
            if (at >= currentCut.endMs) {
                pause()
                seek(currentCut.startMs)
            }
            delay(50.milliseconds)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(
            enabled = player != null,
            onClick = {
                if (playing) pause()
                else {
                    // From the start of the part, unless it's within it
                    if (position < cut.startMs || position >= cut.endMs - 50) seek(cut.startMs)
                    runCatching { player?.start() }
                    playing = true
                }
            }
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.f_VidEnc_pause else R.string.f_VidEnc_play)
            )
        }
        Text("${time(position)} / ${time(probe.durationMs)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { pause(); onCut(Cut(position.coerceAtMost(cut.endMs - MIN_CUT_MS).coerceAtLeast(0), cut.endMs)) }) {
            Text(stringResource(R.string.f_VidEnc_set_start))
        }
        TextButton(onClick = { pause(); onCut(Cut(cut.startMs, position.coerceAtLeast(cut.startMs + MIN_CUT_MS).coerceAtMost(duration))) }) {
            Text(stringResource(R.string.f_VidEnc_set_end))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (probe.video != null) FrameStrip(file)
        RangeSlider(
            value = cut.startMs.toFloat()..cut.endMs.toFloat(),
            onValueChange = { range ->
                var start = range.start.toLong()
                var end = range.endInclusive.toLong()
                // Never shorter than a moment
                if (end - start < MIN_CUT_MS) {
                    if (start != cut.startMs) start = (end - MIN_CUT_MS).coerceAtLeast(0) else end = (start + MIN_CUT_MS).coerceAtMost(duration)
                }
                val movedStart = start != cut.startMs
                pause()
                onCut(Cut(start, end))
                // Shows the frame where the moved end is
                seek(if (movedStart) start else end)
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            CutTime(stringResource(R.string.f_VidEnc_start), cut.startMs, Alignment.Start, Modifier.weight(1f))
            CutTime(stringResource(R.string.f_VidEnc_length), cut.lengthMs, Alignment.CenterHorizontally, Modifier.weight(1f))
            CutTime(stringResource(R.string.f_VidEnc_end), cut.endMs, Alignment.End, Modifier.weight(1f))
        }
        Reveal(visible = !cut.isWhole(probe), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            TextButton(onClick = { pause(); onCut(Cut(0, probe.durationMs)) }) {
                Text(stringResource(R.string.f_VidEnc_whole))
            }
        }
    }
}

private const val MIN_CUT_MS = 100L
private const val FRAMES = 8

@Composable
private fun CutTime(label: String, ms: Long, alignment: Alignment.Horizontal, modifier: Modifier) {
    Column(horizontalAlignment = alignment, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(time(ms), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Frames along the whole video, under the range it's cut to (read one by one, as they come). */
@Composable
private fun FrameStrip(file: VideoJobs.Opened) {
    val frames by produceState<List<ImageBitmap?>>(List(FRAMES) { null }, file) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.copy.absolutePath)
                val step = file.probe.durationMs * 1000 / FRAMES
                for (i in 0 until FRAMES) {
                    val frame = runCatching {
                        retriever.getScaledFrameAtTime(step * i + step / 2, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160)
                    }.getOrNull()?.asImageBitmap()
                    value = value.toMutableList().also { it[i] = frame }
                }
            } catch (_: Exception) {
                // No frames: just the range
            } finally {
                retriever.release()
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        frames.forEach { frame ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (frame != null) Image(frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The new file's name, format and, as it is or encoded, how: codec, size, quality, frame rate,
 * speed, turning, sound; first whether it's a picture (video, GIF) or sound alone, and last where
 * it goes ([folder]: off in the settings, which list each type's). Without [probe] (the defaults, in
 * the settings) every choice, and no file name; without [encoders] (not known yet) every codec.
 */
@Composable
fun OutputForm(
    probe: Probe?,
    encoders: Set<String>?,
    options: EncodeOptions,
    onChange: (EncodeOptions) -> Unit,
    fileName: String? = null,
    onFileNameChange: (String) -> Unit = {},
    folder: Boolean = true,
) {
    val o = options.effective(probe, encoders)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            if (fileName != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.f_VidEnc_file_name), style = MaterialTheme.typography.labelLarge)
                    InputField(
                        value = fileName,
                        onValueChange = onFileNameChange,
                        singleLine = true,
                        suffix = { Text(".${o.format.ext}") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ModeChoice(o.isAudio) { onChange(options.withMode(it, probe, encoders)) }
            // Right under it: the size to aim at, of what can have one (it rules out choosing a quality)
            Reveal(visible = o.canTargetSize) { TargetSizeChoice(probe, o.targetMb) { onChange(options.copy(targetMb = it)) } }
            // The formats of what it makes: video ones and GIF, or sound ones
            val formats = Format.entries.filter { it.isAvailable(probe, encoders) && (it.kind == Format.Kind.Audio) == o.isAudio }
            Chips(
                stringResource(R.string.f_VidEnc_format),
                formats.map { it to it.ext.uppercase() },
                o.format
            ) { onChange(options.copy(format = it)) }

            // Each kind's own choices (the last ones it had while they fold away)
            KindChoices(Format.Kind.Video, o) { k ->
                val codecs = VideoCodec.forFormat(k.format, probe, encoders)
                Chips(
                    stringResource(R.string.f_VidEnc_video),
                    codecs.map { it to if (it == VideoCodec.Copy) stringResource(R.string.f_VidEnc_copy) else it.label },
                    k.codec
                ) { onChange(options.copy(codec = it)) }
                Reveal(visible = k.codec == VideoCodec.Copy) {
                    Note(stringResource(if (codecs.size == 1) R.string.f_VidEnc_copy_only else R.string.f_VidEnc_copy_note))
                }
                Reveal(visible = k.codec != VideoCodec.Copy) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Only sizes under what it is
                        val shown = probe?.video?.let { if (k.rotate % 180 == 90) it.width else it.height }
                        Chips(
                            stringResource(R.string.f_VidEnc_resolution),
                            listOf(0 to stringResource(R.string.f_VidEnc_original) + (shown?.let { " · ${it}p" } ?: "")) +
                                EncodeOptions.HEIGHTS.filter { shown == null || it < shown }.map { it to "${it}p" },
                            k.height
                        ) { onChange(options.copy(height = it)) }
                        ResultNote(probe, k)
                        // A quality, unless there's a size to aim at (at the top)
                        Reveal(visible = k.targetMb <= 0) { QualityChoice(k.quality) { onChange(options.copy(quality = it)) } }
                        // Of sound alone the picture stands still (its cover, or black): nothing to time or turn
                        val still = probe != null && probe.video == null
                        val fps = probe?.video?.fps
                        if (!still) Chips(
                            stringResource(R.string.f_VidEnc_fps),
                            listOf(0 to stringResource(R.string.f_VidEnc_original) + (fps?.let { " · ${"%.0f".format(it)}" } ?: "")) +
                                EncodeOptions.FPS.filter { fps == null || it < fps - 0.5 }.map { it to "$it" },
                            k.fps
                        ) { onChange(options.copy(fps = it)) }
                        Chips(stringResource(R.string.f_VidEnc_speed), EncodeOptions.SPEEDS.map { it to speed(it) }, k.speed) { onChange(options.copy(speed = it)) }
                        if (!still) Chips(stringResource(R.string.f_VidEnc_rotate), EncodeOptions.ROTATIONS.map { it to "$it°" }, k.rotate) { onChange(options.copy(rotate = it)) }
                    }
                }
            }
            KindChoices(Format.Kind.Gif, o) { k ->
                Chips(stringResource(R.string.f_VidEnc_resolution), EncodeOptions.GIF_HEIGHTS.map { it to "${it}p" }, k.height) { onChange(options.copy(height = it)) }
                ResultNote(probe, k)
                Chips(stringResource(R.string.f_VidEnc_fps), EncodeOptions.GIF_FPS.map { it to "$it" }, k.fps) { onChange(options.copy(fps = it)) }
                QualityChoice(k.quality) { onChange(options.copy(quality = it)) }
                Chips(stringResource(R.string.f_VidEnc_speed), EncodeOptions.SPEEDS.map { it to speed(it) }, k.speed) { onChange(options.copy(speed = it)) }
                Chips(stringResource(R.string.f_VidEnc_rotate), EncodeOptions.ROTATIONS.map { it to "$it°" }, k.rotate) { onChange(options.copy(rotate = it)) }
            }
            KindChoices(Format.Kind.Audio, o) { k ->
                Reveal(visible = k.format.hasQuality && k.targetMb <= 0) { QualityChoice(k.quality) { onChange(options.copy(quality = it)) } }
                Chips(stringResource(R.string.f_VidEnc_speed), EncodeOptions.SPEEDS.map { it to speed(it) }, k.speed) { onChange(options.copy(speed = it)) }
            }
        }

        // The buttons apart as in the settings
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // A video's sound, as wide as in the settings (of sound alone, it's what the video is of)
            Reveal(visible = o.format.kind == Format.Kind.Video && (probe == null || (probe.video != null && probe.audio != null))) {
                SwitchButton(stringResource(R.string.f_VidEnc_sound), Icons.AutoMirrored.Default.VolumeUp, o.sound, subtitle = stringResource(R.string.f_VidEnc_sound_desc)) {
                    onChange(options.copy(sound = it))
                }
            }
            // Where it goes (for the next ones of its type too), as in the media downloader
            if (folder) FolderSetting(o.format.kind)
        }
    }
}

/**
 * [content] of the choices only [kind] has, while [o] is of it; the last options it had are kept
 * while they fold away (the next kind's unfolding in their place).
 */
@Composable
private fun KindChoices(kind: Format.Kind, o: EncodeOptions, content: @Composable (EncodeOptions) -> Unit) {
    val last = remember { arrayOfNulls<EncodeOptions>(1) }
    if (o.format.kind == kind) last[0] = o
    Reveal(visible = o.format.kind == kind) {
        last[0]?.let { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content(it) } }
    }
}

/** Where new files of [kind] go: a folder picked for it, else where that kind goes (Movies, Music, Pictures). */
@Composable
fun FolderSetting(kind: Format.Kind) {
    VideoJobs.output(kind).Setting(
        stringResource(
            when (kind) {
                Format.Kind.Video -> R.string.f_VidEnc_save_videos_to
                Format.Kind.Audio -> R.string.f_VidEnc_save_audio_to
                Format.Kind.Gif -> R.string.f_VidEnc_save_gifs_to
            }
        ),
        stringResource(VideoJobs.kindFolder(kind)),
        stringResource(R.string.f_VidEnc_save_to_reset)
    )
}

/**
 * What it makes: a video (or GIF) or sound alone, of any file: a video of sound alone shows its
 * cover (or black), sound of a silent video is silence.
 */
@Composable
fun ModeChoice(audio: Boolean, onSelect: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.f_VidEnc_mode), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = listOf(
                Triple(false, Icons.Default.Movie, R.string.f_VidEnc_mode_video),
                Triple(true, Icons.Default.MusicNote, R.string.f_VidEnc_mode_audio),
            )
            modes.forEachIndexed { i, (isAudio, icon, label) ->
                SegmentedButton(
                    selected = audio == isAudio,
                    onClick = { onSelect(isAudio) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) { Text(stringResource(label)) }
            }
        }
    }
}

@Composable
private fun <T> Chips(label: String, values: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { (value, text) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(text) })
            }
        }
    }
}

@Composable
private fun QualityChoice(selected: Quality, onSelect: (Quality) -> Unit) {
    val labels = listOf(
        Quality.High to R.string.f_VidEnc_quality_high,
        Quality.Medium to R.string.f_VidEnc_quality_medium,
        Quality.Small to R.string.f_VidEnc_quality_small
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.f_VidEnc_quality), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, (quality, label) ->
                SegmentedButton(
                    selected = quality == selected,
                    onClick = { onSelect(quality) },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    icon = {}
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
    }
}

/**
 * The file's size to aim at, in MB: none (a quality instead), one of [EncodeOptions.TARGET_SIZES] under the
 * file's own size (any, in the settings), or another one typed in.
 */
@Composable
private fun TargetSizeChoice(probe: Probe?, selected: Double, onSelect: (Double) -> Unit) {
    val presets = EncodeOptions.TARGET_SIZES.filter { probe == null || probe.size <= 0 || it * 1_000_000 < probe.size }
    var other by remember { mutableStateOf(selected > 0 && selected !in presets) }
    var text by remember { mutableStateOf(if (selected > 0 && selected !in presets) megabytes(selected) else "") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.f_VidEnc_target), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected <= 0 && !other,
                onClick = { other = false; onSelect(0.0) },
                label = { Text(stringResource(R.string.f_VidEnc_target_none)) }
            )
            presets.forEach { mb ->
                FilterChip(
                    selected = !other && selected == mb,
                    onClick = { other = false; onSelect(mb) },
                    label = { Text("${megabytes(mb)} MB") }
                )
            }
            FilterChip(
                selected = other,
                onClick = {
                    other = true
                    text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onSelect)
                },
                label = { Text(stringResource(R.string.f_VidEnc_target_other)) }
            )
        }
        Reveal(visible = other) {
            InputField(
                value = text,
                onValueChange = { typed ->
                    text = typed.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
                    text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onSelect)
                },
                singleLine = true,
                suffix = { Text("MB") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Reveal(visible = selected > 0) { Note(stringResource(R.string.f_VidEnc_target_note)) }
    }
}

/** e.g. 10, 2.5 */
private fun megabytes(mb: Double) = if (mb % 1.0 == 0.0) "%.0f".format(mb) else "%.1f".format(mb)

/** What the size (and turning) comes to, under the sizes, as in the image resizer; nothing when it can't be told. */
@Composable
private fun ResultNote(probe: Probe?, o: EncodeOptions) {
    val (width, height) = probe?.let { VideoJobs.outputSize(it, o) } ?: return
    Note(stringResource(R.string.f_VidEnc_result, "$width×$height"))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The encoding's line under the step bar: what it's at (how far and how long it has left go after it), or that it failed. */
@Composable
private fun encodeCaption(j: VideoJobs.Job): String = stringResource(
    when {
        j.phase == VideoJobs.Phase.Failed -> R.string.f_VidEnc_failed
        j.phase == VideoJobs.Phase.Saving -> R.string.f_VidEnc_saving
        // Too big the last time: again, at lower bitrates
        j.retry -> R.string.f_VidEnc_encoding_again
        j.phase == VideoJobs.Phase.Starting -> R.string.f_VidEnc_starting
        else -> R.string.f_VidEnc_encoding
    }
)

/** How the encoding went, once it has: the file saved (where, under what name), or that it failed or was canceled. */
@Composable
private fun EncodeResult(j: VideoJobs.Job) {
    // Under way, nothing: its bar (what it's at, how far, cancelling it), and what it takes at the bottom
    val failedOrCancelled = j.phase == VideoJobs.Phase.Failed || j.phase == VideoJobs.Phase.Cancelled
    // The saved file (kept while it folds away)
    val last = remember { arrayOfNulls<SavedFile>(1) }
    j.saved?.let { last[0] = it }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Reveal(visible = j.phase == VideoJobs.Phase.Done && j.saved != null) {
            val saved = last[0] ?: return@Reveal
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.f_VidEnc_saved, saved.folder), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    saved.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis
                )
                if (j.missedTarget) ErrorText(stringResource(R.string.f_VidEnc_missed_target, megabytes(j.options.targetMb)))
            }
        }
        Reveal(visible = failedOrCancelled) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val failed = j.phase == VideoJobs.Phase.Failed
                Icon(
                    if (failed) Icons.Default.ErrorOutline else Icons.Default.Close, contentDescription = null,
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(if (failed) R.string.f_VidEnc_failed else R.string.f_VidEnc_cancelled),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * At the bottom, as every tool's buttons: while it encodes, what FFmpeg takes; once it's done, what
 * to do with the file (open, share), or with the next one (the same another way, another file).
 * Each folds away as the next unfolds.
 */
@Composable
private fun EncodeButtons(j: VideoJobs.Job, onAnother: () -> Unit, onChange: () -> Unit) {
    val context = LocalContext.current
    val running = j.phase == VideoJobs.Phase.Starting || j.phase == VideoJobs.Phase.Encoding || j.phase == VideoJobs.Phase.Saving
    // The saved file (kept while its buttons fold away)
    val last = remember { arrayOfNulls<SavedFile>(1) }
    j.saved?.let { last[0] = it }
    Reveal(visible = running) { ResourceTiles(VideoEncoderFeature, scope = ResourceScope.Task(j.taskId)) }
    Reveal(visible = j.phase == VideoJobs.Phase.Done && j.saved != null) {
        val saved = last[0] ?: return@Reveal
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(Icons.AutoMirrored.Default.OpenInNew, stringResource(R.string.f_VidEnc_open)) { openFile(context, saved.uri, saved.mimeType) }
                ActionButton(Icons.Default.Share, stringResource(R.string.f_VidEnc_share)) { shareFile(context, saved.uri, saved.mimeType, "share.Video.Encode") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(Icons.Default.Tune, stringResource(R.string.f_VidEnc_change), onClick = onChange)
                ActionButton(Icons.Default.FolderOpen, stringResource(R.string.f_VidEnc_another), onClick = onAnother)
            }
        }
    }
    // Again, as it was or another way
    Reveal(visible = j.phase == VideoJobs.Phase.Failed || j.phase == VideoJobs.Phase.Cancelled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(Icons.Default.Tune, stringResource(R.string.f_VidEnc_change), onClick = onChange)
            ActionButton(Icons.Default.FolderOpen, stringResource(R.string.f_VidEnc_another), onClick = onAnother)
        }
    }
}

/** e.g. 0.5×, 1×, 1.5× */
private fun speed(s: Float) = (if (s % 1f == 0f) "%.0f×" else "%.1f×").format(s)

/** e.g. 0:07.4, 3:07.0, 1:02:45.5; without [tenths] 3:07 */
private fun time(ms: Long, tenths: Boolean = true): String {
    val total = ms.coerceAtLeast(0)
    val h = total / 3_600_000
    val m = total % 3_600_000 / 60_000
    val s = total % 60_000 / 1000
    val t = total % 1000 / 100
    val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    return if (tenths) "$base.$t" else base
}
