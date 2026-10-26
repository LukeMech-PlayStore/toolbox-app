package org.lukemech.lmtoolbox.ui.pages.features.media.file_hash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.media.file_hash.Checksums
import org.lukemech.lmtoolbox.features.media.file_hash.ExpectedHash
import org.lukemech.lmtoolbox.features.media.file_hash.FileHash
import org.lukemech.lmtoolbox.features.media.file_hash.FileHashFeature
import org.lukemech.lmtoolbox.features.media.file_hash.HashAlgorithm
import org.lukemech.lmtoolbox.features.media.file_hash.HashMode
import org.lukemech.lmtoolbox.features.media.file_hash.MultiDigest
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.ui.components.ActionButton
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.Fade
import org.lukemech.lmtoolbox.ui.components.fadeEnter
import org.lukemech.lmtoolbox.ui.components.fadeExit
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.ToolIntro

private enum class Step { Input, Results }

/** The "Share to" entry ([FileHashFeature.S_DSHKEY]): what's shared from here doesn't come back to it. */
private const val ALIAS = "share.File.Hash"

/**
 * File hash in two steps: what to hash (files, or text typed) and by which algorithms, then the hashes, each
 * file read once for all of them, checked against hashes pasted or a checksum file loaded.
 */
@Composable
fun FileHashPage(providedInput: String? = "") {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // What's open stays when the page is left (FileHash.files, ...); a file being read then is read again
    var mode by remember { mutableStateOf(FileHash.mode) }
    var files by remember { mutableStateOf(FileHash.files.map { if (it.state == FileHash.State.Hashing) it.copy(state = FileHash.State.Waiting) else it }) }
    var text by remember { mutableStateOf(FileHash.text) }
    var step by remember { mutableStateOf(if (FileHash.showResults) Step.Results else Step.Input) }
    var algorithms by remember { mutableStateOf(FileHash.algorithms(context)) }
    var expectedText by remember { mutableStateOf(FileHash.expectedText) }
    var expectedFile by remember { mutableStateOf(FileHash.expectedFile) }
    SideEffect {
        FileHash.mode = mode
        FileHash.files = files
        FileHash.text = text
        FileHash.showResults = step == Step.Results
        FileHash.expectedText = expectedText
        FileHash.expectedFile = expectedFile
    }

    // The files' reading: how far of all of them, the one at it
    var job by remember { mutableStateOf<Job?>(null) }
    var readBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    val hashing = job != null

    fun update(uri: Uri, change: (FileHash.Item) -> FileHash.Item) {
        files = files.map { if (it.uri == uri) change(it) else it }
    }

    /** Every file's hashes by the [algorithms] it doesn't have yet (none read twice for the same one), one file at a time. */
    fun calculate() {
        focusManager.clearFocus()
        // Shared in with none picked: the usual ones
        if (algorithms.isEmpty()) algorithms = HashAlgorithm.DEFAULTS
        FileHash.saveAlgorithms(context, algorithms)
        step = Step.Results
        if (mode == HashMode.Text || job != null) return
        val wanted = algorithms
        val todo = files.filter { (wanted - it.hashes.keys).isNotEmpty() }
        if (todo.isEmpty()) return
        totalBytes = todo.sumOf { it.size }
        readBytes = 0
        lateinit var started: Job
        started = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                for (item in todo) {
                    val before = readBytes
                    update(item.uri) { it.copy(state = FileHash.State.Hashing, error = null) }
                    try {
                        val hashes = FileHash.hash(context, item.uri, wanted - item.hashes.keys) { read -> readBytes = before + read }
                        update(item.uri) { it.copy(hashes = it.hashes + hashes, state = FileHash.State.Done) }
                    } catch (e: CancellationException) {
                        // What it had before stays
                        update(item.uri) { it.copy(state = if (it.hashes.isNotEmpty()) FileHash.State.Done else FileHash.State.Waiting) }
                        throw e
                    } catch (e: Exception) {
                        update(item.uri) { it.copy(state = FileHash.State.Failed, error = e.message ?: e.javaClass.simpleName) }
                    }
                    readBytes = before + item.size
                }
            } finally {
                // Not another one's, started as this one was cancelled
                if (job === started) job = null
            }
        }
        job = started
        started.start()
    }

    fun addFiles(uris: List<Uri>, replace: Boolean = false) {
        coroutineScope.launch {
            val kept = if (replace) emptyList() else files
            val new = uris.distinct().filter { uri -> kept.none { it.uri == uri } }.map { FileHash.describe(context, it) }
            files = kept + new
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) addFiles(uris) }
    // Others, from the results: in place of these, read right away
    val otherPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            files = uris.distinct().map { FileHash.describe(context, it) }
            calculate()
        }
    }
    val checksumPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching { FileHash.readChecksumFile(context, uri) }
                .onSuccess { (content, name) ->
                    expectedText = content
                    expectedFile = name ?: ""
                }
                .onFailure { LinuxTasks.reportFailure(FileHashFeature, "", "Couldn't read the checksum file", it.stackTraceToString()) }
        }
    }

    // The checksum file saved: by the one algorithm it's for (see exportAlgorithm)
    val exportAlgorithm = exportAlgorithm(files, algorithms)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val alg = exportAlgorithm ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        val content = Checksums.format(files.mapNotNull { f -> f.hashes[alg]?.let { f.name to it } })
        coroutineScope.launch {
            runCatching { FileHash.write(context, uri, content) }
                .onSuccess { Toast.makeText(context, resources.getString(R.string.f_FileHash_saved, ".${alg.fileTag}"), Toast.LENGTH_SHORT).show() }
                .onFailure { LinuxTasks.reportFailure(FileHashFeature, "", "Couldn't save the checksum file", it.stackTraceToString()) }
        }
    }

    // Shared to the tool: files (one, or several at once) read straight away; text, hashed
    LaunchedEffect(providedInput) {
        val shared = providedInput?.lines()?.filter { it.isNotBlank() }.orEmpty()
        if (shared.isEmpty()) return@LaunchedEffect
        if (shared.all { it.startsWith("content://") || it.startsWith("file://") }) {
            mode = HashMode.Files
            job?.cancel()
            job = null
            files = shared.distinct().map { FileHash.describe(context, it.toUri()) }
        } else {
            mode = HashMode.Text
            text = providedInput.orEmpty()
        }
        calculate()
    }

    BackHandler(enabled = step == Step.Results) { step = Step.Input }

    // Typed text: hashed as it's shown, at once
    val textHashes = remember(text, algorithms, mode) {
        if (mode == HashMode.Text) MultiDigest.ofText(text, algorithms) else emptyMap()
    }
    val expected = remember(expectedText, expectedFile) { Checksums.parse(expectedText, expectedFile) }

    val current = files.firstOrNull { it.state == FileHash.State.Hashing }
    val steps = Steps(
        Step.entries.size, step.ordinal, false,
        working = hashing,
        progress = if (hashing && totalBytes > 0) (readBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null,
        caption = current?.let { stringResource(R.string.f_FileHash_reading, it.name) },
        detail = if (hashing && files.size > 1) current?.let { stringResource(R.string.f_FileHash_count, files.indexOf(it) + 1, files.size) } else null,
        onCancel = if (hashing) ({ job?.cancel() }) else null,
    )
    val title = stringResource(R.string.fp_FileHash)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            // One heading for both steps (its steps' bar stays put); what's under it fades from one to the other
            StepWithButtons(
                content = {
                    PageHeading(
                        steps = steps, title = title,
                        onBack = if (step == Step.Results) ({ step = Step.Input }) else null,
                        header = {
                            Reveal(visible = step == Step.Input) {
                                ModeChoice(mode) {
                                    mode = it
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    ) {
                        AnimatedContent(step, transitionSpec = { fadeEnter togetherWith fadeExit }, label = "step") { shown ->
                            Column {
                                when (shown) {
                                    Step.Input -> InputStep(
                                        mode, files, text, { text = it }, algorithms, { algorithms = it },
                                        onAdd = { picker.launch(arrayOf("*/*")) },
                                        onRemove = { uri -> files = files.filter { it.uri != uri } }
                                    )
                                    Step.Results -> ResultsStep(
                                        mode, files, text, textHashes, algorithms, expected, expectedText, expectedFile,
                                        onExpectedText = { expectedText = it },
                                        onLoad = { checksumPicker.launch(arrayOf("*/*")) },
                                        onUnload = { expectedText = ""; expectedFile = null },
                                        onCalculate = {
                                            algorithms = algorithms + it
                                            calculate()
                                        },
                                        hashing = hashing,
                                    )
                                }
                            }
                        }
                    }
                },
                buttons = {
                    AnimatedContent(step, transitionSpec = { fadeEnter togetherWith fadeExit }, label = "buttons") { shown ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (shown) {
                                Step.Results -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    ActionButton(Icons.Default.Share, stringResource(R.string.f_FileHash_share), enabled = !hashing) {
                                        shareText(context, shareable(context, mode, files, textHashes, algorithms))
                                    }
                                    if (mode == HashMode.Files) {
                                        ActionButton(
                                            Icons.Default.Save,
                                            stringResource(R.string.f_FileHash_export, (exportAlgorithm ?: HashAlgorithm.SHA256).fileTag),
                                            enabled = !hashing && exportAlgorithm != null
                                        ) {
                                            val alg = exportAlgorithm ?: return@ActionButton
                                            exporter.launch((files.singleOrNull()?.name ?: "checksums") + ".${alg.fileTag}")
                                        }
                                        ActionButton(Icons.Default.FolderOpen, stringResource(R.string.f_FileHash_other), enabled = !hashing) {
                                            otherPicker.launch(arrayOf("*/*"))
                                        }
                                    } else {
                                        ActionButton(Icons.Default.Edit, stringResource(R.string.f_FileHash_edit_text)) { step = Step.Input }
                                    }
                                }

                                Step.Input -> {
                                    Reveal(visible = mode == HashMode.Files && files.any { it.hashes.isNotEmpty() }) {
                                        SecondaryButton(stringResource(R.string.f_FileHash_back_to_results), !hashing) { step = Step.Results }
                                    }
                                    val pick = mode == HashMode.Files && files.isEmpty()
                                    BigButton(
                                        if (pick) Icons.Default.FolderOpen else Icons.Default.Fingerprint,
                                        stringResource(
                                            when {
                                                pick -> R.string.f_FileHash_pick
                                                hashing -> R.string.f_FileHash_calculating
                                                else -> R.string.f_FileHash_calculate
                                            }
                                        ),
                                        enabled = pick || (!hashing && algorithms.isNotEmpty())
                                    ) { if (pick) picker.launch(arrayOf("*/*")) else calculate() }
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

/** Files or text: what's hashed. */
@Composable
private fun ModeChoice(mode: HashMode, onSelect: (HashMode) -> Unit) {
    val modes = listOf(
        Triple(HashMode.Files, Icons.AutoMirrored.Default.InsertDriveFile, stringResource(R.string.f_FileHash_files)),
        Triple(HashMode.Text, Icons.AutoMirrored.Default.TextSnippet, stringResource(R.string.f_FileHash_text)),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        modes.forEachIndexed { i, (m, icon, label) ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onSelect(m) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** The files picked (or the tool's intro while there are none), or the text; the algorithms under them. */
@Composable
private fun InputStep(
    mode: HashMode,
    files: List<FileHash.Item>,
    text: String,
    onText: (String) -> Unit,
    algorithms: Set<HashAlgorithm>,
    onAlgorithms: (Set<HashAlgorithm>) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit,
) {
    val context = LocalContext.current
    Reveal(visible = mode == HashMode.Files && files.isEmpty()) {
        Box(modifier = Modifier.padding(top = 32.dp)) {
            ToolIntro(
                Icons.Default.Fingerprint, stringResource(R.string.f_FileHash_intro_title),
                listOf(
                    Icons.Default.FolderOpen to stringResource(R.string.f_FileHash_intro_1),
                    Icons.Default.Fingerprint to stringResource(R.string.f_FileHash_intro_2),
                    Icons.Default.Verified to stringResource(R.string.f_FileHash_intro_3),
                ),
                stringResource(R.string.f_FileHash_intro_tip)
            )
        }
    }
    Reveal(visible = mode == HashMode.Files && files.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp).animateContentSize()) {
            files.forEach { file ->
                key(file.uri) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                            if (file.size > 0) Text(
                                Formatter.formatShortFileSize(context, file.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onRemove(file.uri) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.f_FileHash_remove))
                        }
                    }
                }
            }
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.f_FileHash_add))
            }
        }
    }
    Reveal(visible = mode == HashMode.Text) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            InputField(
                value = text,
                onValueChange = onText,
                label = { Text(stringResource(R.string.f_FileHash_text_label)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Default.Notes, contentDescription = null) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.f_FileHash_text_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Reveal(visible = mode == HashMode.Text || files.isNotEmpty()) {
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            AlgorithmChoice(algorithms, onAlgorithms)
        }
    }
}

/** Which algorithms are computed: those this device has, a chip each (SHA-3 noted when it's missing). */
@Composable
fun AlgorithmChoice(selected: Set<HashAlgorithm>, onChange: (Set<HashAlgorithm>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.f_FileHash_algorithms), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HashAlgorithm.available.forEach { alg ->
                FilterChip(
                    selected = alg in selected,
                    onClick = { onChange(if (alg in selected) selected - alg else selected + alg) },
                    label = { Text(alg.label) }
                )
            }
        }
        if (!HashAlgorithm.SHA3_256.isAvailable) {
            Text(stringResource(R.string.f_FileHash_sha3_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The hashes to check against (pasted, or a checksum file's), then each file's (or the text's) hashes,
 * a row each, marked where they match or don't; what's still to compute to tell, offered.
 */
@Composable
private fun ResultsStep(
    mode: HashMode,
    files: List<FileHash.Item>,
    text: String,
    textHashes: Map<HashAlgorithm, String>,
    algorithms: Set<HashAlgorithm>,
    expected: List<ExpectedHash>,
    expectedText: String,
    expectedFile: String?,
    onExpectedText: (String) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onCalculate: (Set<HashAlgorithm>) -> Unit,
    hashing: Boolean,
) {
    val context = LocalContext.current
    // Each one's hashes (those picked now) and what they come to against what's expected
    val shown = if (mode == HashMode.Text) {
        val mine = Checksums.forName(expected, null)
        listOf(Shown(stringResource(R.string.f_FileHash_text), text.takeIf { it.isNotEmpty() }, null, textHashes, mine.isNotEmpty(), Checksums.verify(textHashes, mine)))
    } else files.map { f ->
        val hashes = f.hashes.filterKeys { it in algorithms }
        val mine = Checksums.forName(expected, f.name)
        Shown(f.name, f.size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }, f, hashes, mine.isNotEmpty(), Checksums.verify(hashes, mine))
    }
    val missing = shown.flatMap { it.verdict.missing }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VerifyInput(expected, expectedText, expectedFile, onExpectedText, onLoad, onUnload)

        // What's to compute yet to tell: once, for all
        Reveal(visible = missing.isNotEmpty() && !hashing) {
            val names = missing.joinToString(", ") { it.label }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp)
            ) {
                Text(
                    stringResource(R.string.f_FileHash_need, names),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onCalculate(missing) }) { Text(stringResource(R.string.f_FileHash_calculate_too, names)) }
            }
        }

        // Of several files checked: how many came out right
        val checked = shown.filter { it.verdict.isMatch || it.verdict.isMismatch }
        Reveal(visible = shown.size > 1 && checked.isNotEmpty()) {
            val matching = checked.count { it.verdict.isMatch }
            Text(
                stringResource(R.string.f_FileHash_summary, matching, shown.size),
                style = MaterialTheme.typography.titleMedium,
                color = if (matching == shown.size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }

        shown.forEach { s ->
            key(s.item?.uri ?: "text") {
                HashCard(s.title, s.details, s.item, s.hashes, s.verdict, notListed = expected.isNotEmpty() && !s.listed && (s.item == null || s.item.state == FileHash.State.Done))
            }
        }
        Note(stringResource(R.string.f_FileHash_copy_hint))
    }
}

/** A file's (no [item]: the text's) [hashes] as they're shown, whether [listed] in what's expected, and its [verdict]. */
private data class Shown(
    val title: String,
    val details: String?,
    val item: FileHash.Item?,
    val hashes: Map<HashAlgorithm, String>,
    val listed: Boolean,
    val verdict: Checksums.Verdict,
)

/** A field for a hash (or a checksum file's lines) pasted, or the checksum file loaded in its place; what's understood of it under it. */
@Composable
private fun VerifyInput(
    expected: List<ExpectedHash>,
    expectedText: String,
    expectedFile: String?,
    onExpectedText: (String) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.f_FileHash_verify), style = MaterialTheme.typography.labelLarge)
        Reveal(visible = expectedFile == null) {
            InputField(
                value = expectedText,
                onValueChange = onExpectedText,
                placeholder = { Text(stringResource(R.string.f_FileHash_expected_hint)) },
                trailingIcon = {
                    IconButton(onClick = { pasted(context)?.let(onExpectedText) }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.f_FileHash_paste))
                    }
                },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // The loaded file, standing for its lines
        val shownFile = remember { arrayOfNulls<String>(1) }
        if (expectedFile != null) shownFile[0] = expectedFile
        Reveal(visible = expectedFile != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                    .padding(start = 16.dp)
            ) {
                Icon(Icons.AutoMirrored.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.f_FileHash_loaded, shownFile[0].orEmpty().ifEmpty { "…" }, expected.size),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onUnload) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.f_FileHash_unload)) }
            }
        }
        // What it is: a hash by which algorithm (or several hashes), or none
        val single = expected.singleOrNull()
        StatusLine(
            when {
                expectedFile != null || expected.isEmpty() -> null
                single != null -> stringResource(R.string.f_FileHash_detected, single.candidates.joinToString(" / ") { it.label })
                else -> stringResource(R.string.f_FileHash_hashes, expected.size)
            },
            MaterialTheme.colorScheme.onSurfaceVariant, inset = false
        )
        StatusLine(
            stringResource(R.string.f_FileHash_not_hash).takeIf { expectedText.isNotBlank() && expected.isEmpty() },
            MaterialTheme.colorScheme.error, inset = false
        )
        Reveal(visible = expectedFile == null) {
            TextButton(onClick = onLoad, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.f_FileHash_load))
            }
        }
    }
}

