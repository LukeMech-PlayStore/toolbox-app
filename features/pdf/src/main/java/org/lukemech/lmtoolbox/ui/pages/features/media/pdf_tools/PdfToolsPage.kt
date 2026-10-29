package org.lukemech.lmtoolbox.ui.pages.features.media.pdf_tools

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R as AppR
import org.lukemech.lmtoolbox.features.media.pdf_tools.PageRanges
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfAction
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfFile
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfJobs
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfOptions
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfToolsFeature
import org.lukemech.lmtoolbox.features.media.pdf_tools.Preset
import org.lukemech.lmtoolbox.features.media.pdf_tools.SplitMode
import org.lukemech.lmtoolbox.features.pdf.R
import org.lukemech.lmtoolbox.helpers.ResourceScope
import org.lukemech.lmtoolbox.helpers.SavedFile
import org.lukemech.lmtoolbox.helpers.openFile
import org.lukemech.lmtoolbox.helpers.shareFiles
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.ui.components.ActionButton
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.ChipChoice
import org.lukemech.lmtoolbox.ui.components.ChipGroup
import org.lukemech.lmtoolbox.ui.components.ClearAction
import org.lukemech.lmtoolbox.ui.components.ErrorText
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.LIST_MAX_HEIGHT
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.ui.components.edgeFade

private enum class Step { Files, Action, Result }

/** Its own "Share to" entry (the manifest's alias): what's made isn't shared back to it. */
private const val SHARE_ALIAS = "share.Pdf.Tools"

/**
 * PDF tools in three steps, like the video encoder: the files (copied in, qpdf reads them; put in order,
 * protected ones opened with their passwords), what to do with them (merged, split, compressed, turned, a
 * password added or removed; several are joined first for any but merging), and the work, which goes on in
 * the background when the page is left (see [PdfJobs]). [providedInput]: PDFs shared in, a URI a line.
 */
