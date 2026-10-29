package org.lukemech.lmtoolbox.ui.pages.features.media.file_hash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.media.file_hash.Checksums
import org.lukemech.lmtoolbox.features.media.file_hash.ExpectedHash
import org.lukemech.lmtoolbox.features.media.file_hash.FileHash
import org.lukemech.lmtoolbox.features.media.file_hash.FileHashFeature
import org.lukemech.lmtoolbox.features.media.file_hash.HashAlgorithm
import org.lukemech.lmtoolbox.features.media.file_hash.HashMode
import org.lukemech.lmtoolbox.features.media.file_hash.MultiDigest
import org.lukemech.lmtoolbox.ui.components.ActionButton
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.Chip
import org.lukemech.lmtoolbox.ui.components.ChipGroup
import org.lukemech.lmtoolbox.ui.components.ErrorText
import org.lukemech.lmtoolbox.ui.components.Fade
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.helpers.copyText
import org.lukemech.lmtoolbox.helpers.pastedText
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.tasks.ShownWhile

private enum class Step { File, Options, Results }

/** The "Share to" entry ([FileHashFeature.S_DSHKEY]): what's shared from here doesn't come back to it. */
private const val ALIAS = "share.File.Hash"

/**
 * File hash in three steps, like the video encoder: the file (or text typed), the algorithms, then its hashes,
 * read once for all of them and checked against a hash pasted or a checksum file loaded. One file at a time.
 */
