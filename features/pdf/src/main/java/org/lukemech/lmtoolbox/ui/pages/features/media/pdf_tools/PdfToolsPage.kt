package org.lukemech.lmtoolbox.ui.pages.features.media.pdf_tools

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R as AppR
import org.lukemech.lmtoolbox.features.media.pdf_tools.ImageType
import org.lukemech.lmtoolbox.features.media.pdf_tools.PageRanges
import org.lukemech.lmtoolbox.features.media.pdf_tools.PageRef
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfFile
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfJobs
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfOptions
import org.lukemech.lmtoolbox.features.media.pdf_tools.PdfOutput
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
import org.lukemech.lmtoolbox.ui.components.Fade
import org.lukemech.lmtoolbox.ui.components.FieldArrangement
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.RowColors
import org.lukemech.lmtoolbox.ui.components.SecondaryButton
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.ui.components.edgeFade
import org.lukemech.lmtoolbox.ui.helpers.rememberPdfPage

private enum class Step { Files, Pages, Output, Result }

/** Its own "Share to" entry (the manifest's alias): what's made isn't shared back to it. */
private const val SHARE_ALIAS = "share.Pdf.Tools"

/** A page in the grid as wide as this at least (as many in a row as fit). */
private val PAGE_WIDTH = 104.dp

/** A page's picture's shape in the grid, width to height: about A4's. */
private const val PAGE_ASPECT = 0.72f

/**
 * PDF tools in four steps, like the metadata editor and the video encoder: the intro, the pages (of PDFs and pictures opened: copied in, protected
 * ones opened with their passwords), arranged in a grid as pdfarranger's (moved, turned, copied, deleted); what's
 * made of them (one PDF, compressed or protected; a split; pictures); and the work, which goes on in the background
 * when the page is left (see [PdfJobs]). [providedInput]: PDFs or pictures shared in, a URI a line.
 */
