package org.lukemech.lmtoolbox.ui.pages.features.media.media_dl

import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import org.lukemech.lmtoolbox.ui.components.ErrorText
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R as AppR
import org.lukemech.lmtoolbox.features.ytdlp.R
import org.lukemech.lmtoolbox.features.media.media_dl.MediaOutput
import org.lukemech.lmtoolbox.features.media.media_dl.MediaDownloads
import org.lukemech.lmtoolbox.features.media.media_dl.MediaInfo
import org.lukemech.lmtoolbox.features.media.media_dl.MediaOptions
import org.lukemech.lmtoolbox.ui.components.ActionButton
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.helpers.ResourceScope
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.helpers.rememberUrlImage
import org.lukemech.lmtoolbox.features.media.media_dl.MediaDownloaderFeature
import org.lukemech.lmtoolbox.helpers.SavedFile
import org.lukemech.lmtoolbox.helpers.shareFile
import org.lukemech.lmtoolbox.helpers.openFile
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.ROW_SPACING
import org.lukemech.lmtoolbox.tasks.AppTasks

private enum class Step { Link, Options, Download }

/**
 * Media downloader in three steps, like the metadata editor: the link (yt-dlp looks it up), what
 * to make of it (a video up to a quality, or its sound in a format), and the download, which goes
 * on in the background when the page is left (see [MediaDownloads]).
 */
@Composable
fun MediaDownloaderPage(providedInput: String? = "") {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val download by MediaDownloads.state.collectAsState()

    // Back on the page while something downloads (or has): straight to it
    var step by rememberSaveable { mutableStateOf(if (MediaDownloads.state.value != null) Step.Download else Step.Link) }
    var link by rememberSaveable { mutableStateOf("") }
    // What was looked up stays when the page is left (MediaDownloads.found)
    var info by remember { mutableStateOf(MediaDownloads.found?.first) }
    var options by remember { mutableStateOf(MediaOptions.load(context)) }
    var fileName by remember { mutableStateOf(MediaDownloads.found?.second.orEmpty()) }
    LaunchedEffect(info, fileName) { MediaDownloads.found = info?.let { it to fileName } }
    var busy by remember { mutableStateOf(false) }

    fun find() {
        val url = MediaDownloads.linkIn(link)
        if (url.isBlank() || busy) return
        focusManager.clearFocus()
        coroutineScope.launch {
            busy = true
            val result = MediaDownloads.fetchInfo(context, url)
            busy = false
            result.onSuccess {
                info = it
                fileName = it.title
                step = Step.Options
            }
            // Failed: the bottom bar tells (yt-dlp's task, or see MediaDownloads.fetchInfo)
        }
    }

    fun startDownload() {
        focusManager.clearFocus()
        val found = info ?: return
        MediaOptions.save(context, options)
        MediaDownloads.start(context, found, effective(found, options), fileName)
        step = Step.Download
    }

    // Shared to the downloader: straight to what's at the link
    LaunchedEffect(providedInput) {
        if (!providedInput.isNullOrBlank()) {
            link = MediaDownloads.linkIn(providedInput)
            step = Step.Link
            find()
        }
    }

    // Left while downloading: it goes on, shown in the bottom bar
    DisposableEffect(Unit) {
        onDispose { MediaDownloads.state.value?.takeIf { it.isActive }?.taskId?.let(AppTasks::detach) }
    }

    BackHandler(enabled = step != Step.Link && !busy) { step = Step.Link }

    // Where it stands, under each step's title
    // The download's progress is the last step's bar
    val active = download?.takeIf { step == Step.Download && it.isActive }
    // Its line under the bars: while it runs, and once it failed (its log a tap away)
    val shown = download?.takeIf { step == Step.Download && (it.isActive || it.phase == MediaDownloads.Phase.Failed) }
    val steps = Steps(
        Step.entries.size, step.ordinal, busy,
        working = active != null,
        progress = active?.takeIf { it.phase == MediaDownloads.Phase.Downloading }?.overall?.div(100),
        caption = shown?.let { downloadCaption(it) },
        detail = active?.takeIf { it.phase == MediaDownloads.Phase.Downloading }?.eta?.let(::duration),
        onCancel = active?.takeIf { it.taskId != null }?.let { { MediaDownloads.cancel() } }
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val found = info
            val current = download
            when (step) {
                // What it gets and how it went up top; what it takes, or what to do next, at the bottom
                Step.Download if current != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = stringResource(AppR.string.fp_MediaDl), onBack = { step = Step.Link }) {
                            DownloadResult(current)
                        }
                    },
                    buttons = {
                        DownloadButtons(
                            current,
                            onAnother = {
                                MediaDownloads.clear()
                                link = ""
                                info = null
                                step = Step.Link
                            },
                            // The same media again, another way
                            onChangeFormat = {
                                MediaDownloads.clear()
                                info = current.info
                                options = current.options
                                fileName = current.fileName
                                step = Step.Options
                            }
                        )
                    }
                )

                Step.Options if found != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = stringResource(AppR.string.fp_MediaDl), onBack = { step = Step.Link }) {
                            MediaCard(found)
                            Spacer(modifier = Modifier.height(16.dp))
                            OptionsForm(found, options, { options = it }, fileName, { fileName = it })
                        }
                    },
                    buttons = {
                        ErrorText(stringResource(R.string.f_MediaDl_busy).takeIf { current?.isActive == true })
                        BigButton(
                            Icons.Default.Download, stringResource(R.string.f_MediaDl_download),
                            enabled = current?.isActive != true && fileName.isNotBlank()
                        ) { startDownload() }
                    }
                )

                else -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = stringResource(AppR.string.fp_MediaDl), verticalArrangement = Arrangement.Center) {
                            ToolIntro(
                                Icons.Default.Download, stringResource(R.string.f_MediaDl_intro_title),
                                listOf(
                                    Icons.Default.Link to stringResource(R.string.f_MediaDl_intro_1),
                                    Icons.Default.Tune to stringResource(R.string.f_MediaDl_intro_2),
                                    Icons.Default.Download to stringResource(R.string.f_MediaDl_intro_3),
                                ),
                                stringResource(R.string.f_MediaDl_intro_tip)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            LinkField(link, { link = it }, enabled = !busy, onDone = ::find)
                        }
                    },
                    buttons = {
                        // The current (or last) download, or the link looked up, stays a tap away
                        when {
                            current != null -> SecondaryButton(stringResource(R.string.f_MediaDl_back_to, current.info.title), !busy) { step = Step.Download }
                            found != null -> SecondaryButton(stringResource(R.string.f_MediaDl_back_to, found.title), !busy) { step = Step.Options }
                        }
                        BigButton(Icons.Default.Search, stringResource(R.string.f_MediaDl_find), enabled = link.isNotBlank() && !busy) { find() }
                    }
                )
            }
        }
    }
}

