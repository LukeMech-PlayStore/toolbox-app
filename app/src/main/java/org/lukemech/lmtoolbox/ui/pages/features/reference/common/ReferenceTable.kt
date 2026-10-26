package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

/** The widest a column gets: a longer cell (a description) wraps. */
private val MAX_COLUMN = 240.dp

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
            val widest = (listOf(measurer.measure(headings[col], headingStyle).size.width) +
                texts.map { row -> row.getOrNull(col)?.let { measurer.measure(it, cellStyle).size.width } ?: 0 }).max()
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
            .horizontalScroll(table.scroll)
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
 * A table's rows, those with [query] in a cell (any case); rows come and go as it's typed. A cell held down is
 * copied.
 */
@Composable
fun TableRows(table: TableLayout, query: String) {
    val copy = rememberCopy()
    val q = query.trim()
    val matches = table.texts.map { row -> q.isEmpty() || row.any { it.contains(q, ignoreCase = true) } }
    val stripe = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .horizontalScroll(table.scroll)
    ) {
        table.sheet.rows.forEachIndexed { r, row ->
            Reveal(matches[r]) {
                Row(modifier = Modifier.background(if (r % 2 == 1) stripe else Color.Transparent)) {
                    row.forEachIndexed { col, cell ->
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
        }
    }
    Reveal(matches.none { it }) { EmptyText(stringResource(R.string.search_no_results)) }
    Spacer(Modifier.height(4.dp))
    table.sheet.note?.let { Note(it.resolve(LocalResources.current)) }
    Note(stringResource(R.string.rf_copy_hint))
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
        { text ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { clipboard.setClipEntry(ClipData.newPlainText(text, text).toClipEntry()) }
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied.format(text), Toast.LENGTH_SHORT).show()
        }
    }
}