@Composable
fun PdfToolsPage(providedInput: String? = "") {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val job by PdfJobs.state.collectAsState()

    // Back on the page while something's being made: straight to it; else the intro, the first step as the metadata
    // editor's (the pages open a tap away), the grid of them the second once they're added or gone back to
    var step by rememberSaveable {
        mutableStateOf(if (PdfJobs.state.value?.isActive == true) Step.Result else Step.Files)
    }
    // What's open, how it's arranged and what's picked for it is PdfJobs': it stays when the page is left or redrawn,
    // what's being made goes on meanwhile (a task). A name not typed follows the output
    val files = PdfJobs.files
    var pages by PdfJobs::pages
    var selected by PdfJobs::selected
    var typedName by PdfJobs::fileName
    var options by PdfJobs::options
    var busy by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf<PdfFile?>(null) }

    // Only the selected ones while there are some
    val o = options.copy(selectedOnly = options.selectedOnly && selected.isNotEmpty())
    val made = PdfJobs.pagesFor(o)
    val suggestedName = files.firstOrNull()?.let { "${it.baseName}-${stringResource(suffixOf(o, files.size))}" }.orEmpty()
    val fileName = typedName ?: suggestedName

    /** Whether the open files may be closed: not while a job reads their copies (its card a tap away). */
    fun canChange(): Boolean {
        if (!PdfJobs.isBusy()) return true
        // Not an error: something's being made
        Toast.makeText(context, R.string.f_Pdf_busy_open, Toast.LENGTH_SHORT).show()
        return false
    }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        coroutineScope.launch {
            busy = true
            val opened = PdfJobs.open(context, uris)
            busy = false
            // None read: the bottom bar tells (see PdfJobs.open)
            if (opened.isEmpty()) return@launch
            if (files.isEmpty()) typedName = null
            PdfJobs.add(opened)
            PdfJobs.clear()
            step = Step.Pages
            // The first protected one asks for its password right away
            opened.firstOrNull { it.needsPassword }?.let { unlocking = it }
        }
    }

    /** The pages changed by [change], the selection what's left of it, the files none are of anymore closed. */
    fun edit(change: (List<PageRef>) -> List<PageRef>) {
        pages = change(pages)
        val keys = pages.mapTo(HashSet()) { it.key }
        selected = selected.filterTo(HashSet()) { it in keys }
        PdfJobs.prune()
    }

    fun start() {
        focusManager.clearFocus()
        if (made.isEmpty()) return
        PdfJobs.start(context, o, fileName)
        step = Step.Result
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> add(uris) }
    fun pick() = picker.launch(arrayOf(PdfJobs.MIME_TYPE, "image/*"))

    // Shared to the tools: straight to the pages
    LaunchedEffect(providedInput) {
        if (!providedInput.isNullOrBlank()) add(providedInput.lines().filter { it.isNotBlank() }.map { it.trim().toUri() })
    }

    // Left while working: it goes on, shown in the bottom bar
    DisposableEffect(Unit) {
        onDispose { PdfJobs.state.value?.takeIf { it.isActive }?.taskId?.let(AppTasks::detach) }
    }

    // Every file closed (cleared, or each locked one taken off): back to the intro, the step bar's first
    LaunchedEffect(files.isEmpty()) {
        if (files.isEmpty() && (step == Step.Pages || step == Step.Output)) step = Step.Files
    }

    BackHandler(enabled = step != Step.Files && !busy) {
        step = when (step) {
            Step.Result -> if (pages.isNotEmpty()) Step.Output else Step.Files
            Step.Output -> Step.Pages
            else -> Step.Files
        }
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
            when (step) {
                // What was made and how it went up top; what it takes, or what to do next, at the bottom
                Step.Result if current != null -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = if (pages.isNotEmpty()) Step.Output else Step.Files }) {
                            Summary(current.files, current.pages.size)
                            Spacer(modifier = Modifier.height(24.dp))
                            JobResult(current)
                        }
                    },
                    buttons = {
                        JobButtons(
                            current,
                            onAnother = {
                                PdfJobs.clear()
                                if (canChange()) PdfJobs.closeAll()
                                step = Step.Files
                            },
                            // The same pages again, arranged or made another way: from their grid
                            onChange = {
                                PdfJobs.clear()
                                options = current.options
                                step = if (pages.isNotEmpty()) Step.Pages else Step.Files
                            }
                        )
                    }
                )

                Step.Output if pages.isNotEmpty() -> StepWithButtons(
                    content = {
                        PageHeading(steps = steps, title = title, onBack = { step = Step.Pages }) {
                            Summary(files, made.size)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutputForm(pages.size, selected.size, options, { options = it }, fileName) { typedName = it }
                        }
                    },
                    buttons = {
                        ErrorText(stringResource(R.string.f_Pdf_busy).takeIf { current?.isActive == true })
                        BigButton(
                            iconOf(o.output), stringResource(R.string.f_Pdf_make),
                            enabled = current?.isActive != true && fileName.isNotBlank() && o.isReady(made.size)
                        ) { start() }
                    }
                )

                Step.Pages if files.isNotEmpty() -> StepWithButtons(
                    content = {
                        PageHeading(
                            steps = steps, title = title, onBack = { step = Step.Files }, scrolls = false,
                            action = { ClearAction { if (canChange()) PdfJobs.closeAll() } },
                            header = {
                                PageToolbar(
                                    selected = selected.size,
                                    all = pages.size,
                                    enabled = !busy,
                                    onTurn = { angle -> edit { all -> all.map { if (it.key in selected) it.turned(angle) else it } } },
                                    onDuplicate = { edit { all -> all.flatMap { if (it.key in selected) listOf(it, PdfJobs.copyOf(it)) else listOf(it) } } },
                                    onDelete = { if (canChange()) edit { all -> all.filter { it.key !in selected } } },
                                    onSelectAll = { selected = if (selected.size == pages.size) emptySet() else pages.mapTo(HashSet()) { it.key } }
                                )
                            }
                        ) {
                            PageGrid(
                                files, pages, selected,
                                onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
                                onMove = { from, to -> pages = pages.toMutableList().apply { add(to, removeAt(from)) } },
                                onUnlock = { unlocking = it },
                                onRemove = { if (canChange()) PdfJobs.remove(it) }
                            )
                        }
                    },
                    buttons = {
                        // More pages, as "Back to" is on the intro: over the step's own button
                        SecondaryButton(stringResource(R.string.f_Pdf_add), !busy) { pick() }
                        BigButton(
                            Icons.AutoMirrored.Default.ArrowForward, stringResource(R.string.f_Pdf_next),
                            enabled = !busy && pages.isNotEmpty()
                        ) { step = Step.Output }
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
                        // The pages open stay a tap away, else the last work
                        when {
                            files.isNotEmpty() -> SecondaryButton(
                                stringResource(R.string.f_Pdf_back_to, files.singleOrNull()?.name ?: "${files.first().name} +${files.size - 1}"),
                                !busy
                            ) { step = Step.Pages }
                            current != null -> SecondaryButton(stringResource(R.string.f_Pdf_back_to, current.fileName), !busy) { step = Step.Result }
                        }
                        BigButton(Icons.Default.FolderOpen, stringResource(R.string.f_Pdf_pick), enabled = !busy) { pick() }
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
                PdfJobs.unlocked(opened)
                // The next one still locked, if there's one
                unlocking = PdfJobs.files.firstOrNull { it.needsPassword && it.id != opened.id }
            }
        )
    }
}