/** Only the sound when there's no video. */
private fun effective(info: MediaInfo?, options: MediaOptions) =
    if (info == null || info.hasVideo) options else options.copy(kind = MediaOptions.Kind.Audio)

/** The link, with a paste button while it's empty (links are mostly copied from elsewhere). */
@Composable
private fun LinkField(value: String, onChange: (String) -> Unit, enabled: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    InputField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.f_MediaDl_link)) },
        placeholder = { Text("https://…") },
        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
        trailingIcon = {
            if (value.isEmpty()) {
                IconButton(onClick = { clipboardText(context)?.let { onChange(MediaDownloads.linkIn(it)) } }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.f_MediaDl_paste))
                }
            } else {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(AppR.string.clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

private fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }

/** The found media: its thumbnail, title, who put it up, how long it is and where it's from. */
@Composable
private fun MediaCard(info: MediaInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f)
        ) {
            info.thumbnail?.let { url ->
                Crossfade(targetState = rememberUrlImage(url, 512), label = "thumbnail") { image ->
                    if (image != null) {
                        Image(
                            bitmap = image, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(info.uploader, info.durationSec?.let(::duration), info.site).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The file's name, video or sound and how, and what goes into the file. Without [info] (the
 * defaults, in the settings) the usual qualities, and no file name.
 */
@Composable
fun OptionsForm(
    info: MediaInfo?,
    options: MediaOptions,
    onChange: (MediaOptions) -> Unit,
    fileName: String? = null,
    onFileNameChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val o = effective(info, options)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            if (fileName != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.f_MediaDl_file_name), style = MaterialTheme.typography.labelLarge)
                    InputField(
                        value = fileName,
                        onValueChange = onFileNameChange,
                        singleLine = true,
                        // The only field: done with it, the keyboard goes
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (info == null || info.hasVideo) {
                val kinds = listOf(
                    Triple(MediaOptions.Kind.Video, Icons.Default.VideoFile, R.string.f_MediaDl_video),
                    Triple(MediaOptions.Kind.Audio, Icons.Default.AudioFile, R.string.f_MediaDl_audio)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { i, (kind, icon, label) ->
                        SegmentedButton(
                            selected = o.kind == kind,
                            onClick = { onChange(options.copy(kind = kind)) },
                            shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        ) { Text(stringResource(label)) }
                    }
                }
            }

            // Each kind's own choices
            Reveal(visible = o.kind == MediaOptions.Kind.Video) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The best there is, then the usual heights below it (all of them for the defaults)
                    val best = info?.heights?.firstOrNull()
                    val choices = listOf(0) +
                        if (info == null) MediaOptions.HEIGHTS
                        else MediaOptions.HEIGHTS.filter { h -> best != null && h < best && h in info.heights }
                    // A saved height above what's here is the best there is
                    val selected = if (o.maxHeight == 0 || (best != null && o.maxHeight >= best)) 0 else o.maxHeight
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.f_MediaDl_quality), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { h ->
                                val label = if (h == 0) {
                                    stringResource(R.string.f_MediaDl_best) + (best?.let { " · ${it}p" } ?: "")
                                } else "${h}p"
                                val size = info?.videoSize(h.takeIf { it > 0 })?.let { "~" + Formatter.formatShortFileSize(context, it) }
                                FilterChip(
                                    selected = selected == h,
                                    onClick = { onChange(options.copy(maxHeight = h)) },
                                    label = { Text(listOfNotNull(label, size).joinToString("  ")) }
                                )
                            }
                        }
                    }
                    Choice(stringResource(R.string.f_MediaDl_container), MediaOptions.CONTAINERS, o.container) { onChange(options.copy(container = it)) }
                }
            }
            Reveal(visible = o.kind == MediaOptions.Kind.Audio) {
                Choice(stringResource(R.string.f_MediaDl_format), MediaOptions.AUDIO_FORMATS, o.audioFormat) { onChange(options.copy(audioFormat = it)) }
            }
        }

        // Settings-like rows, as wide as in the settings
        Column(verticalArrangement = Arrangement.spacedBy(ROW_SPACING)) {
            // Only where it can be kept (see MediaOptions.hasCover)
            Reveal(visible = o.hasCover) {
                SwitchButton(stringResource(R.string.f_MediaDl_thumbnail), Icons.Default.Image, o.embedThumbnail, subtitle = stringResource(R.string.f_MediaDl_thumbnail_desc)) {
                    onChange(options.copy(embedThumbnail = it))
                }
            }
            SwitchButton(stringResource(R.string.f_MediaDl_metadata), Icons.Default.Sell, o.embedMetadata, subtitle = stringResource(R.string.f_MediaDl_metadata_desc)) {
                onChange(options.copy(embedMetadata = it))
            }
            Reveal(visible = o.kind == MediaOptions.Kind.Video) {
                SwitchButton(stringResource(R.string.f_MediaDl_subtitles), Icons.Default.Subtitles, o.subtitles, subtitle = stringResource(R.string.f_MediaDl_subtitles_desc)) {
                    onChange(options.copy(subtitles = it))
                }
            }
            SwitchButton(stringResource(R.string.f_MediaDl_sponsorblock), Icons.Default.MoneyOff, o.sponsorBlock, subtitle = stringResource(R.string.f_MediaDl_sponsorblock_desc)) {
                onChange(options.copy(sponsorBlock = it))
            }
            // Where it goes (for the next ones too)
            MediaOutput.SaveToSetting()
        }
    }
}

