package org.lukemech.lmtoolbox.ui.pages.features.media.md_edit

import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.lukemech.lmtoolbox.R as AppR
import org.lukemech.lmtoolbox.features.exiftool.R
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.ui.components.ActionButton
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.Fade
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.features.media.md_edit.MetadataEditorFeature
import org.lukemech.lmtoolbox.ui.components.InputField

private enum class Step { File, Tags, Export }

/** Groups ExifTool works out or reads off the file system: shown, never written. */
private val READ_ONLY_GROUPS = setOf("File", "Composite", "ExifTool", "PrintIM", "System")

/** Tags read-only although their group isn't. */
private val READ_ONLY_TAGS = setOf("PDF:Linearized", "PDF:PDFVersion", "PDF:PageCount")

private fun isReadOnly(key: String) = key.substringBefore(':') in READ_ONLY_GROUPS || key in READ_ONLY_TAGS

private fun groupOf(key: String) = if (':' in key) key.substringBefore(':') else ""
private fun tagOf(key: String) = key.substringAfter(':')

/** The file being edited: the original, its name and type, and the copy ExifTool writes. */
private class Opened(val uri: Uri, val name: String, val mimeType: String, val copy: File)

/** Tags (group:tag) whose value [staged] changes, adds or removes from [original]. */
private fun changedKeys(original: Map<String, String>, staged: Map<String, String>) =
    (original.keys + staged.keys).filter { !isReadOnly(it) && original[it] != staged[it] }

/**
 * Metadata editor in three steps, like the QR generator: the file, its tags (edited right in the
 * list, by ExifTool's groups), and the edited file to save or share. The tags are written into
 * a copy in the cache on the way to the last step; the original only changes when saved over.
 */