@Composable
fun PdfToolsPage(providedInput: String? = "") {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val job by PdfJobs.state.collectAsState()

    // Back on the page while something's being made: straight to it; else the files (or the intro)
    var step by rememberSaveable {
        mutableStateOf(if (PdfJobs.state.value?.isActive == true) Step.Result else Step.Files)
    }
    // What's open, and what's picked for it, is PdfJobs': it stays when the page is left or redrawn, what's being
    // made goes on meanwhile (a task). A name not typed follows the action
    var files by PdfJobs::files
    var typedName by PdfJobs::fileName
    var options by PdfJobs::options
    var busy by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf<PdfFile?>(null) }

    val encrypted = files.any { it.encrypted }
    val o = options.effective(files.size, encrypted)
    val pageCount = files.sumOf { it.pages ?: 0 }
    val suggestedName = files.firstOrNull()?.let { "${it.baseName}-${stringResource(suffixOf(o))}" }.orEmpty()
    val fileName = typedName ?: suggestedName

    /** Whether the open files may change: not while a job reads their copies (its card a tap away). */
    fun canChange(): Boolean {
        if (!PdfJobs.isBusy()) return true
        // Not an error: something's being made
        Toast.makeText(context, R.string.f_Pdf_busy_open, Toast.LENGTH_SHORT).show()
        return false
    }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty() || !canChange()) return
        coroutineScope.launch {
            busy = true
            val opened = PdfJobs.open(context, uris)
            busy = false
            // None read: the bottom bar tells (see PdfJobs.open)
            if (opened.isEmpty()) return@launch
            if (files.isEmpty()) typedName = null
            files = files + opened
            PdfJobs.clear()
            step = Step.Files
            // The first protected one asks for its password right away
            opened.firstOrNull { it.needsPassword }?.let { unlocking = it }
        }
    }

    fun remove(file: PdfFile) {
        if (!canChange()) return
        files = files - file
        PdfJobs.close(listOf(file))
    }

    fun move(index: Int, by: Int) {
        files = files.toMutableList().apply { add(index + by, removeAt(index)) }
    }

    fun start() {
        focusManager.clearFocus()
        if (files.isEmpty()) return
        PdfJobs.start(context, files, o, fileName)
        step = Step.Result
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> add(uris) }

    // Shared to the tools: straight to the files
    LaunchedEffect(providedInput) {
        if (!providedInput.isNullOrBlank()) add(providedInput.lines().filter { it.isNotBlank() }.map { it.trim().toUri() })
    }

    // Left while working: it goes on, shown in the bottom bar
    DisposableEffect(Unit) {
        onDispose { PdfJobs.state.value?.takeIf { it.isActive }?.taskId?.let(AppTasks::detach) }
    }

    BackHandler(enabled = step != Step.Files && !busy) {
        step = if (step == Step.Result && files.isNotEmpty()) Step.Action else Step.Files
    }

    // The work's progress is the last step's bar
    val active = job?.takeIf { step == Step.Result && it.isActive }
    // Its line under the bars: while it runs, and once it failed (its log a tap away)
    val shown = job?.takeIf { step == Step.Result && (it.isActive || it.phase == PdfJobs.Phase.Failed) }
    val steps = Steps(
        Step.entries.size, step.ordinal, busy,
        working = active != null,
        progress = active?.fraction,
        caption = shown?.let { stringResource(captionOf(it)) },
        onCancel = active?.takeIf { it.taskId != null && it.phase != PdfJobs.Phase.Saving }?.let { { PdfJobs.cancel() } }
    )
    val title = stringResource(AppR.string.fp_PdfTools)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val current = job
            when {
                // What was made and how it went up top; what it takes, or what to do next, at the bottom
                step == Step.Result && current != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = if (files.isNotEmpty()) Step.Action else Step.Files }) {
                            Summary(current.files)
                            Spacer(modifier = Modifier.height(24.dp))
                            JobResult(current)
                        }
                    },
                    buttons = {
                        JobButtons(
                            current,
                            onAnother = {
                                PdfJobs.clear()
                                if (canChange()) {
                                    PdfJobs.close(files)
                                    files = emptyList()
                                }
                                step = Step.Files
                            },
                            // The same files again, another way
                            onChange = {
                                PdfJobs.clear()
                                if (files.isEmpty()) files = current.files
                                options = current.options
                                step = Step.Action
                            }
                        )
                    }
                )

                step == Step.Action && files.isNotEmpty() -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = Step.Files }) {
                            Summary(files)
                            Spacer(modifier = Modifier.height(16.dp))
                            ActionForm(files.size, encrypted, pageCount, options, { options = it }, fileName) { typedName = it }
                        }
                    },
                    buttons = {
                        ErrorText(stringResource(R.string.f_Pdf_busy).takeIf { current?.isActive == true })
                        BigButton(
                            iconOf(o.action), stringResource(labelOf(o.action)),
                            enabled = current?.isActive != true && fileName.isNotBlank() && o.isReady(pageCount)
                        ) { start() }
                    }
                )

                files.isNotEmpty() -> StepWithButtons(
                    content = {
                        PageHeading(
                            steps = steps, title = title,
                            action = {
                                ClearAction {
                                    if (canChange()) {
                                        PdfJobs.close(files)
                                        files = emptyList()
                                    }
                                }
                            }
                        ) {
                            FileList(files, enabled = !busy, onUnlock = { unlocking = it }, onMove = ::move, onRemove = ::remove)
                            TextButton(
                                onClick = { picker.launch(arrayOf(PdfJobs.MIME_TYPE)) },
                                enabled = !busy,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.f_Pdf_add))
                            }
                            Note(stringResource(R.string.f_Pdf_order_note), visible = files.size > 1)
                            Note(stringResource(R.string.f_Pdf_password_note), visible = files.any { it.password != null })
                        }
                    },
                    buttons = {
                        ErrorText(stringResource(R.string.f_Pdf_needs_passwords).takeIf { files.any { it.needsPassword } })
                        BigButton(
                            Icons.AutoMirrored.Default.ArrowForward, stringResource(R.string.f_Pdf_next),
                            enabled = !busy && files.none { it.needsPassword }
                        ) { step = Step.Action }
                    }
                )

                else -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, verticalArrangement = Arrangement.Center) {
                            ToolIntro(
                                Icons.Default.PictureAsPdf, stringResource(R.string.f_Pdf_intro_title),
                                listOf(
                                    Icons.Default.FolderOpen to stringResource(R.string.f_Pdf_intro_1),
                                    Icons.Default.SwapVert to stringResource(R.string.f_Pdf_intro_2),
                                    Icons.Default.Tune to stringResource(R.string.f_Pdf_intro_3),
                                ),
                                stringResource(R.string.f_Pdf_intro_tip)
                            )
                        }
                    },
                    buttons = {
                        // The current (or last) work stays a tap away
                        if (current != null) {
                            SecondaryButton(stringResource(R.string.f_Pdf_back_to, current.fileName), !busy) { step = Step.Result }
                        }
                        BigButton(Icons.Default.FolderOpen, stringResource(R.string.f_Pdf_pick), enabled = !busy) {
                            picker.launch(arrayOf(PdfJobs.MIME_TYPE))
                        }
                    }
                )
            }
        }
    }

    unlocking?.let { file ->
        PasswordDialog(
            file,
            onDismiss = { unlocking = null },
            onUnlocked = { opened ->
                files = files.map { if (it.id == opened.id) opened else it }
                // The next one still locked, if there's one
                unlocking = files.firstOrNull { it.needsPassword && it.id != opened.id }
            }
        )
    }
}