@Composable
private fun Choice(label: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            values.forEachIndexed { i, value ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, values.size),
                    icon = {}
                ) { Text(value.uppercase(), maxLines = 1) }
            }
        }
    }
}

/** What it gets, and how it went once it has: the file saved (where, under what name), or that it failed or was canceled. */
@Composable
private fun DownloadResult(d: MediaDownloads.Download) {
    MediaCard(d.info)
    Spacer(modifier = Modifier.height(24.dp))
    // Under way, nothing: its bar (what it's at, how far, cancelling it), and what it takes at the bottom.
    // The saved file kept while it folds away
    val last = remember { arrayOfNulls<SavedFile>(1) }
    d.saved?.let { last[0] = it }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Reveal(visible = d.phase == MediaDownloads.Phase.Done && d.saved != null) {
            val saved = last[0] ?: return@Reveal
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.f_MediaDl_saved, saved.folder), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    saved.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis
                )
            }
        }
        Reveal(visible = d.phase == MediaDownloads.Phase.Failed || d.phase == MediaDownloads.Phase.Cancelled) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val failed = d.phase == MediaDownloads.Phase.Failed
                Icon(
                    if (failed) Icons.Default.ErrorOutline else Icons.Default.Close, contentDescription = null,
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(if (failed) R.string.f_MediaDl_failed else R.string.f_MediaDl_cancelled),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * At the bottom, as every tool's buttons: while it downloads, what it takes (yt-dlp, FFmpeg joining
 * it); once it's done, what to do with the file (open, share), or next (another format, another link).
 */
