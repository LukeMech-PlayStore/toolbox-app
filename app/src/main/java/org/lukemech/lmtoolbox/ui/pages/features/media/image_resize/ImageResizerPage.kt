package org.lukemech.lmtoolbox.ui.pages.features.media.image_resize

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.media.file_size.FileSizeRequest
import org.lukemech.lmtoolbox.features.media.image_resize.Fit
import org.lukemech.lmtoolbox.features.media.image_resize.ImageFormat
import org.lukemech.lmtoolbox.features.media.image_resize.ImageResize
import org.lukemech.lmtoolbox.features.media.image_resize.ImageResizerFeature
import org.lukemech.lmtoolbox.features.media.image_resize.ResizeOptions
import org.lukemech.lmtoolbox.ui.components.ActionButton
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.Chip
import org.lukemech.lmtoolbox.ui.components.ChipChoice
import org.lukemech.lmtoolbox.ui.components.ChipGroup
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.ErrorText
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.helpers.shareFile
import org.lukemech.lmtoolbox.helpers.openFile
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.tasks.ShownWhile

private enum class Step { File, Options, Done }

/**
 * Image resizer in three steps, like the media encoder: the picture, how to resize it (to a part of
 * it or into a box, as what, and at what quality or file size), and the saved one.
 */
@Composable
fun ImageResizerPage(providedInput: String? = "") {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var step by rememberSaveable { mutableStateOf(if (ImageResize.saved != null) Step.Done else Step.File) }
    // What's open, and what's picked for it, is ImageResize's: it stays when the page is left, a picture being made
    // goes on meanwhile (a task)
    var opened by ImageResize::opened
    var options by remember { mutableStateOf(ImageResize.options ?: ResizeOptions.load(context)) }
    SideEffect { ImageResize.options = options }
    var fileName by ImageResize::fileName
    var saved by ImageResize::saved
    var opening by remember { mutableStateOf(false) }
    val busy = opening || ImageResize.run.running
    ShownWhile(ImageResize.run)

    fun open(uri: Uri) {
        coroutineScope.launch {
            opening = true
            val result = ImageResize.open(context, uri)
            opening = false
            result.onSuccess {
                opened = it
                fileName = resources.getString(R.string.f_ImgRes_default_name, it.baseName)
                saved = null
                step = Step.Options
            }.onFailure {
                // In the app, not a command: told in the one place errors are, the bottom bar
                AppTasks.reportFailure(ImageResizerFeature, "", "Couldn't open the picture", it.stackTraceToString())
            }
        }
    }

    fun resize() {
        focusManager.clearFocus()
        val image = opened ?: return
        ResizeOptions.save(context, options)
        val app = context.applicationContext
        val picked = options
        val name = fileName
        ImageResize.run.start(app, name) {
            val result = ImageResize.resize(app, image, picked, name)
            result.onSuccess {
                saved = it
                step = Step.Done
            }.onFailure { e ->
                val message = if (e is OutOfMemoryError) ImageResize.TOO_BIG
                else e.message ?: "Couldn't make the picture"
                AppTasks.reportFailure(ImageResizerFeature, name, message, e.stackTraceToString())
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::open) }

    // Shared to the resizer: straight to the picture
    LaunchedEffect(providedInput) {
        if (!providedInput.isNullOrBlank()) open(providedInput.toUri())
    }
    // Opened from the file size shortcut: its size as it is, at most the size aimed at
    LaunchedEffect(Unit) {
        if (FileSizeRequest.take(ImageResizerFeature)) {
            options = options.copy(percent = 100, width = 0, height = 0, targetMb = options.targetMb.takeIf { it > 0 } ?: ResizeOptions.DEFAULT_TARGET_MB)
        }
    }

    BackHandler(enabled = step != Step.File && !busy) {
        step = if (step == Step.Done && opened != null) Step.Options else Step.File
    }

    val steps = Steps(Step.entries.size, step.ordinal, busy)
    val title = stringResource(R.string.fp_ImgRes)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val image = opened
            val done = saved
            when (step) {
                Step.Done if done != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = if (image != null) Step.Options else Step.File }) {
                            DoneStep(done, image)
                        }
                    },
                    buttons = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionButton(Icons.AutoMirrored.Default.OpenInNew, stringResource(R.string.f_ImgRes_open)) { openFile(context, done.uri, done.mimeType) }
                            ActionButton(Icons.Default.Share, stringResource(R.string.f_ImgRes_share)) { shareFile(context, done.uri, done.mimeType, "share.Image.Resize") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionButton(Icons.Default.Tune, stringResource(R.string.f_ImgRes_change), enabled = image != null) { step = Step.Options }
                            ActionButton(Icons.Default.FolderOpen, stringResource(R.string.f_ImgRes_another)) {
                                saved = null
                                picker.launch(arrayOf("image/*"))
                            }
                        }
                    }
                )

                Step.Options if image != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = Step.File }) {
                            ImageCard(image.preview, image.name, details(context, image.width, image.height, image.size))
                            Spacer(modifier = Modifier.height(16.dp))
                            OptionsForm(image, options, { options = it }, fileName, { fileName = it })
                        }
                    },
                    buttons = {
                        BigButton(Icons.Default.PhotoSizeSelectLarge, stringResource(R.string.f_ImgRes_resize), enabled = !busy && fileName.isNotBlank()) { resize() }
                    }
                )

                else -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, verticalArrangement = Arrangement.Center) {
                            ToolIntro(
                                Icons.Default.PhotoSizeSelectLarge, stringResource(R.string.f_ImgRes_intro_title),
                                listOf(
                                    Icons.Default.FolderOpen to stringResource(R.string.f_ImgRes_intro_1),
                                    Icons.Default.AspectRatio to stringResource(R.string.f_ImgRes_intro_2),
                                    Icons.Default.Straighten to stringResource(R.string.f_ImgRes_intro_3),
                                ),
                                stringResource(R.string.f_ImgRes_intro_tip)
                            )
                        }
                    },
                    buttons = {
                        when {
                            done != null -> SecondaryButton(stringResource(R.string.f_ImgRes_back_to, done.name), !busy) { step = Step.Done }
                            image != null -> SecondaryButton(stringResource(R.string.f_ImgRes_back_to, image.name), !busy) { step = Step.Options }
                        }
                        BigButton(Icons.Default.FolderOpen, stringResource(R.string.f_ImgRes_pick), enabled = !busy) {
                            picker.launch(arrayOf("image/*"))
                        }
                    }
                )
            }
        }
    }
}