@Composable
fun FileHashPage(providedInput: String? = "") {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var step by rememberSaveable { mutableStateOf(Step.File) }
    // What's open is FileHash's: it stays when the page is left, a file being read read on meanwhile (a task)
    var mode by FileHash::mode
    var file by FileHash::file
    var text by FileHash::text
    // The settings' default ones, changed here for this time only
    var algorithms by remember { mutableStateOf(FileHash.algorithms(context)) }
    var expectedText by FileHash::expectedText
    var expectedFile by FileHash::expectedFile
    // Another file to check against (not a checksum file): its own hashes, by the same algorithms
    var reference by FileHash::reference

    // The files' reading: the one at it (the file, or the one it's checked against), and how far
    var reading by FileHash::reading
    var readBytes by FileHash::readBytes
    val hashing = FileHash.run.running
    ShownWhile(FileHash.run)

    /** The open file, or the one it's checked against, changed, if it's still [uri] (not another one opened meanwhile). */
    fun update(uri: Uri, change: (FileHash.Item) -> FileHash.Item) {
        file = file?.let { if (it.uri == uri) change(it) else it }
        reference = reference?.let { if (it.uri == uri) change(it) else it }
    }

    /**
     * What's missing of the [algorithms] read (none read twice): the file's (not the text's: hashed as it's shown), then
     * the one it's checked against; one at a time, then anything asked for meanwhile. A file that couldn't be read
     * is left alone (calculate() tries it again). A task: it goes on as the page is left.
     */
    fun hashMissing() {
        fun next() = listOfNotNull(file.takeIf { mode == HashMode.Files }, reference)
            .firstOrNull { it.state != FileHash.State.Failed && (algorithms - it.hashes.keys).isNotEmpty() }
        val first = next() ?: return
        val app = context.applicationContext
        FileHash.run.start(app, first.name) {
            try {
                while (true) {
                    val item = next() ?: break
                    reading = item
                    readBytes = 0
                    try {
                        update(item.uri) { it.copy(state = FileHash.State.Hashing, error = null) }
                        val hashes = FileHash.hash(app, item.uri, algorithms - item.hashes.keys) { read ->
                            readBytes = read
                            FileHash.run.progress(if (item.size > 0) read.toFloat() / item.size else null)
                        }
                        update(item.uri) { it.copy(hashes = it.hashes + hashes, state = FileHash.State.Done) }
                    } catch (e: CancellationException) {
                        // What it had before stays
                        update(item.uri) { it.copy(state = if (it.hashes.isNotEmpty()) FileHash.State.Done else FileHash.State.Waiting) }
                        throw e
                    } catch (e: Exception) {
                        update(item.uri) { it.copy(state = FileHash.State.Failed, error = e.message ?: e.javaClass.simpleName) }
                    }
                }
            } finally {
                reading = null
            }
        }
    }

    /** The hashes shown: what's missing of them read, a file that couldn't be read tried again. */
    fun calculate() {
        focusManager.clearFocus()
        step = Step.Results
        file = file?.let { if (it.state == FileHash.State.Failed) it.copy(state = FileHash.State.Waiting) else it }
        reference = reference?.let { if (it.state == FileHash.State.Failed) it.copy(state = FileHash.State.Waiting) else it }
        hashMissing()
    }

    /** [uri] in place of the open file (its reading stopped), its algorithms next. */
    fun open(uri: Uri) {
        FileHash.run.stop()
        coroutineScope.launch {
            file = FileHash.describe(context, uri)
            mode = HashMode.Files
            step = Step.Options
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::open) }
    // What to check against: a checksum file (its lines), or any other file (e.g. the same download from elsewhere),
    // hashed as the file is and compared hash by hash
    val checksumPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val picked = FileHash.describe(context, uri)
            val lines = FileHash.readChecksumFile(context, picked)
            if (lines != null) {
                reference = null
                expectedText = lines
                expectedFile = picked.name
            } else {
                expectedText = ""
                expectedFile = null
                reference = picked
                hashMissing()
            }
        }
    }

    // The checksum file saved: by the one algorithm it's for (see exportAlgorithm)
    val exportAlgorithm = file?.let { exportAlgorithm(it, algorithms) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val alg = exportAlgorithm ?: return@rememberLauncherForActivityResult
        val item = file ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        val content = Checksums.format(listOfNotNull(item.hashes[alg]?.let { item.name to it }))
        coroutineScope.launch {
            runCatching { FileHash.write(context, uri, content) }
                .onSuccess { Toast.makeText(context, resources.getString(R.string.f_FileHash_saved, ".${alg.fileTag}"), Toast.LENGTH_SHORT).show() }
                .onFailure { AppTasks.reportFailure(FileHashFeature, "", "Couldn't save the checksum file", it.stackTraceToString()) }
        }
    }

    // Shared to the tool: straight to the file (or the text), as the video encoder has it
    LaunchedEffect(providedInput) {
        val shared = providedInput?.trim().orEmpty()
        if (shared.isEmpty()) return@LaunchedEffect
        if (shared.startsWith("content://") || shared.startsWith("file://")) open(shared.toUri())
        else {
            mode = HashMode.Text
            text = providedInput.orEmpty()
            step = Step.Options
        }
    }

    BackHandler(enabled = step != Step.File) {
        step = if (step == Step.Results) Step.Options else Step.File
    }

    // Typed text: hashed as it's shown, at once
    val textHashes = remember(text, algorithms, mode) {
        if (mode == HashMode.Text) MultiDigest.ofText(text, algorithms) else emptyMap()
    }
    // The other file's hashes stand for no file in particular, each by its algorithm
    val expected = remember(expectedText, expectedFile, reference) {
        reference?.let { r -> r.hashes.map { (alg, hash) -> ExpectedHash(hash, algorithm = alg) } } ?: Checksums.parse(expectedText, expectedFile)
    }

    val size = reading?.size ?: 0
    val steps = Steps(
        Step.entries.size, step.ordinal, false,
        working = hashing,
        progress = if (hashing && size > 0) (readBytes.toFloat() / size).coerceIn(0f, 1f) else null,
        caption = reading?.takeIf { hashing }?.let { stringResource(R.string.f_FileHash_reading, it.name) },
        onCancel = if (hashing) FileHash.run::stop else null,
    )
    val title = stringResource(R.string.fp_FileHash)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val item = file
            // What's hashed: the open file, or the text
            val ready = mode == HashMode.Text || item != null
            when (step) {
                Step.Results if ready -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = Step.Options }) {
                            SubjectCard(mode, item, text)
                            Spacer(modifier = Modifier.height(16.dp))
                            ResultsStep(
                                if (mode == HashMode.Text) null else item, if (mode == HashMode.Text) textHashes else item?.hashes.orEmpty(),
                                algorithms, expected, expectedText, expectedFile, reference,
                                onExpectedText = { expectedText = it },
                                onLoad = { checksumPicker.launch(arrayOf("*/*")) },
                                onUnload = {
                                    // Not read on for nothing
                                    if (reading != null && reading?.uri == reference?.uri) FileHash.run.stop()
                                    reference = null
                                    expectedText = ""
                                    expectedFile = null
                                },
                                onCalculate = {
                                    algorithms = algorithms + it
                                    calculate()
                                },
                                hashing = hashing,
                            )
                        }
                    },
                    buttons = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionButton(Icons.Default.Share, stringResource(R.string.f_FileHash_share), enabled = !hashing) {
                                shareText(context, shareable(context, mode, item, textHashes, algorithms))
                            }
                            if (mode == HashMode.Files) ActionButton(
                                Icons.Default.Save,
                                stringResource(R.string.f_FileHash_export, (exportAlgorithm ?: HashAlgorithm.SHA256).fileTag),
                                enabled = !hashing && exportAlgorithm != null
                            ) {
                                val alg = exportAlgorithm ?: return@ActionButton
                                exporter.launch("${item?.name ?: "checksums"}.${alg.fileTag}")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionButton(Icons.Default.Tune, stringResource(R.string.f_FileHash_change)) { step = Step.Options }
                            if (mode == HashMode.Files) ActionButton(Icons.Default.FolderOpen, stringResource(R.string.f_FileHash_another)) {
                                picker.launch(arrayOf("*/*"))
                            } else ActionButton(Icons.Default.Edit, stringResource(R.string.f_FileHash_edit_text)) { step = Step.File }
                        }
                    }
                )

                Step.Options if ready -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = Step.File }) {
                            SubjectCard(mode, item, text)
                            Note(stringResource(R.string.f_FileHash_text_note), visible = mode == HashMode.Text)
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                AlgorithmChoice(algorithms) { algorithms = it }
                            }
                        }
                    },
                    buttons = {
                        BigButton(
                            Icons.Default.Fingerprint,
                            stringResource(if (hashing) R.string.f_FileHash_calculating else R.string.f_FileHash_calculate),
                            enabled = !hashing && algorithms.isNotEmpty()
                        ) { calculate() }
                    }
                )

                else -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, verticalArrangement = Arrangement.Center) {
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
                    },
                    buttons = {
                        // The open file stays a tap away (kept while it folds away)
                        val last = remember { arrayOfNulls<FileHash.Item>(1) }
                        if (item != null) last[0] = item
                        Reveal(visible = item != null) {
                            val open = last[0] ?: return@Reveal
                            SecondaryButton(stringResource(R.string.f_FileHash_back_to, open.name)) {
                                mode = HashMode.Files
                                step = if (open.hashes.isNotEmpty()) Step.Results else Step.Options
                            }
                        }
                        // Text instead of a file: a line, growing up to three; its arrow, once there's some, goes on with it
                        InputField(
                            value = text,
                            onValueChange = { text = it },
                            // Its hint inside it, not a label over it: a label's room would set it apart from the button above
                            placeholder = { Text(stringResource(R.string.f_FileHash_text_label)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Default.Notes, contentDescription = null) },
                            trailingIcon = {
                                Fade(visible = text.isNotEmpty()) {
                                    IconButton(onClick = {
                                        focusManager.clearFocus()
                                        mode = HashMode.Text
                                        step = Step.Options
                                    }) {
                                        Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = stringResource(R.string.f_FileHash_next))
                                    }
                                }
                            },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BigButton(Icons.Default.FolderOpen, stringResource(R.string.f_FileHash_pick), enabled = true) {
                            picker.launch(arrayOf("*/*"))
                        }
                    }
                )
            }
        }
    }
}