@Composable
fun MetadataEditorPage(providedInput: String? = "") {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var step by remember { mutableStateOf(Step.File) }
    var opened by remember { mutableStateOf<Opened?>(null) }
    // As read from the copy, and as edited
    var original by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var staged by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // "Remove all": everything goes, also what isn't listed (e.g. binary data, thumbnails)
    var stripAll by remember { mutableStateOf(false) }
    // Changes written into the copy on the way to the export
    var written by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    val changes = changedKeys(original, staged)

    fun open(uri: Uri) {
        coroutineScope.launch {
            busy = true
            val name = displayName(context, uri)
            val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "*/*"
            val dir = MetadataEditorFeature.cacheDir(context).apply { deleteRecursively(); mkdirs() }
            // ExifTool tells formats by the extension; a plain name keeps odd characters out of its command
            val ext = name.substringAfterLast('.', "")
            val copy = File(dir, if (ext.isEmpty()) "file" else "file.$ext")
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
                }.isSuccess
            }
            val tags = if (copied) readTags(context, copy) else null
            busy = false
            if (tags == null) {
                // ExifTool's failure the bottom bar tells already; the copy's (not a command), told there too
                if (!copied) LinuxTasks.reportFailure(MetadataEditorFeature, name, "Couldn't read the file")
                return@launch
            }
            opened = Opened(uri, name, mimeType, copy)
            original = tags
            staged = tags
            stripAll = false
            written = 0
            step = Step.Tags
        }
    }

    // Writes the changes into the copy, then reads it again: what's there is the new original
    fun write() {
        val file = opened?.copy ?: return
        if (changes.isEmpty()) {
            step = Step.Export
            return
        }
        val args = buildList {
            if (stripAll) add("-all=")
            changes.forEach { key ->
                val value = staged[key]
                when {
                    value != null -> add("-$key=$value")
                    // Already gone with all the rest
                    !stripAll -> add("-$key=")
                }
            }
        }
        coroutineScope.launch {
            busy = true
            val result = LinuxTasks.run(
                context,
                listOf("exiftool", "-P", "-overwrite_original") + args + file.absolutePath,
                MetadataEditorFeature
            )
            val updated = result.isSuccess && Regex("""\b1 (image )?files? updated""").containsMatchIn(result.output)
            val tags = if (updated) readTags(context, file) else null
            busy = false
            if (tags == null) {
                // ExifTool's failure the bottom bar tells already (its log); it ran but changed nothing: told there too
                if (result.isSuccess) LinuxTasks.reportFailure(MetadataEditorFeature, file.name, "ExifTool changed nothing")
                return@launch
            }
            written += changes.size
            original = tags
            staged = tags
            stripAll = false
            step = Step.Export
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::open) }

    // Opened with or shared to the editor: straight to its tags
    LaunchedEffect(providedInput) {
        if (!providedInput.isNullOrBlank()) open(providedInput.toUri())
    }

    BackHandler(enabled = step != Step.File && !busy) {
        step = if (step == Step.Export) Step.Tags else Step.File
    }

    // Where it stands, under each step's title; a tap outside the text fields hides the keyboard
    val steps = Steps(Step.entries.size, step.ordinal, busy)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val file = opened
            when {
                step == Step.File || file == null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = stringResource(AppR.string.fp_MdEd), verticalArrangement = Arrangement.Center) {
                            ToolIntro(
                                Icons.Default.EditNote, stringResource(R.string.f_MdEd_intro_title),
                                listOf(
                                    Icons.Default.FolderOpen to stringResource(R.string.f_MdEd_intro_1),
                                    Icons.Default.EditNote to stringResource(R.string.f_MdEd_intro_2),
                                    Icons.Default.Save to stringResource(R.string.f_MdEd_intro_3),
                                ),
                                stringResource(R.string.f_MdEd_intro_tip)
                            )
                        }
                    },
                    buttons = {
                        // Back from the tags: the open file stays a tap away
                        if (file != null) {
                            SecondaryButton(stringResource(R.string.f_MdEd_continue, file.name), enabled = !busy) { step = Step.Tags }
                        }
                        BigButton(Icons.Default.FolderOpen, stringResource(R.string.f_MdEd_pick), enabled = !busy) {
                            picker.launch(arrayOf("*/*"))
                        }
                    }
                )

                step == Step.Tags -> StepWithButtons(
                    content = {
                        PageHeading(
                            steps = steps,
                            title = stringResource(AppR.string.fp_MdEd),
                            onBack = { step = Step.File },
                            action = {
                                Fade(visible = changes.isNotEmpty()) {
                                    IconButton(onClick = { staged = original; stripAll = false }) {
                                        Icon(Icons.AutoMirrored.Default.Undo, contentDescription = stringResource(R.string.f_MdEd_str_reset))
                                    }
                                }
                            }
                        ) {
                            FileName(file.name)
                            val editable = staged.keys.filterNot(::isReadOnly)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                                ActionButton(Icons.Default.Add, stringResource(R.string.f_MdEd_add_tag), enabled = !busy) { adding = true }
                                ActionButton(
                                    Icons.Default.LocationOff, stringResource(R.string.f_MdEd_remove_gps),
                                    enabled = !busy && editable.any { "GPS" in it }
                                ) {
                                    staged = staged.filterKeys { isReadOnly(it) || "GPS" !in it }
                                }
                                ActionButton(Icons.Default.DeleteSweep, stringResource(R.string.f_MdEd_remove_all), enabled = !busy && editable.isNotEmpty()) {
                                    staged = staged.filterKeys(::isReadOnly)
                                    stripAll = true
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            TagList(
                                original = original,
                                staged = staged,
                                onEdit = { editing = it },
                                onRemove = { key -> staged = staged - key },
                                onRevert = { key -> staged = original[key]?.let { staged + (key to it) } ?: (staged - key) }
                            )
                        }
                    },
                    buttons = {
                        // Only greyed out while it's written: the step bar shows the progress
                        BigButton(
                            if (changes.isEmpty()) Icons.AutoMirrored.Default.ArrowForward else Icons.Default.Save,
                            if (changes.isEmpty()) stringResource(R.string.f_MdEd_next)
                            else pluralStringResource(R.plurals.f_MdEd_write, changes.size, changes.size),
                            enabled = !busy
                        ) { write() }
                    }
                )

                else -> ExportStep(file, written, steps, onBack = { step = Step.Tags }, onOpenAnother = { step = Step.File })
            }
        }
    }

    editing?.let { key ->
        TagDialog(
            title = stringResource(R.string.f_MdEd_edit_tag),
            initialGroup = groupOf(key),
            initialTag = tagOf(key),
            initialValue = staged[key] ?: original[key].orEmpty(),
            keyEditable = false,
            onDismiss = { editing = null },
            onConfirm = { _, value ->
                staged = staged + (key to value)
                editing = null
            }
        )
    }
    if (adding) {
        TagDialog(
            title = stringResource(R.string.f_MdEd_add_tag),
            initialGroup = "",
            initialTag = "",
            initialValue = "",
            keyEditable = true,
            onDismiss = { adding = false },
            onConfirm = { key, value ->
                staged = staged + (key to value)
                adding = false
            }
        )
    }
}

