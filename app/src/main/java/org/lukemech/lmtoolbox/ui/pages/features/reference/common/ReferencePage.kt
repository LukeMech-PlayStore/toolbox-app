package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.Sheet
import org.lukemech.lmtoolbox.features.reference.common.TableSheet
import org.lukemech.lmtoolbox.features.reference.common.ToolSheet
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SearchField
import org.lukemech.lmtoolbox.ui.components.edgeFade
import org.lukemech.lmtoolbox.ui.components.fadeEnter
import org.lukemech.lmtoolbox.ui.components.fadeExit

/**
 * A reference tool's page: its [sheets] picked by chips at the top (when there are several), the search
 * through a table's rows or a pinout's pins under them, and a table's headings staying put as its rows scroll.
 * [action]: at the end of the title's row (a calculator's formulas).
 */
@Composable
fun ReferencePage(title: String, sheets: List<Sheet>, action: (@Composable () -> Unit)? = null) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val sheet = sheets[index.coerceIn(sheets.indices)]
    val resources = LocalResources.current
    // A table's columns, as wide as their widest cell, and its sideways scroll: its headings' and its rows'
    val table = (sheet as? TableSheet)?.let { rememberTableLayout(it) }
    // Each table's as it was last laid out: the one going out of sight fades with its own
    val layouts = remember { LinkedHashMap<TableSheet, TableLayout>() }
    table?.let {
        layouts.remove(it.sheet)
        layouts[it.sheet] = it
    }

    PageHeading(
        title = title,
        action = action,
        header = {
            if (sheets.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                ) {
                    sheets.forEachIndexed { i, it ->
                        FilterChip(selected = i == index, onClick = { index = i }, label = { Text(it.name.resolve(resources)) })
                    }
                }
            }
            Reveal(sheet !is ToolSheet) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SearchField(
                        query, { query = it },
                        stringResource(if (sheet is PinoutSheet) R.string.rf_search_pins else R.string.rf_search_table)
                    )
                }
            }
            // The last table's notes and headings fold away with it when a pinout's picked
            Reveal(table != null) {
                (table ?: layouts.values.lastOrNull())?.let {
                    Column {
                        TableNotes(it, Modifier.padding(top = 4.dp))
                        TableHeadings(it, Modifier.padding(top = 4.dp))
                    }
                }
            }
            // Under a search or a pinout's, but none under a table's headings: its rows start right there
            val gap by animateDpAsState(if (table != null) 0.dp else 8.dp, label = "headerGap")
            Spacer(Modifier.height(gap))
        },
        // Each sheet scrolls itself: a table's a lazy list, only its rows in sight built
        scrolls = false
    ) {
        AnimatedContent(
            targetState = sheet,
            transitionSpec = { fadeEnter togetherWith fadeExit },
            label = "sheet",
            modifier = Modifier.fillMaxSize()
        ) { shown ->
            when (shown) {
                is TableSheet -> layouts[shown]?.let { TableRows(it, query) }
                is PinoutSheet -> ScrollingSheet { PinoutView(shown, query) }
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