/** One file's (or the text's) hashes: its name and size, how it's going, then a row per hash; a tap copies one. */
@Composable
private fun HashCard(
    title: String,
    details: String?,
    item: FileHash.Item?,
    hashes: Map<HashAlgorithm, String>,
    verdict: Checksums.Verdict,
    notListed: Boolean,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                details?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            // The whole file's verdict, by its name
            VerdictBadge(if (verdict.isMismatch) false else if (verdict.isMatch) true else null, showText = true)
        }
        val state = item?.state
        val status = when {
            state == FileHash.State.Hashing -> stringResource(R.string.f_FileHash_hashing)
            state == FileHash.State.Waiting && hashes.isEmpty() -> stringResource(R.string.f_FileHash_waiting)
            notListed -> stringResource(R.string.f_FileHash_not_listed)
            else -> null
        }
        StatusLine(status, MaterialTheme.colorScheme.onSurfaceVariant)
        StatusLine(item?.error.takeIf { state == FileHash.State.Failed }?.let { stringResource(R.string.f_FileHash_failed, it) }, MaterialTheme.colorScheme.error)

        HashAlgorithm.entries.forEach { alg ->
            val hash = hashes[alg]
            // The last one, while it folds away
            val last = remember { arrayOfNulls<String>(1) }
            if (hash != null) last[0] = hash
            Reveal(visible = hash != null) {
                HashRow(
                    alg, last[0].orEmpty(),
                    when (alg) {
                        in verdict.matched -> true
                        in verdict.mismatched -> false
                        else -> null
                    }
                ) { copy(context, alg.label, last[0].orEmpty()) }
            }
        }
    }
}