/** The open files: how many, their pages and size, in a line under the title. */
@Composable
private fun Summary(files: List<PdfFile>) {
    val context = LocalContext.current
    val pages = files.sumOf { it.pages ?: 0 }
    val details = listOf(
        pluralStringResource(R.plurals.f_Pdf_files, files.size, files.size),
        pluralStringResource(R.plurals.f_Pdf_pages, pages, pages),
        Formatter.formatShortFileSize(context, files.sumOf { it.size })
    ).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                files.singleOrNull()?.name ?: files.joinToString(", ") { it.name },
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis
            )
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The files in their order (merged so), each moved up or down or taken off; a protected one says so, a tap
 * asking for its password. Rows come, go and move places as they change (a list of its own past ~7 of them).
 */
@Composable
private fun FileList(
    files: List<PdfFile>,
    enabled: Boolean,
    onUnlock: (PdfFile) -> Unit,
    onMove: (index: Int, by: Int) -> Unit,
    onRemove: (PdfFile) -> Unit,
) {
    val context = LocalContext.current
    val scroll = rememberLazyListState()
    LazyColumn(state = scroll, modifier = Modifier.fillMaxWidth().heightIn(max = LIST_MAX_HEIGHT).edgeFade(scroll)) {
        itemsIndexed(files, key = { _, file -> file.id }) { index, file ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .clickable(enabled = enabled && file.needsPassword) { onUnlock(file) }
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Icon(
                    if (file.needsPassword) Icons.Default.Lock else Icons.Default.PictureAsPdf, contentDescription = null,
                    tint = if (file.needsPassword) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                    if (file.needsPassword) {
                        Text(stringResource(R.string.f_Pdf_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        val pages = file.pages ?: 0
                        Text(
                            "${pluralStringResource(R.plurals.f_Pdf_pages, pages, pages)} · ${Formatter.formatShortFileSize(context, file.size)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onMove(index, -1) }, enabled = enabled && index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.f_Pdf_move_up))
                }
                IconButton(onClick = { onMove(index, 1) }, enabled = enabled && index < files.lastIndex) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.f_Pdf_move_down))
                }
                IconButton(onClick = { onRemove(file) }, enabled = enabled) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.f_Pdf_remove))
                }
            }
        }
    }
}

/**
 * What to do with the files ([count] of them, [pageCount] pages together; one [encrypted] can be unlocked), each
 * action's own choices under it, then the new file's name and where it goes (as in the settings).
 */