/** What's hashed, as the video encoder shows its file: the file's name and size, or the text. */
@Composable
private fun SubjectCard(mode: HashMode, file: FileHash.Item?, text: String) {
    val context = LocalContext.current
    if (mode == HashMode.Text) SubjectCard(
        Icons.AutoMirrored.Default.TextSnippet, stringResource(R.string.f_FileHash_text),
        text.replace('\n', ' ')
    ) else if (file != null) SubjectCard(
        Icons.AutoMirrored.Default.InsertDriveFile, file.name,
        file.size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    )
}

@Composable
private fun SubjectCard(icon: ImageVector, title: String, details: String?, action: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            if (details != null) Text(
                details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        action?.invoke()
    }
}

/** Which algorithms are computed, a chip each. */
@Composable
fun AlgorithmChoice(selected: Set<HashAlgorithm>, onChange: (Set<HashAlgorithm>) -> Unit) {
    ChipGroup(stringResource(R.string.f_FileHash_algorithms)) {
        HashAlgorithm.entries.forEach { alg ->
            Chip(alg.label, selected = alg in selected) { onChange(if (alg in selected) selected - alg else selected + alg) }
        }
    }
}

/**
 * The hashes to check against (pasted, or a checksum file's), then the file's (no [file]: the text's) [hashes] in
 * one card, the verdict on top, each row marked where it matches or doesn't; what's still to compute to tell, offered.
 */