@Composable
private fun DownloadButtons(d: MediaDownloads.Download, onAnother: () -> Unit, onChangeFormat: () -> Unit) {
    val context = LocalContext.current
    val running = d.phase == MediaDownloads.Phase.Starting || d.phase == MediaDownloads.Phase.Downloading || d.phase == MediaDownloads.Phase.Processing
    // The saved file (kept while its buttons fold away)
    val last = remember { arrayOfNulls<SavedFile>(1) }
    d.saved?.let { last[0] = it }
    Reveal(visible = running) {
        // Its network: yt-dlp's own speed (the app's traffic would count anything else running too)
        ResourceTiles(MediaDownloaderFeature, scope = ResourceScope.Task(d.taskId), downloadRate = {
            d.speed?.takeIf { d.phase == MediaDownloads.Phase.Downloading }?.toLong() ?: 0L
        })
    }
    Reveal(visible = d.phase == MediaDownloads.Phase.Done && d.saved != null) {
        val saved = last[0] ?: return@Reveal
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(Icons.AutoMirrored.Default.OpenInNew, stringResource(R.string.f_MediaDl_open)) { openFile(context, saved.uri, saved.mimeType) }
                ActionButton(Icons.Default.Share, stringResource(R.string.f_MediaDl_share)) { shareFile(context, saved.uri, saved.mimeType, "share.Media.Download") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(Icons.Default.Tune, stringResource(R.string.f_MediaDl_change_format), onClick = onChangeFormat)
                ActionButton(Icons.Default.Search, stringResource(R.string.f_MediaDl_another), onClick = onAnother)
            }
        }
    }
    // Again, as it was or another way
    Reveal(visible = d.phase == MediaDownloads.Phase.Failed || d.phase == MediaDownloads.Phase.Cancelled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(Icons.Default.Tune, stringResource(R.string.f_MediaDl_change_format), onClick = onChangeFormat)
            ActionButton(Icons.Default.Search, stringResource(R.string.f_MediaDl_another), onClick = onAnother)
        }
    }
}

@Composable
private fun status(d: MediaDownloads.Download): String = when (d.phase) {
    MediaDownloads.Phase.Starting -> stringResource(R.string.f_MediaDl_starting)
    MediaDownloads.Phase.Downloading ->
        if (d.streams > 1) stringResource(R.string.f_MediaDl_downloading_part, d.stream.coerceAtLeast(1), d.streams)
        else stringResource(R.string.f_MediaDl_downloading)
    else -> stringResource(
        when (d.step) {
            "Merger" -> R.string.f_MediaDl_step_merge
            "ExtractAudio" -> R.string.f_MediaDl_step_audio
            "EmbedThumbnail", "ThumbnailsConvertor" -> R.string.f_MediaDl_step_thumbnail
            "Metadata" -> R.string.f_MediaDl_step_metadata
            "SponsorBlock", "ModifyChapters" -> R.string.f_MediaDl_step_sponsorblock
            "EmbedSubtitle", "SubtitlesConvertor" -> R.string.f_MediaDl_step_subtitles
            else -> R.string.f_MediaDl_step_processing
        }
    )
}

/** The download's line under the step bar: what it's at (how far and how long it has left go after it), or that it failed. */
@Composable
private fun downloadCaption(d: MediaDownloads.Download): String =
    if (d.phase == MediaDownloads.Phase.Failed) stringResource(R.string.f_MediaDl_failed) else status(d)

/** e.g. 3:07, 1:02:45 */
private fun duration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