/** The files: their names, how many, the [pages] made of them and their size, in a line under the title. */
@Composable
private fun Summary(files: List<PdfFile>, pages: Int) {
    val context = LocalContext.current
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
 * Over the grid: what's done to the [selected] pages (of [all]), how many there are first: turned either way,
 * duplicated, deleted; all of them selected, or none.
 */
@Composable
private fun PageToolbar(
    selected: Int,
    all: Int,
    enabled: Boolean,
    onTurn: (angle: Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val some = enabled && selected > 0
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(
            if (selected > 0) pluralStringResource(R.plurals.f_Pdf_selected, selected, selected) else "",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        IconButton(onClick = { onTurn(-90) }, enabled = some) {
            Icon(Icons.Default.Rotate90DegreesCcw, contentDescription = stringResource(R.string.f_Pdf_turn_left))
        }
        IconButton(onClick = { onTurn(90) }, enabled = some) {
            Icon(Icons.Default.Rotate90DegreesCw, contentDescription = stringResource(R.string.f_Pdf_turn_right))
        }
        IconButton(onClick = onDuplicate, enabled = some) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.f_Pdf_duplicate))
        }
        IconButton(onClick = onDelete, enabled = some) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.f_Pdf_delete))
        }
        IconButton(onClick = onSelectAll, enabled = enabled && all > 0) {
            val none = all > 0 && selected == all
            Icon(
                if (none) Icons.Default.Deselect else Icons.Default.SelectAll,
                contentDescription = stringResource(if (none) R.string.f_Pdf_select_none else R.string.f_Pdf_select_all)
            )
        }
    }
}

/**
 * The [pages] of the [files] as a grid of their pictures, in their order: a tap selects one (or not), one held is
 * dragged to its new place ([onMove]: from, to), the grid scrolling at its edges. A file still to be opened with its
 * password is a card of its own after them ([onUnlock], [onRemove]). Pages come, go and move places as they change.
 */