/** A picture's small copy, its name and what it is. */
@Composable
private fun ImageCard(preview: Bitmap, name: String, details: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val bitmap = remember(preview) { preview.asImageBitmap() }
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** e.g. 4000×3000 · 5.2 MB */
private fun details(context: Context, width: Int, height: Int, bytes: Long) = listOfNotNull(
    "$width×$height",
    bytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
).joinToString(" · ")

/**
 * The new picture's name, file size to aim at, size (a part of it, or a box and how it goes in, and what
 * that comes to), format and quality, its metadata and where it goes ([folder]: off in the settings, which have it apart).
 * Without [image] (the defaults, in the settings) every choice, and no file name.
 */
@Composable
fun OptionsForm(
    image: ImageResize.Opened?,
    options: ResizeOptions,
    onChange: (ResizeOptions) -> Unit,
    fileName: String? = null,
    onFileNameChange: (String) -> Unit = {},
    folder: Boolean = true,
) {
    val format = options.format.resolve(image?.mimeType)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            if (fileName != null) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.f_ImgRes_file_name), style = MaterialTheme.typography.labelLarge)
                InputField(
                    value = fileName,
                    onValueChange = onFileNameChange,
                    singleLine = true,
                    suffix = { Text(".${format.ext}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right under it, as in the video encoder: the size to aim at (it rules out choosing a quality)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TargetSizeChoice(image?.size ?: 0, options.targetMb) { onChange(options.copy(targetMb = it)) }
                Reveal(visible = options.targetMb > 0) {
                    Note(stringResource(if (format.isLossy) R.string.f_ImgRes_target_note else R.string.f_ImgRes_target_note_png), inset = false)
                }
            }

            SizeChoice(image, options, onChange)
            Reveal(visible = options.hasBox) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ChipChoice(
                        stringResource(R.string.f_ImgRes_fit),
                        listOf(
                            Fit.Fit to stringResource(R.string.f_ImgRes_fit_fit),
                            Fit.Fill to stringResource(R.string.f_ImgRes_fit_fill),
                            Fit.Pad to stringResource(R.string.f_ImgRes_fit_pad),
                            Fit.Stretch to stringResource(R.string.f_ImgRes_fit_stretch),
                        ),
                        options.fit,
                        note = stringResource(
                            when (options.fit) {
                                Fit.Fit -> R.string.f_ImgRes_fit_fit_desc
                                Fit.Fill -> R.string.f_ImgRes_fit_fill_desc
                                Fit.Pad -> R.string.f_ImgRes_fit_pad_desc
                                Fit.Stretch -> R.string.f_ImgRes_fit_stretch_desc
                            }
                        )
                    ) { onChange(options.copy(fit = it)) }
                }
            }
            // What the size (and how it goes in the box) comes to, under them
            if (image != null) {
                val plan = ImageResize.plan(image.width, image.height, options)
                Note(stringResource(R.string.f_ImgRes_result, "${plan.width}×${plan.height}"), inset = false)
            }

            ChipChoice(
                stringResource(R.string.f_ImgRes_format),
                ImageFormat.entries.map {
                    it to if (it == ImageFormat.Original) {
                        stringResource(R.string.f_ImgRes_original) + (image?.let { i -> " · " + it.resolve(i.mimeType).ext.uppercase() } ?: "")
                    } else it.ext.uppercase()
                },
                options.format
            ) { onChange(options.copy(format = it)) }

            // A quality, unless there's a size to aim at (at the top)
            Reveal(visible = options.targetMb <= 0 && format.isLossy) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.f_ImgRes_quality, options.quality), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = options.quality.toFloat(),
                        onValueChange = { onChange(options.copy(quality = it.toInt())) },
                        valueRange = ResizeOptions.MIN_QUALITY.toFloat()..100f
                    )
                }
            }
        }

        // The buttons apart as in the settings
        Column(verticalArrangement = RowArrangement) {
            Reveal(visible = options.hasBox) {
                Column(verticalArrangement = RowArrangement) {
                    // A box's way, and whether a smaller picture grows into it
                    SwitchButton(
                        stringResource(R.string.f_ImgRes_orientation), Icons.Default.ScreenRotation, options.matchOrientation,
                        subtitle = stringResource(R.string.f_ImgRes_orientation_desc)
                    ) { onChange(options.copy(matchOrientation = it)) }
                    Reveal(visible = options.fit == Fit.Fit || options.fit == Fit.Pad) {
                        SwitchButton(
                            stringResource(R.string.f_ImgRes_enlarge), Icons.Default.OpenInFull, options.enlarge,
                            subtitle = stringResource(R.string.f_ImgRes_enlarge_desc)
                        ) { onChange(options.copy(enlarge = it)) }
                    }
                }
            }
            SwitchButton(
                stringResource(R.string.f_ImgRes_metadata), Icons.Default.Info, options.keepMetadata,
                subtitle = stringResource(R.string.f_ImgRes_metadata_desc)
            ) { onChange(options.copy(keepMetadata = it)) }
            if (folder) FolderSetting()
        }
    }
}