@Composable
private fun ActionForm(
    count: Int,
    encrypted: Boolean,
    pageCount: Int,
    options: PdfOptions,
    onChange: (PdfOptions) -> Unit,
    fileName: String,
    onFileNameChange: (String) -> Unit,
) {
    // Each action's own choices are kept in [options] while another's picked: folding away, they show as they were
    val o = options.effective(count, encrypted)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = RowArrangement, modifier = Modifier.padding(horizontal = 16.dp)) {
            ChipGroup(stringResource(R.string.f_Pdf_action)) {
                PdfOptions.actions(count, encrypted).forEach { action ->
                    FilterChip(
                        selected = action == o.action,
                        onClick = { onChange(options.copy(action = action)) },
                        label = { Text(stringResource(labelOf(action))) },
                        leadingIcon = { Icon(iconOf(action), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            RevealSection(o.action == PdfAction.Merge) { Note(stringResource(R.string.f_Pdf_merge_note), inset = false) }
            RevealSection(o.action == PdfAction.Split) {
                ChipChoice(
                    null,
                    listOf(
                        SplitMode.Pages to stringResource(R.string.f_Pdf_split_pages),
                        SplitMode.Ranges to stringResource(R.string.f_Pdf_split_ranges),
                        SplitMode.Extract to stringResource(R.string.f_Pdf_split_extract),
                    ),
                    o.splitMode,
                    note = when (o.splitMode) {
                        SplitMode.Pages -> pluralStringResource(R.plurals.f_Pdf_split_pages_note, pageCount, pageCount)
                        SplitMode.Ranges -> stringResource(R.string.f_Pdf_split_ranges_note)
                        SplitMode.Extract -> stringResource(R.string.f_Pdf_split_extract_note)
                    }
                ) { onChange(options.copy(splitMode = it)) }
                RevealSection(o.splitMode != SplitMode.Pages) {
                    PagesField(o.ranges, pageCount, placeholder = "1-3,5,7-z") { onChange(options.copy(ranges = it)) }
                }
            }
            RevealSection(o.action == PdfAction.Compress) {
                ChipChoice(
                    stringResource(R.string.f_Pdf_preset),
                    Preset.entries.map { it to stringResource(presetLabel(it)) },
                    o.preset,
                    note = stringResource(presetNote(o.preset))
                ) { onChange(options.copy(preset = it)) }
            }
            RevealSection(o.action == PdfAction.Rotate) {
                ChipChoice(
                    null,
                    listOf(
                        90 to stringResource(R.string.f_Pdf_angle_90),
                        180 to stringResource(R.string.f_Pdf_angle_180),
                        270 to stringResource(R.string.f_Pdf_angle_270),
                    ),
                    o.angle
                ) { onChange(options.copy(angle = it)) }
                PagesField(o.rotatePages, pageCount, placeholder = stringResource(R.string.f_Pdf_pages_all)) {
                    onChange(options.copy(rotatePages = it))
                }
            }
            RevealSection(o.action == PdfAction.Protect) {
                PasswordField(o.password, stringResource(R.string.f_Pdf_password)) { onChange(options.copy(password = it)) }
                Note(stringResource(R.string.f_Pdf_protect_note), inset = false)
            }
            RevealSection(o.action == PdfAction.Unlock) { Note(stringResource(R.string.f_Pdf_unlock_note), inset = false) }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.f_Pdf_file_name), style = MaterialTheme.typography.labelLarge)
                InputField(
                    value = fileName,
                    onValueChange = onFileNameChange,
                    singleLine = true,
                    suffix = { Text(".pdf") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                // A split's parts: the name and their pages
                Note(stringResource(R.string.f_Pdf_parts_note), visible = o.action == PdfAction.Split && o.splitMode != SplitMode.Extract, inset = false)
            }
        }
        // Where it goes (for the next ones too), as in the settings
        FolderSetting()
    }
}

/**
 * Pages typed (see [PageRanges]): only what a selection has goes in (no other letters), and what isn't pages of
 * the [pageCount] there are is said under it.
 */
@Composable
private fun PagesField(value: String, pageCount: Int, placeholder: String, onChange: (String) -> Unit) {
    val wrong = value.isNotBlank() && PageRanges.parse(value, pageCount) == null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InputField(
            value = value,
            onValueChange = { onChange(PageRanges.filter(it).take(200)) },
            label = { Text(stringResource(R.string.f_Pdf_pages)) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            isError = wrong,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        ErrorText(stringResource(R.string.f_Pdf_pages_error, pageCount).takeIf { wrong })
    }
}

/** A password typed, hidden unless shown with its eye. */
@Composable
private fun PasswordField(value: String, label: String, isError: Boolean = false, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    InputField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(if (visible) R.string.f_Pdf_hide_password else R.string.f_Pdf_show_password)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** A protected file's password asked; checked with qpdf, it's opened with it, else it says it's wrong. */
@Composable
private fun PasswordDialog(file: PdfFile, onDismiss: () -> Unit, onUnlocked: (PdfFile) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var password by remember(file.id) { mutableStateOf("") }
    var wrong by remember(file.id) { mutableStateOf(false) }
    var checking by remember(file.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.f_Pdf_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                PasswordField(password, stringResource(R.string.f_Pdf_password), isError = wrong) {
                    password = it
                    wrong = false
                }
                ErrorText(stringResource(R.string.f_Pdf_wrong_password).takeIf { wrong })
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty() && !checking,
                onClick = {
                    coroutineScope.launch {
                        checking = true
                        val opened = PdfJobs.unlock(context, file, password)
                        checking = false
                        if (opened != null) onUnlocked(opened) else wrong = true
                    }
                }
            ) { Text(stringResource(R.string.f_Pdf_unlock_file)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

/** Where new files go: a folder picked, else Downloads. */
@Composable
fun FolderSetting() {
    PdfJobs.output.Setting(
        stringResource(R.string.f_Pdf_save_to),
        stringResource(R.string.f_Pdf_downloads),
        stringResource(R.string.f_Pdf_save_to_reset)
    )
}

/** How the work went, once it has: what was saved (where, under what name; a compression's sizes), or that it failed or was canceled. */
@Composable
private fun JobResult(j: PdfJobs.Job) {
    val context = LocalContext.current
    // Under way, nothing: its bar (what it's at, how far, cancelling it), and what it takes at the bottom
    val failedOrCancelled = j.phase == PdfJobs.Phase.Failed || j.phase == PdfJobs.Phase.Cancelled
    // What was saved (kept while it folds away)
    val last = remember { arrayOfNulls<PdfJobs.Job>(1) }
    if (j.phase == PdfJobs.Phase.Done) last[0] = j
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Reveal(visible = j.phase == PdfJobs.Phase.Done && j.saved.isNotEmpty()) {
            val done = last[0] ?: return@Reveal
            val saved = done.saved
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (saved.size == 1) stringResource(R.string.f_Pdf_saved, saved[0].folder)
                    else pluralStringResource(R.plurals.f_Pdf_saved_many, saved.size, saved.size, saved[0].folder),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (saved.size == 1) saved[0].name else "${saved.first().name} … ${saved.last().name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis
                )
                // A compression: the size before and after
                if (done.options.action == PdfAction.Compress && done.sizeBefore > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(
                            R.string.f_Pdf_size_change,
                            Formatter.formatShortFileSize(context, done.sizeBefore), Formatter.formatShortFileSize(context, done.sizeAfter)
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val percent = ((done.sizeBefore - done.sizeAfter) * 100 / done.sizeBefore).toInt()
                    if (percent > 0) {
                        Text(stringResource(R.string.f_Pdf_smaller, percent), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        ErrorText(stringResource(R.string.f_Pdf_not_smaller))
                    }
                }
            }
        }
        Reveal(visible = failedOrCancelled) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val failed = j.phase == PdfJobs.Phase.Failed
                Icon(
                    if (failed) Icons.Default.ErrorOutline else Icons.Default.Close, contentDescription = null,
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(if (failed) R.string.f_Pdf_failed else R.string.f_Pdf_cancelled),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * At the bottom, as every tool's buttons: while it works, what it takes; once it's done, what to do with what
 * was made (open one, share them), or next (the same files another way, other files). Each folds away as the
 * next unfolds.
 */
@Composable
private fun JobButtons(j: PdfJobs.Job, onAnother: () -> Unit, onChange: () -> Unit) {
    val context = LocalContext.current
    // What was saved (kept while its buttons fold away)
    val last = remember { arrayOfNulls<List<SavedFile>>(1) }
    if (j.saved.isNotEmpty()) last[0] = j.saved
    Reveal(visible = j.isActive) { ResourceTiles(PdfToolsFeature, scope = ResourceScope.Task(j.taskId)) }
    Reveal(visible = j.phase == PdfJobs.Phase.Done && j.saved.isNotEmpty()) {
        val saved = last[0] ?: return@Reveal
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                // Several (a split's parts): shared together, opened from where they are
                if (saved.size == 1) {
                    ActionButton(Icons.AutoMirrored.Default.OpenInNew, stringResource(R.string.f_Pdf_open)) {
                        openFile(context, saved[0].uri, saved[0].mimeType)
                    }
                }
                ActionButton(Icons.Default.Share, stringResource(R.string.f_Pdf_share)) {
                    shareFiles(context, saved.map { it.uri }, PdfJobs.MIME_TYPE, SHARE_ALIAS)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(Icons.Default.Tune, stringResource(R.string.f_Pdf_change), onClick = onChange)
                ActionButton(Icons.Default.FolderOpen, stringResource(R.string.f_Pdf_another), onClick = onAnother)
            }
        }
    }
    // Again, as it was or another way
    Reveal(visible = j.phase == PdfJobs.Phase.Failed || j.phase == PdfJobs.Phase.Cancelled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(Icons.Default.Tune, stringResource(R.string.f_Pdf_change), onClick = onChange)
            ActionButton(Icons.Default.FolderOpen, stringResource(R.string.f_Pdf_another), onClick = onAnother)
        }
    }
}

private fun labelOf(action: PdfAction) = when (action) {
    PdfAction.Merge -> R.string.f_Pdf_action_merge
    PdfAction.Split -> R.string.f_Pdf_action_split
    PdfAction.Compress -> R.string.f_Pdf_action_compress
    PdfAction.Rotate -> R.string.f_Pdf_action_rotate
    PdfAction.Protect -> R.string.f_Pdf_action_protect
    PdfAction.Unlock -> R.string.f_Pdf_action_unlock
}

private fun iconOf(action: PdfAction): ImageVector = when (action) {
    PdfAction.Merge -> Icons.AutoMirrored.Default.CallMerge
    PdfAction.Split -> Icons.AutoMirrored.Default.CallSplit
    PdfAction.Compress -> Icons.Default.Compress
    PdfAction.Rotate -> Icons.Default.Rotate90DegreesCw
    PdfAction.Protect -> Icons.Default.Lock
    PdfAction.Unlock -> Icons.Default.LockOpen
}

/** What a name not typed ends with, after the first file's: what was done. */
private fun suffixOf(o: PdfOptions) = when (o.action) {
    PdfAction.Merge -> R.string.f_Pdf_suffix_merged
    PdfAction.Split -> if (o.splitMode == SplitMode.Extract) R.string.f_Pdf_suffix_extracted else R.string.f_Pdf_suffix_part
    PdfAction.Compress -> R.string.f_Pdf_suffix_compressed
    PdfAction.Rotate -> R.string.f_Pdf_suffix_rotated
    PdfAction.Protect -> R.string.f_Pdf_suffix_protected
    PdfAction.Unlock -> R.string.f_Pdf_suffix_unlocked
}

private fun presetLabel(preset: Preset) = when (preset) {
    Preset.Screen -> R.string.f_Pdf_preset_screen
    Preset.Ebook -> R.string.f_Pdf_preset_ebook
    Preset.Printer -> R.string.f_Pdf_preset_printer
    Preset.Prepress -> R.string.f_Pdf_preset_prepress
    Preset.Lossless -> R.string.f_Pdf_preset_lossless
}

private fun presetNote(preset: Preset) = when (preset) {
    Preset.Screen -> R.string.f_Pdf_preset_screen_note
    Preset.Ebook -> R.string.f_Pdf_preset_ebook_note
    Preset.Printer -> R.string.f_Pdf_preset_printer_note
    Preset.Prepress -> R.string.f_Pdf_preset_prepress_note
    Preset.Lossless -> R.string.f_Pdf_preset_lossless_note
}

/** The work's line under the step bar: what it's at, or that it failed. */
private fun captionOf(j: PdfJobs.Job) = when (j.phase) {
    PdfJobs.Phase.Failed -> R.string.f_Pdf_failed
    PdfJobs.Phase.Saving -> R.string.f_Pdf_saving
    PdfJobs.Phase.Starting -> R.string.f_Pdf_starting
    else -> when (j.options.action) {
        PdfAction.Merge -> R.string.f_Pdf_merging
        PdfAction.Split -> R.string.f_Pdf_splitting
        PdfAction.Compress -> R.string.f_Pdf_compressing
        PdfAction.Rotate -> R.string.f_Pdf_rotating
        PdfAction.Protect -> R.string.f_Pdf_protecting
        PdfAction.Unlock -> R.string.f_Pdf_unlocking
    }
}