@Composable
private fun PageGrid(
    files: List<PdfFile>,
    pages: List<PageRef>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onUnlock: (PdfFile) -> Unit,
    onRemove: (PdfFile) -> Unit,
) {
    val grid = rememberLazyGridState()
    val byId = files.associateBy { it.id }
    val edge = with(LocalDensity.current) { 48.dp.toPx() }
    // The page being dragged, and how far from its place
    var dragged by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val currentPages by rememberUpdatedState(pages)
    val currentMove by rememberUpdatedState(onMove)

    fun itemAt(position: Offset): LazyGridItemInfo? = grid.layoutInfo.visibleItemsInfo.firstOrNull {
        it.key is Long && position.x.toInt() in it.offset.x..(it.offset.x + it.size.width) &&
            position.y.toInt() in it.offset.y..(it.offset.y + it.size.height)
    }

    /**
     * Page [key], held at [grab] in it, dragged to [finger] (both in its place in the grid): drawn under the finger,
     * the grid scrolling on at its top or bottom edge, it takes the place of the page it's over.
     */
    fun drag(key: Long, grab: Offset, finger: Offset) {
        offset = finger - grab
        val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val inGrid = info.offset.y + finger.y
        val scroll = when {
            inGrid < edge -> -edge / 4
            inGrid > grid.layoutInfo.viewportSize.height - edge -> edge / 4
            else -> 0f
        }
        if (scroll != 0f) grid.dispatchRawDelta(scroll)
        val center = Offset(info.offset.x + info.size.width / 2f, info.offset.y + info.size.height / 2f) + offset
        val target = itemAt(center)?.takeIf { it.key != key } ?: return
        val from = currentPages.indexOfFirst { it.key == key }
        val to = currentPages.indexOfFirst { it.key == target.key }
        if (from < 0 || to < 0) return
        // The first one seen moved: the grid keeps where it is, not following it
        if (grid.firstVisibleItemIndex in setOf(from, to)) {
            grid.requestScrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
        }
        currentMove(from, to)
        // Its place now the target's: still under the finger till the next move says where it is
        offset += Offset((info.offset.x - target.offset.x).toFloat(), (info.offset.y - target.offset.y).toFloat())
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(PAGE_WIDTH),
        state = grid,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().edgeFade(grid)
    ) {
        itemsIndexed(pages, key = { _, page -> page.key }) { index, page ->
            val file = byId[page.file] ?: return@itemsIndexed
            val moving = dragged == page.key
            // Held on the page itself (before the grid's scrolling takes the drag), in its place in the grid: first,
            // so it's the same gesture as the page moves
            val hold = Modifier.pointerInput(page.key) {
                var grab = Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        grab = at
                        offset = Offset.Zero
                        dragged = page.key
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        drag(page.key, grab, change.position)
                    },
                    onDragEnd = { dragged = null },
                    onDragCancel = { dragged = null }
                )
            }
            PageCard(
                file, page, index + 1, page.key in selected, several = files.size > 1,
                modifier = hold.then(
                    if (moving) {
                        Modifier.zIndex(1f).offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }.graphicsLayer { scaleX = 1.05f; scaleY = 1.05f }
                    } else {
                        Modifier.animateItem()
                    }
                )
            ) { onToggle(page.key) }
        }
        items(files.filter { it.needsPassword }, key = { "locked-${it.id}" }) { file ->
            LockedCard(file, Modifier.animateItem(), onUnlock = { onUnlock(file) }, onRemove = { onRemove(file) })
        }
        item(key = "notes", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.animateItem()) {
                Note(stringResource(R.string.f_Pdf_grid_note), inset = false)
                Note(stringResource(R.string.f_Pdf_password_note), visible = files.any { it.password != null }, inset = false)
            }
        }
    }
}

/**
 * Page [number] of the grid: its picture (page [page] of [file], turned as it'll be made), its number under it and,
 * of [several] files, which one it's of; [selected] marked as a row is ([RowColors.highlighted]).
 */
@Composable
private fun PageCard(
    file: PdfFile,
    page: PageRef,
    number: Int,
    selected: Boolean,
    several: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = if (selected) RowColors.highlighted else RowColors.default
    // Turned the shorter way round, not back through 0 (270 to 0 is a quarter turn on)
    var turn by remember { mutableFloatStateOf(page.rotation.toFloat()) }
    LaunchedEffect(page.rotation) {
        val delta = ((page.rotation - turn) % 360 + 540) % 360 - 180
        turn += delta
    }
    val angle by animateFloatAsState(turn, label = "turn")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().aspectRatio(PAGE_ASPECT)) {
            Crossfade(rememberPdfPage(file.copy, page.index), label = "page") { image ->
                if (image != null) {
                    Image(
                        image, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            rotationZ = angle
                            // On its side, as wide as the card: a turned page fits in it
                            val sideways = kotlin.math.abs(kotlin.math.sin(Math.toRadians(angle.toDouble()))).toFloat()
                            val scale = 1f - (1f - PAGE_ASPECT) * sideways
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            Fade(visible = selected, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("$number", style = MaterialTheme.typography.labelLarge, color = colors.content)
        if (several) {
            Text(
                file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.MiddleEllipsis
            )
        }
    }
}

/** A protected [file] still to be opened: its name and lock in the error's color; tapped, its password asked. */
@Composable
private fun LockedCard(file: PdfFile, modifier: Modifier, onUnlock: () -> Unit, onRemove: () -> Unit) {
    val colors = RowColors.alert
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
            .clickable(onClick = onUnlock)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().aspectRatio(PAGE_ASPECT).padding(8.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = colors.content, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(file.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.f_Pdf_locked), style = MaterialTheme.typography.bodySmall, color = colors.content,
                textAlign = TextAlign.Center
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.f_Pdf_remove))
        }
    }
}

/**
 * What's made of the pages ([all] of them, [selected] picked in the grid): which (all or the selected), one PDF
 * (compressed, with a password), a split, or pictures, each one's own choices under it; then the new file's name and
 * where it goes (as in the settings).
 */