/** An algorithm's [hash], tapped to copy it; its row turning to the match's color ([match] true) or the mismatch's (false). */
@Composable
private fun HashRow(alg: HashAlgorithm, hash: String, match: Boolean?, onCopy: () -> Unit) {
    val tint = when (match) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> Color.Transparent
    }
    val background by animateColorAsState(tint.copy(alpha = if (match == null) 0f else 0.12f), tween(REVEAL_MS), label = "verdict")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(alg.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(hash, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        VerdictBadge(match, showText = false)
    }
}

/** A tick for a match, a cross for a mismatch (with its word: [showText]), fading in and out; nothing without a verdict. */
@Composable
private fun VerdictBadge(match: Boolean?, showText: Boolean) {
    // The last one, while it fades away
    val last = remember { booleanArrayOf(true) }
    if (match != null) last[0] = match
    Fade(visible = match != null) {
        val ok = last[0]
        val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            if (showText) {
                Text(
                    stringResource(if (ok) R.string.f_FileHash_match else R.string.f_FileHash_mismatch),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = color
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = if (showText) null else stringResource(if (ok) R.string.f_FileHash_match else R.string.f_FileHash_mismatch),
                tint = color
            )
        }
    }
}

/**
 * A line of what's what in [color] (e.g. why a file couldn't be read), [inset] as a card's rows are; none
 * without [text], the last one kept while it folds away.
 */