@Composable
private fun ResultsStep(
    file: FileHash.Item?,
    hashes: Map<HashAlgorithm, String>,
    algorithms: Set<HashAlgorithm>,
    expected: List<ExpectedHash>,
    expectedText: String,
    expectedFile: String?,
    reference: FileHash.Item?,
    onExpectedText: (String) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onCalculate: (Set<HashAlgorithm>) -> Unit,
    hashing: Boolean,
) {
    val context = LocalContext.current
    val shown = hashes.filterKeys { it in algorithms }
    val mine = Checksums.forName(expected, file?.name)
    val verdict = Checksums.verify(shown, mine)
    val state = file?.state

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VerifyInput(expected, expectedText, expectedFile, reference, onExpectedText, onLoad, onUnload)

        // What's to compute yet to tell
        val missing = remember { arrayOfNulls<String>(1) }
        if (verdict.missing.isNotEmpty()) missing[0] = verdict.missing.joinToString(", ") { it.label }
        Reveal(visible = verdict.missing.isNotEmpty() && !hashing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp)
            ) {
                Text(
                    stringResource(R.string.f_FileHash_need, missing[0].orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onCalculate(verdict.missing) }) { Text(stringResource(R.string.f_FileHash_calculate_too, missing[0].orEmpty())) }
            }
        }
        Note(
            stringResource(R.string.f_FileHash_not_listed),
            visible = expected.isNotEmpty() && mine.isEmpty() && (file == null || state == FileHash.State.Done)
        )
        Note(stringResource(R.string.f_FileHash_waiting), visible = state == FileHash.State.Waiting && shown.isEmpty() && !hashing)
        ErrorText(file?.error?.takeIf { state == FileHash.State.Failed }?.let { stringResource(R.string.f_FileHash_failed, it) })
        ErrorText(reference?.error?.takeIf { reference.state == FileHash.State.Failed }?.let { stringResource(R.string.f_FileHash_failed, it) })

        Results {
            VerdictRow(if (verdict.isMismatch) false else if (verdict.isMatch) true else null)
            HashAlgorithm.entries.forEach { alg ->
                val hash = shown[alg]
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
                    ) { copyText(context, last[0].orEmpty(), alg.label, context.getString(R.string.f_FileHash_copied, alg.label)) }
                }
            }
        }
        Note(stringResource(R.string.f_FileHash_copy_hint), visible = shown.isNotEmpty())
    }
}

/**
 * A field for a hash (or a checksum file's lines) pasted, or the file loaded in its place (shown as the file hashed
 * is): a checksum file, or [reference], another file compared hash by hash; what's understood of it under it.
 */