/** The open file's name under the editor's title: long names take two lines, not the heading. */
@Composable
private fun FileName(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis
        )
    }
}

/**
 * The tags by group, editable ones first (changed ones in the primary color, removed ones
 * struck through), then what ExifTool only reads. Tapping an editable tag edits it.
 */
@Composable
private fun TagList(
    original: Map<String, String>,
    staged: Map<String, String>,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRevert: (String) -> Unit,
) {
    val keys = (original.keys + staged.keys).distinct()
    Reveal(visible = keys.none { !isReadOnly(it) }) { EmptyText(stringResource(R.string.f_MdEd_no_tags)) }

    val groups = keys.groupBy(::groupOf).toSortedMap(compareBy({ it in READ_ONLY_GROUPS }, { it }))
    groups.forEach { (group, groupKeys) ->
        val readOnlyGroup = group in READ_ONLY_GROUPS
        SectionTitle(
            if (readOnlyGroup) stringResource(R.string.f_MdEd_group_read_only, group)
            else group.ifEmpty { stringResource(R.string.f_MdEd_str_tag) }
        )
        groupKeys.sortedBy(::tagOf).forEach { key ->
            TagRow(key, original[key], staged[key], onEdit, onRemove, onRevert)
        }
    }
}

@Composable
private fun TagRow(
    key: String,
    originalValue: String?,
    value: String?,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRevert: (String) -> Unit,
) {
    val readOnly = isReadOnly(key)
    val removed = value == null
    val changed = originalValue != value
    val color = when {
        removed -> MaterialTheme.colorScheme.error
        changed -> MaterialTheme.colorScheme.primary
        readOnly -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }
    val decoration = if (removed) TextDecoration.LineThrough else TextDecoration.None

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !readOnly && !removed) { onEdit(key) }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tagOf(key),
                style = MaterialTheme.typography.labelMedium.copy(textDecoration = decoration),
                fontFamily = FontFamily.Monospace,
                color = if (changed) color else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value ?: originalValue.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = decoration),
                color = color,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            readOnly -> Spacer(modifier = Modifier.size(48.dp))
            // Changed or removed: back to how it was (a new tag just goes)
            changed && originalValue != null -> IconButton(onClick = { onRevert(key) }) {
                Icon(Icons.AutoMirrored.Default.Undo, contentDescription = stringResource(R.string.f_MdEd_restore), tint = color)
            }
            else -> IconButton(onClick = { onRemove(key) }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.f_MdEd_remove), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * A tag's value, and for a new one its group (optional: ExifTool then picks where it belongs)
 * and name. Read-only ones are refused.
 */