/** Where pictures go: a folder picked for them, else Pictures. */
@Composable
fun FolderSetting() {
    ImageResize.outputFolder().Setting(
        stringResource(R.string.f_ImgRes_save_to),
        stringResource(R.string.f_ImgRes_pictures),
        stringResource(R.string.f_ImgRes_save_to_reset)
    )
}

/** One of a few sizes: a part of it ([ResizeOptions.PERCENTS]), a box ([ResizeOptions.BOXES]), or one typed in. */
@Composable
private fun SizeChoice(image: ImageResize.Opened?, options: ResizeOptions, onChange: (ResizeOptions) -> Unit) {
    val preset = options.width to options.height
    var custom by remember { mutableStateOf(options.hasBox && preset !in ResizeOptions.BOXES) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipGroup(stringResource(R.string.f_ImgRes_size)) {
            ResizeOptions.PERCENTS.forEach { p ->
                Chip(
                    if (p == 100) stringResource(R.string.f_ImgRes_original) else "$p%",
                    selected = !custom && !options.hasBox && options.percent == p
                ) { custom = false; onChange(options.copy(percent = p, width = 0, height = 0)) }
            }
            ResizeOptions.BOXES.forEach { (w, h) ->
                Chip("$w×$h", selected = !custom && preset == (w to h)) { custom = false; onChange(options.copy(width = w, height = h)) }
            }
            Chip(stringResource(R.string.f_ImgRes_custom), selected = custom) {
                custom = true
                // From what it is: a side to change
                if (!options.hasBox) onChange(options.copy(width = image?.width ?: ResizeOptions.BOXES[1].first, height = 0))
            }
        }
        Reveal(visible = custom) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    @Composable
                    fun Side(value: Int, label: String, onValue: (Int) -> Unit, modifier: Modifier) = InputField(
                        value = if (value > 0) value.toString() else "",
                        onValueChange = { typed -> onValue(typed.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) },
                        singleLine = true,
                        label = { Text(label) },
                        placeholder = { Text(stringResource(R.string.f_ImgRes_auto)) },
                        suffix = { Text("px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = modifier
                    )
                    Side(options.width, stringResource(R.string.f_ImgRes_width), { onChange(options.copy(width = it)) }, Modifier.weight(1f))
                    Text("×")
                    Side(options.height, stringResource(R.string.f_ImgRes_height), { onChange(options.copy(height = it)) }, Modifier.weight(1f))
                }
                Note(stringResource(R.string.f_ImgRes_custom_note), inset = false)
            }
        }
    }
}