@Composable
private fun VerifyInput(
    expected: List<ExpectedHash>,
    expectedText: String,
    expectedFile: String?,
    reference: FileHash.Item?,
    onExpectedText: (String) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    val context = LocalContext.current
    val loaded = expectedFile != null || reference != null
    Column {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.f_FileHash_verify), style = MaterialTheme.typography.labelLarge)
            Reveal(visible = !loaded) {
                InputField(
                    value = expectedText,
                    onValueChange = onExpectedText,
                    placeholder = { Text(stringResource(R.string.f_FileHash_expected_hint)) },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { pastedText(context)?.let(onExpectedText) }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.f_FileHash_paste))
                            }
                            IconButton(onClick = onLoad) {
                                Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.f_FileHash_load))
                            }
                        }
                    },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // The loaded file in the field's place, as the file hashed is shown: its name and size (kept while it folds away)
        val lastFile = remember { arrayOfNulls<Pair<String, Long>>(1) }
        when {
            reference != null -> lastFile[0] = reference.name to reference.size
            expectedFile != null -> lastFile[0] = expectedFile.ifEmpty { "…" } to expectedText.toByteArray(Charsets.UTF_8).size.toLong()
        }
        Reveal(visible = loaded) {
            val (name, size) = lastFile[0] ?: return@Reveal
            SubjectCard(
                Icons.AutoMirrored.Default.InsertDriveFile, name,
                size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
            ) {
                IconButton(onClick = onUnload) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.f_FileHash_unload)) }
            }
        }
        // What it is: a hash by which algorithm (or several hashes), or none
        val single = expected.singleOrNull()
        val detected = when {
            loaded || expected.isEmpty() -> null
            single != null -> stringResource(R.string.f_FileHash_detected, single.candidates.joinToString(" / ") { it.label })
            else -> stringResource(R.string.f_FileHash_hashes, expected.size)
        }
        val last = remember { arrayOfNulls<String>(1) }
        if (detected != null) last[0] = detected
        Note(last[0].orEmpty(), visible = detected != null)
        // Nothing to check against in what was pasted, or in the file loaded
        ErrorText(stringResource(R.string.f_FileHash_not_hash).takeIf { reference == null && (loaded || expectedText.isNotBlank()) && expected.isEmpty() })
    }
}

/** On top of the hashes: whether they match what's expected, in its color; nothing without a verdict. */
@Composable
private fun VerdictRow(match: Boolean?) {
    // The last one, while it folds away
    val last = remember { booleanArrayOf(true) }
    if (match != null) last[0] = match
    Reveal(visible = match != null) {
        val ok = last[0]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                stringResource(if (ok) R.string.f_FileHash_match else R.string.f_FileHash_mismatch),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            VerdictBadge(ok)
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
        // The last one, while it fades away
        val last = remember { booleanArrayOf(true) }
        if (match != null) last[0] = match
        Fade(visible = match != null) { VerdictBadge(last[0]) }
    }
}

/** A tick for a match, a cross for a mismatch. */
@Composable
private fun VerdictBadge(ok: Boolean) {
    Icon(
        if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
        contentDescription = stringResource(if (ok) R.string.f_FileHash_match else R.string.f_FileHash_mismatch),
        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 8.dp)
    )
}

/** The one algorithm an exported checksum file is by: SHA-256 when it's computed, else the first other one there is (CRC32 has no such file). */
private fun exportAlgorithm(file: FileHash.Item, algorithms: Set<HashAlgorithm>): HashAlgorithm? {
    val have = file.hashes.keys.intersect(algorithms) - HashAlgorithm.CRC32
    return HashAlgorithm.SHA256.takeIf { it in have } ?: HashAlgorithm.entries.firstOrNull { it in have }
}

/** All hashes as text to share: the file's name and size, then its hashes, a line each (the text's: just them). */
private fun shareable(context: Context, mode: HashMode, file: FileHash.Item?, textHashes: Map<HashAlgorithm, String>, algorithms: Set<HashAlgorithm>): String {
    fun lines(hashes: Map<HashAlgorithm, String>) =
        HashAlgorithm.entries.filter { it in algorithms }.mapNotNull { alg -> hashes[alg]?.let { "${alg.label}: $it" } }
    if (mode == HashMode.Text || file == null) return lines(textHashes).joinToString("\n")
    val size = file.size.takeIf { it > 0 }?.let { " (${Formatter.formatShortFileSize(context, it)})" }.orEmpty()
    return (listOf(file.name + size) + lines(file.hashes)).joinToString("\n")
}

/** [text] to other apps, but not back here ([ALIAS]). */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(
        Intent.createChooser(send, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ALIAS)))
    )
}