@Composable
private fun TagDialog(
    title: String,
    initialGroup: String,
    initialTag: String,
    initialValue: String,
    keyEditable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit,
) {
    var group by remember { mutableStateOf(initialGroup) }
    var tag by remember { mutableStateOf(initialTag) }
    var value by remember { mutableStateOf(initialValue) }
    val key = if (group.isBlank()) tag.trim() else "${group.trim()}:${tag.trim()}"
    val readOnly = tag.isNotBlank() && isReadOnly(key)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (keyEditable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InputField(
                            value = group,
                            onValueChange = { group = it },
                            label = { Text(stringResource(R.string.f_MdEd_str_group)) },
                            placeholder = { Text("EXIF") },
                            singleLine = true,
                            modifier = Modifier.weight(0.4f)
                        )
                        InputField(
                            value = tag,
                            onValueChange = { tag = it },
                            label = { Text(stringResource(R.string.f_MdEd_str_tag)) },
                            placeholder = { Text("Artist") },
                            singleLine = true,
                            isError = readOnly,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                    Reveal(visible = readOnly) {
                        Text(stringResource(R.string.f_MdEd_str_readonly), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(key, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                InputField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.f_MdEd_str_value)) },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(key, value) }, enabled = tag.isNotBlank() && !readOnly) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

/**
 * What was written up top, and at the bottom (as every tool's buttons) where the edited copy goes:
 * over the original, to a new file, or shared; or another file.
 */
@Composable
private fun ExportStep(file: Opened, written: Int, steps: Steps, onBack: () -> Unit, onOpenAnother: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    var replaced by remember(file, written) { mutableStateOf(false) }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(file.mimeType)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val ok = copyTo(context, file.copy, uri)
            // A failure, told in the one place errors are: the bottom bar
            if (ok) Toast.makeText(context, resources.getString(R.string.f_MdEd_saved), Toast.LENGTH_SHORT).show()
            else LinuxTasks.reportFailure(MetadataEditorFeature, file.name, "Couldn't save the file")
        }
    }

    StepWithButtons(content = { PageHeading(steps = steps, title = stringResource(AppR.string.fp_MdEd), onBack = onBack) {
        FileName(file.name)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Icon(
            if (written > 0) Icons.Default.CheckCircle else Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (written > 0) pluralStringResource(R.plurals.f_MdEd_written, written, written) else stringResource(R.string.f_MdEd_no_changes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(if (written > 0) R.string.f_MdEd_export_note else R.string.f_MdEd_export_note_unchanged),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        }
    } }, buttons = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(Icons.Default.Save, stringResource(R.string.f_MdEd_replace), enabled = written > 0 && !replaced) {
                coroutineScope.launch {
                    replaced = copyTo(context, file.copy, file.uri)
                    if (replaced) {
                        Toast.makeText(context, resources.getString(R.string.f_MdEd_saved), Toast.LENGTH_SHORT).show()
                    } else {
                        // E.g. a shared file, which may only be read: a new one instead
                        Toast.makeText(context, resources.getString(R.string.f_MdEd_str_save_fail_fallback), Toast.LENGTH_LONG).show()
                        saveAs.launch(file.name)
                    }
                }
            }
            ActionButton(Icons.Default.SaveAs, stringResource(R.string.f_MdEd_str_save_as)) { saveAs.launch(file.name) }
            ActionButton(Icons.Default.Share, stringResource(R.string.f_MdEd_share)) { share(context, file) }
        }
        TextButton(onClick = onOpenAnother, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.f_MdEd_open_another)) }
    })
}

/** All [file]'s tags (group:tag) as ExifTool reads them, without binary data; null if it can't. */
private suspend fun readTags(context: Context, file: File): Map<String, String>? {
    val result = LinuxTasks.run(context, listOf("exiftool", "-j", "-a", "-G", file.absolutePath), MetadataEditorFeature)
    val out = result.stdout
    val start = out.indexOf('[')
    val end = out.lastIndexOf(']')
    if (start == -1 || end <= start) return null
    val json = runCatching { JSONArray(out.substring(start, end + 1)).getJSONObject(0) }.getOrNull() ?: return null
    return json.keys().asSequence()
        // The copy's path in the cache, not the file's
        .filter { it != "SourceFile" }
        .associateWith { json.optString(it) }
        .filterValues { !it.startsWith("(Binary data") }
}

/** [uri]'s file name, with an extension from its type when it has none (ExifTool needs one). */
private fun displayName(context: Context, uri: Uri): String {
    var name = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    } else uri.lastPathSegment
    if (name.isNullOrBlank()) name = "file"
    if ('.' !in name) {
        val ext = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (ext != null) name = "$name.$ext"
    }
    return name
}

/** Writes [file] over [target]; false if it can't be written (e.g. a read-only share). */
private suspend fun copyTo(context: Context, file: File, target: Uri) = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(target, "wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
    }.isSuccess
}

/** Shares the edited copy under the file's own name (not to this editor again). */
private fun share(context: Context, file: Opened) {
    val dir = File(MetadataEditorFeature.cacheDir(context), "share").apply { deleteRecursively(); mkdirs() }
    val named = file.copy.copyTo(File(dir, file.name), overwrite = true)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", named)
    val send = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        Intent.createChooser(send, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, "share.Metadata.Edit")))
    )
}
