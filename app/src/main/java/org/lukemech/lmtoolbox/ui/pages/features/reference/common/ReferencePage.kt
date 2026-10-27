package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.Sheet
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.ToolSheet
import org.lukemech.lmtoolbox.ui.components.Fade
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.ROW_SPACING
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SearchField
import org.lukemech.lmtoolbox.ui.components.edgeFade
import org.lukemech.lmtoolbox.ui.components.fadeEnter
import org.lukemech.lmtoolbox.ui.components.fadeExit

/**
 * A reference tool's page: its [sheets] picked at the top (when there are several: a [SheetPicker], [icon] its
 * icon), the search through a table's rows or a pinout's pins under it, and a table's headings staying put as its
 * rows scroll. [action]: at the end of the title's row (a calculator's formulas).
 */
@Composable
fun ReferencePage(
    title: String,
    sheets: List<Sheet>,
    icon: ImageVector = Icons.Default.Layers,
    action: (@Composable () -> Unit)? = null
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val sheet = sheets[index.coerceIn(sheets.indices)]
    // A table's columns, as wide as their widest cell, and its sideways scroll: its headings' and its rows'
    val table = (sheet as? TableSheet)?.let { rememberTableLayout(it) }
    // Each table's as it was last laid out: the one going out of sight fades with its own
    val layouts = remember { LinkedHashMap<TableSheet, TableLayout>() }
    table?.let {
        layouts.remove(it.sheet)
        layouts[it.sheet] = it
    }

    // The sheets' list, open: it folds away as the page under it is used (a pin tapped, the search)
    var picking by rememberSaveable { mutableStateOf(false) }

    PageHeading(
        title = title,
        // A pinout or a table to export as a PDF, before the page's own action (a calculator's formulas)
        action = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The last one there was, as it fades away for a calculator's page
                val printable = remember { arrayOfNulls<Sheet>(1) }
                if (sheet !is ToolSheet) printable[0] = sheet
                Fade(sheet !is ToolSheet) { printable[0]?.let { PdfExportAction(title, it) } }
                action?.invoke()
            }
        },
        header = {
            if (sheets.size > 1) {
                SheetPicker(sheets, sheets.indexOf(sheet), icon, picking, { picking = it }) { index = it }
            }
            Reveal(sheet !is ToolSheet) {
                // As far from what's over and under it as the home page's search
                Column {
                    if (sheets.size > 1) Spacer(Modifier.height(12.dp))
                    SearchField(
                        query, { query = it; picking = false },
                        stringResource(if (sheet is PinoutSheet) R.string.rf_search_pins else R.string.rf_search_table),
                        modifier = Modifier.onFocusChanged { if (it.isFocused) picking = false }
                    )
                }
            }
            // The last table's notes and headings fold away with it when a pinout's picked
            Reveal(table != null) {
                (table ?: layouts.values.lastOrNull())?.let {
                    Column {
                        TableNotes(it, Modifier.padding(top = 8.dp))
                        TableHeadings(it, Modifier.padding(top = 4.dp))
                    }
                }
            }
            // Under a search as on the home page, none under a table's headings (its rows start right there), and
            // under the picker alone (a calculator's page) as rows are apart: it's one of its rows
            val gap by animateDpAsState(
                when {
                    table != null -> 0.dp
                    sheet is ToolSheet -> ROW_SPACING
                    else -> 16.dp
                },
                label = "headerGap"
            )
            Spacer(Modifier.height(gap))
        },
        // Each sheet scrolls itself: a table's a lazy list, only its rows in sight built
        scrolls = false
    ) {
        AnimatedContent(
            targetState = sheet,
            transitionSpec = { fadeEnter togetherWith fadeExit },
            label = "sheet",
            modifier = Modifier
                .fillMaxSize()
                // Any touch on the sheet folds the list away; the touch goes on to what's under it
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        picking = false
                    }
                }
        ) { shown ->
            when (shown) {
                is TableSheet -> layouts[shown]?.let { TableRows(it, query) }
                is PinoutSheet -> PinoutView(shown, query)
                is ToolSheet -> ScrollingSheet { Column(verticalArrangement = RowArrangement) { shown.content() } }
            }
        }
    }
}

/** A sheet that isn't a table, scrolling as a page's content does ([PageHeading]'s). */
@Composable
private fun ScrollingSheet(content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().edgeFade(scroll).verticalScroll(scroll).padding(bottom = 16.dp)) { content() }
}
