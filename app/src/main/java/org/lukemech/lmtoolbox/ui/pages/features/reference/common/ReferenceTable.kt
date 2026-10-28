package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.Txt
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.edgeFade
import org.lukemech.lmtoolbox.ui.components.fadeEnter
import org.lukemech.lmtoolbox.ui.components.fadeExit

/** The widest a column gets: a longer cell (a description) wraps. */
private val MAX_COLUMN = 240.dp

/** How many of a column's longest texts are measured for its width. */
private const val MEASURED_PER_COLUMN = 8

private val CELL_PADDING_H = 8.dp
private val CELL_PADDING_V = 6.dp
private val SWATCH = 14.dp

/**
 * A table as it's laid out: its [headings] and cells ([texts]) in the app's language, each column's
 * [widths], and the sideways [scroll] its headings and rows share.
 */
class TableLayout(val sheet: TableSheet, val headings: List<String>, val texts: List<List<String>>, val widths: List<Dp>, val scroll: ScrollState)

/**
 * [sheet] laid out: each column as wide as its widest cell (up to [MAX_COLUMN]), all of them widened alike
 * to the page's width when they'd be narrower.
 */
@Composable
fun rememberTableLayout(sheet: TableSheet): TableLayout {
    val resources = LocalResources.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellStyle = MaterialTheme.typography.bodyMedium
    val headingStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val pageWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() } - 32.dp
    return remember(sheet, resources.configuration, pageWidth) {
        val headings = sheet.columns.map { it.resolve(resources) }
        val texts = sheet.rows.map { row -> row.map { it.resolve(resources) } }
        val natural = sheet.columns.indices.map { col ->
            val swatch = sheet.rows.any { (it.getOrNull(col) as? Txt.Swatch) != null }
            // Only the longest few measured, not every cell (ASCII's 128 rows): a text's width goes with its length,
            // and a few of them make up for narrow and wide letters
            val longest = texts.mapNotNull { it.getOrNull(col) }.distinct().sortedByDescending { it.length }.take(MEASURED_PER_COLUMN)
            val widest = (listOf(measurer.measure(headings[col], headingStyle).size.width) +
                longest.map { measurer.measure(it, cellStyle).size.width }).max()
            with(density) { widest.toDp() }.coerceAtMost(MAX_COLUMN) + CELL_PADDING_H * 2 + (if (swatch) SWATCH + 6.dp else 0.dp) + 1.dp
        }
        val total = natural.fold(0.dp) { sum, w -> sum + w }
        val widths = if (total < pageWidth) natural.map { it * (pageWidth / total) } else natural
        TableLayout(sheet, headings, texts, widths, ScrollState(0))
    }
}

/** A table's headings, over its rows ([TableRows]), scrolling sideways with them. */
@Composable
fun TableHeadings(table: TableLayout, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .horizontalScroll(table.scroll, overscrollEffect = null)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        table.headings.forEachIndexed { col, heading ->
            Text(
                heading,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.width(table.widths[col]).padding(horizontal = CELL_PADDING_H, vertical = 8.dp)
            )
        }
    }
}

/**
 * A table's rows, those with [query] in a cell (any case), filling the page and scrolling: only the rows in
 * sight are built (ASCII's 128 aren't, all at once). Rows come and go as it's typed; a cell held down is copied.
 */
@Composable
fun TableRows(table: TableLayout, query: String) {
    val copy = rememberCopy()
    val q = query.trim()
    val shown = remember(table, q) {
        table.texts.indices.filter { r -> q.isEmpty() || table.texts[r].any { it.contains(q, ignoreCase = true) } }
    }
    val stripe = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val list = rememberLazyListState()

    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize().edgeFade(list)) {
        items(shown, key = { it }) { r ->
            // Each row scrolls sideways with the others and the headings (one state); the last one's corners round the table off
            Row(
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = 16.dp)
                    .clip(if (r == shown.last()) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp) else RectangleShape)
                    .horizontalScroll(table.scroll, overscrollEffect = null)
                    .background(if (r % 2 == 1) stripe else Color.Transparent)
            ) {
                table.sheet.rows[r].forEachIndexed { col, cell ->
                    val text = table.texts[r][col]
                    Box(
                        modifier = Modifier
                            .width(table.widths.getOrElse(col) { MAX_COLUMN })
                            .pointerInput(text) { detectTapGestures(onLongPress = { copy(text) }) }
                            .semantics { onLongClick { copy(text); true } }
                            .padding(horizontal = CELL_PADDING_H, vertical = CELL_PADDING_V)
                    ) {
                        Cell(cell, text)
                    }
                }
            }
        }
        item(key = "empty") { Reveal(shown.isEmpty()) { EmptyText(stringResource(R.string.search_no_results)) } }
    }
}

/** What's said about a table ([TableSheet.note]) and how to copy its cells: over it, seen as it opens. */
@Composable
fun TableNotes(table: TableLayout, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    Column(modifier) {
        AnimatedContent(targetState = table.sheet.note, transitionSpec = { fadeEnter togetherWith fadeExit }, label = "note") { note ->
            note?.let { Note(it.resolve(resources)) }
        }
        Note(stringResource(R.string.rf_copy_hint))
    }
}

/** A cell's [text], after its swatch if it has one. */
@Composable
private fun Cell(cell: Txt, text: String) {
    if (cell !is Txt.Swatch) return Text(text, style = MaterialTheme.typography.bodyMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Swatch(cell.colors)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A round swatch of [colors]: one, or a base and its stripe (a wire's) as halves. */
@Composable
fun Swatch(colors: List<Color>, size: Dp = SWATCH) {
    val fill = if (colors.size > 1) {
        Brush.horizontalGradient(0f to colors[0], 0.5f to colors[0], 0.5f to colors[1], 1f to colors[1])
    } else Brush.linearGradient(listOf(colors.first(), colors.first()))
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

/**
 * Copying a cell's text: to the clipboard, a buzz, and on Android before 13 (which says so itself) a
 * toast of what was copied.
 */
@Composable
fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.rf_copied)
    return remember(clipboard, context) {
        copy@{ text ->
            // An empty cell (ASCII's names past the controls): nothing to copy
            if (text.isBlank()) return@copy
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { clipboard.setClipEntry(ClipData.newPlainText(text, text).toClipEntry()) }
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied.format(text), Toast.LENGTH_SHORT).show()
        }
    }
}