@Composable
private fun StatusLine(text: String?, color: Color, inset: Boolean = true) {
    val last = remember { arrayOfNulls<String>(1) }
    if (text != null) last[0] = text
    Reveal(visible = text != null) {
        Text(
            last[0].orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = if (inset) 16.dp else 0.dp, vertical = 2.dp)
        )
    }
}

/** The one algorithm an exported checksum file is by: SHA-256 when it's computed, else the first other one there is (CRC32 has no such file). */
private fun exportAlgorithm(files: List<FileHash.Item>, algorithms: Set<HashAlgorithm>): HashAlgorithm? {
    val have = files.flatMap { it.hashes.keys }.toSet().intersect(algorithms) - HashAlgorithm.CRC32
    return HashAlgorithm.SHA256.takeIf { it in have } ?: HashAlgorithm.entries.firstOrNull { it in have }
}

/** All hashes as text to share: each file's name and size, then its hashes, a line each. */
private fun shareable(context: Context, mode: HashMode, files: List<FileHash.Item>, textHashes: Map<HashAlgorithm, String>, algorithms: Set<HashAlgorithm>): String {
    fun lines(hashes: Map<HashAlgorithm, String>) =
        HashAlgorithm.entries.filter { it in algorithms }.mapNotNull { alg -> hashes[alg]?.let { "${alg.label}: $it" } }
    return if (mode == HashMode.Text) lines(textHashes).joinToString("\n")
    else files.filter { it.hashes.isNotEmpty() }.joinToString("\n\n") { f ->
        val size = f.size.takeIf { it > 0 }?.let { " (${Formatter.formatShortFileSize(context, it)})" }.orEmpty()
        (listOf(f.name + size) + lines(f.hashes)).joinToString("\n")
    }
}

/** [text] to other apps, but not back here ([ALIAS]). */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(
        Intent.createChooser(send, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ALIAS)))
    )
}

// A setter: primaryClip is a val in Kotlin (its getter is nullable, the setter isn't)
@Suppress("UsePropertyAccessSyntax")
private fun copy(context: Context, label: String, hash: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, hash))
    Toast.makeText(context, context.getString(R.string.f_FileHash_copied, label), Toast.LENGTH_SHORT).show()
}

/** What's on the clipboard, as text. */
private fun pasted(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