@Composable
private fun OutputForm(
    all: Int,
    selected: Int,
    options: PdfOptions,
    onChange: (PdfOptions) -> Unit,
    fileName: String,
    onFileNameChange: (String) -> Unit,
) {
    // Each output's own choices are kept in [options] while another's picked: folding away, they show as they were
    val o = options.copy(selectedOnly = options.selectedOnly && selected > 0)
    val pageCount = if (o.selectedOnly) selected else all
    Column(verticalArrangement = FieldArrangement) {
        Column(verticalArrangement = FieldArrangement, modifier = Modifier.padding(horizontal = 16.dp)) {
            RevealSection(selected > 0) {
                ChipChoice(
                    stringResource(R.string.f_Pdf_pages),
                    listOf(
                        false to stringResource(R.string.f_Pdf_scope_all, all),
                        true to stringResource(R.string.f_Pdf_scope_selected, selected),
                    ),
                    o.selectedOnly
                ) { onChange(options.copy(selectedOnly = it)) }
            }
            ChipGroup(stringResource(R.string.f_Pdf_output)) {
                PdfOutput.entries.forEach { output ->
                    FilterChip(
                        selected = output == o.output,
                        onClick = { onChange(options.copy(output = output)) },
                        label = { Text(stringResource(labelOf(output))) },
                        leadingIcon = { Icon(iconOf(output), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            RevealSection(o.output == PdfOutput.Pdf) {
                ChipChoice(
                    stringResource(R.string.f_Pdf_preset),
                    Preset.entries.map { it to stringResource(presetLabel(it)) },
                    o.preset,
                    note = stringResource(presetNote(o.preset))
                ) { onChange(options.copy(preset = it)) }
            }
            Reveal(o.output == PdfOutput.Split) {
                Column(verticalArrangement = FieldArrangement) {
                    ChipChoice(
                        null,
                        listOf(
                            SplitMode.Pages to stringResource(R.string.f_Pdf_split_pages),
                            SplitMode.Ranges to stringResource(R.string.f_Pdf_split_ranges),
                        ),
                        o.splitMode,
                        note = when (o.splitMode) {
                            SplitMode.Pages -> pluralStringResource(R.plurals.f_Pdf_split_pages_note, pageCount, pageCount)
                            SplitMode.Ranges -> stringResource(R.string.f_Pdf_split_ranges_note)
                        }
                    ) { onChange(options.copy(splitMode = it)) }
                    RevealSection(o.splitMode == SplitMode.Ranges) {
                        PagesField(o.ranges, pageCount) { onChange(options.copy(ranges = it)) }
                    }
                }
            }
            Reveal(o.output == PdfOutput.Images) {
                Column(verticalArrangement = FieldArrangement) {
                    ChipChoice(
                        stringResource(R.string.f_Pdf_image_type),
                        ImageType.entries.map { it to it.extension.uppercase() },
                        o.imageType,
                        note = stringResource(if (o.imageType == ImageType.Png) R.string.f_Pdf_png_note else R.string.f_Pdf_jpg_note)
                    ) { onChange(options.copy(imageType = it)) }
                    ChipChoice(
                        stringResource(R.string.f_Pdf_dpi),
                        PdfOptions.DPIS.map { it to stringResource(R.string.f_Pdf_dpi_value, it) },
                        o.dpi
                    ) { onChange(options.copy(dpi = it)) }
                }
            }
        }
        // A row as the settings' are (as wide as "Save to" under it), its password typed under it
        Reveal(o.output == PdfOutput.Pdf) {
            Column(verticalArrangement = FieldArrangement) {
                SwitchButton(stringResource(R.string.f_Pdf_protect), Icons.Default.Lock, o.protect) { onChange(options.copy(protect = it)) }
                Reveal(o.protect) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        PasswordField(o.password, stringResource(R.string.f_Pdf_password)) { onChange(options.copy(password = it)) }
                        Note(stringResource(R.string.f_Pdf_protect_note), inset = false)
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.f_Pdf_file_name), style = MaterialTheme.typography.labelLarge)
                InputField(
                    value = fileName,
                    onValueChange = onFileNameChange,
                    singleLine = true,
                    suffix = { Text(if (o.output == PdfOutput.Images) ".${o.imageType.extension}" else ".pdf") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                // A split's parts, pictures: the name and their pages
                Note(stringResource(R.string.f_Pdf_parts_note), visible = o.output != PdfOutput.Pdf, inset = false)
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
private fun PagesField(value: String, pageCount: Int, onChange: (String) -> Unit) {
    val wrong = value.isNotBlank() && PageRanges.parse(value, pageCount) == null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InputField(
            value = value,
            onValueChange = { onChange(PageRanges.filter(it).take(200)) },
            label = { Text(stringResource(R.string.f_Pdf_pages)) },
            placeholder = { Text("1-3,5,7-z") },
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
                // The size before (the files') and after (what was made, together), whatever it was
                if (done.sizeBefore > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(
                            R.string.f_Pdf_size_change,
                            Formatter.formatShortFileSize(context, done.sizeBefore), Formatter.formatShortFileSize(context, done.sizeAfter)
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
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
 * was made (open one, share them), or next (the same pages another way, other files). Each folds away as the
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
                // Several (a split's parts, pictures): shared together, opened from where they are
                if (saved.size == 1) {
                    ActionButton(Icons.AutoMirrored.Default.OpenInNew, stringResource(R.string.f_Pdf_open)) {
                        openFile(context, saved[0].uri, saved[0].mimeType)
                    }
                }
                ActionButton(Icons.Default.Share, stringResource(R.string.f_Pdf_share)) {
                    shareFiles(context, saved.map { it.uri }, saved[0].mimeType, SHARE_ALIAS)
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

private fun labelOf(output: PdfOutput) = when (output) {
    PdfOutput.Pdf -> R.string.f_Pdf_output_pdf
    PdfOutput.Split -> R.string.f_Pdf_output_split
    PdfOutput.Images -> R.string.f_Pdf_output_images
}

private fun iconOf(output: PdfOutput): ImageVector = when (output) {
    PdfOutput.Pdf -> Icons.Default.PictureAsPdf
    PdfOutput.Split -> Icons.AutoMirrored.Default.CallSplit
    PdfOutput.Images -> Icons.Default.Image
}

/** What a name not typed ends with, after the first file's: what was made of [files] of them. */
private fun suffixOf(o: PdfOptions, files: Int) = when (o.output) {
    PdfOutput.Pdf -> when {
        files > 1 -> R.string.f_Pdf_suffix_merged
        o.preset != Preset.AsIs -> R.string.f_Pdf_suffix_compressed
        o.protect -> R.string.f_Pdf_suffix_protected
        else -> R.string.f_Pdf_suffix_edited
    }
    PdfOutput.Split -> R.string.f_Pdf_suffix_part
    PdfOutput.Images -> R.string.f_Pdf_suffix_page
}

private fun presetLabel(preset: Preset) = when (preset) {
    Preset.AsIs -> R.string.f_Pdf_preset_as_is
    Preset.Lossless -> R.string.f_Pdf_preset_lossless
    Preset.Screen -> R.string.f_Pdf_preset_screen
    Preset.Ebook -> R.string.f_Pdf_preset_ebook
    Preset.Printer -> R.string.f_Pdf_preset_printer
    Preset.Prepress -> R.string.f_Pdf_preset_prepress
}

private fun presetNote(preset: Preset) = when (preset) {
    Preset.AsIs -> R.string.f_Pdf_preset_as_is_note
    Preset.Lossless -> R.string.f_Pdf_preset_lossless_note
    Preset.Screen -> R.string.f_Pdf_preset_screen_note
    Preset.Ebook -> R.string.f_Pdf_preset_ebook_note
    Preset.Printer -> R.string.f_Pdf_preset_printer_note
    Preset.Prepress -> R.string.f_Pdf_preset_prepress_note
}

/** The work's line under the step bar: what it's at, or that it failed. */
private fun captionOf(j: PdfJobs.Job) = when (j.phase) {
    PdfJobs.Phase.Failed -> R.string.f_Pdf_failed
    PdfJobs.Phase.Saving -> R.string.f_Pdf_saving
    PdfJobs.Phase.Starting -> R.string.f_Pdf_starting
    else -> when (j.options.output) {
        PdfOutput.Pdf -> when {
            j.options.preset != Preset.AsIs -> R.string.f_Pdf_compressing
            j.options.protect -> R.string.f_Pdf_protecting
            else -> R.string.f_Pdf_making
        }
        PdfOutput.Split -> R.string.f_Pdf_splitting
        PdfOutput.Images -> R.string.f_Pdf_drawing
    }
}