/** The file's size to aim at, in MB: none (a quality instead), one of [ResizeOptions.TARGET_SIZES] under the picture's own, or another one. */
@Composable
private fun TargetSizeChoice(size: Long, selected: Double, onSelect: (Double) -> Unit) {
    val presets = ResizeOptions.TARGET_SIZES.filter { size <= 0 || it * 1_000_000 < size }
    var other by remember { mutableStateOf(selected > 0 && selected !in presets) }
    var text by remember { mutableStateOf(if (selected > 0 && selected !in presets) megabytes(selected) else "") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipGroup(stringResource(R.string.f_ImgRes_target)) {
            Chip(stringResource(R.string.f_ImgRes_target_none), selected = selected <= 0 && !other) { other = false; onSelect(0.0) }
            presets.forEach { mb ->
                Chip("${megabytes(mb)} MB", selected = !other && selected == mb) { other = false; onSelect(mb) }
            }
            Chip(stringResource(R.string.f_ImgRes_custom), selected = other) {
                other = true
                text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onSelect)
            }
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
    }
}

/** e.g. 1, 0.5 */
private fun megabytes(mb: Double) = if (mb % 1.0 == 0.0) "%.0f".format(mb) else "%.1f".format(mb)

/** The saved picture: where it went, what it is now and what it was. */
@Composable
private fun DoneStep(saved: ImageResize.Saved, image: ImageResize.Opened?) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ImageCard(saved.preview, saved.name, details(context, saved.width, saved.height, saved.bytes))
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.f_ImgRes_saved, saved.folder), style = MaterialTheme.typography.titleMedium)
        }
        if (image != null) {
            Text(
                stringResource(R.string.f_ImgRes_was, details(context, image.width, image.height, image.size)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (saved.missedTarget) ErrorText(stringResource(R.string.f_ImgRes_missed_target))
    }
}
