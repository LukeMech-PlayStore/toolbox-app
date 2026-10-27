package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Sheet
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.LIST_MAX_HEIGHT
import org.lukemech.lmtoolbox.ui.components.SearchField
import org.lukemech.lmtoolbox.ui.components.edgeFade

/** From this many sheets on, their list has a search over it (a board by its name, its family, its chip). */
private const val SEARCH_FROM = 6

/** A line of the list sheets are picked from: a family's heading, or a sheet (by its place in the reference). */
private sealed interface Entry {
    data class Heading(val text: String) : Entry
    data class Item(val index: Int, val name: String) : Entry
}

/**
 * What a page's [sheets] are, as a row showing the one [selected]: its name, over it its family and which of them
 * it is ("Nano · 5 of 8"). Tapped, the sheets are listed under it to pick from ([onSelect]), each family under its
 * heading, found by their names, families and notes once there are [SEARCH_FROM] of them. The same row on every
 * page that has sheets to pick from: a reference's, a calculator's with a table.
 */
@Composable
fun SheetPicker(sheets: List<Sheet>, selected: Int, icon: ImageVector, onSelect: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val sheet = sheets[selected]

    fun close() {
        expanded = false
        query = ""
        focusManager.clearFocus()
    }

    FeatureButton(
        title = listOfNotNull(
            sheet.group?.resolve(resources),
            stringResource(R.string.rf_sheet_position, selected + 1, sheets.size)
        ).joinToString(" · "),
        subtitle = sheet.name.resolve(resources),
        titleSubtitleSwap = true,
        icon = icon,
        icon2 = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        onClick = { if (expanded) close() else expanded = true },
        expanded = expanded,
        popupContent = {
            val q = query.trim()
            fun matches(it: Sheet) = q.isEmpty() || listOfNotNull(it.name, it.group, it.note).any { txt -> txt.resolve(resources).contains(q, true) }
            // A family's heading over the first of its sheets in sight
            val entries = buildList {
                var group: String? = null
                sheets.forEachIndexed { i, it ->
                    if (!matches(it)) return@forEachIndexed
                    val family = it.group?.resolve(resources)
                    if (family != null && family != group) add(Entry.Heading(family))
                    group = family
                    add(Entry.Item(i, it.name.resolve(resources)))
                }
            }
            if (sheets.size >= SEARCH_FROM) {
                SearchField(query, { query = it }, stringResource(R.string.rf_search_sheets), modifier = Modifier.padding(bottom = 4.dp))
            }
            // Opened at the one shown, a couple of lines above it in sight (as an OptionSetting's choices)
            val scroll = rememberLazyListState(
                initialFirstVisibleItemIndex = (entries.indexOfFirst { it is Entry.Item && it.index == selected } - 2).coerceAtLeast(0)
            )
            LazyColumn(state = scroll, modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT).edgeFade(scroll)) {
                items(entries, key = { if (it is Entry.Item) it.index else "h:${(it as Entry.Heading).text}" }) { entry ->
                    when (entry) {
                        is Entry.Heading -> Text(
                            entry.text,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
                        )
                        is Entry.Item -> {
                            fun choose() {
                                onSelect(entry.index)
                                close()
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable(onClick = ::choose).padding(horizontal = 8.dp)
                            ) {
                                RadioButton(selected = entry.index == selected, onClick = ::choose)
                                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                if (entries.isEmpty()) item { EmptyText(stringResource(R.string.search_no_results)) }
            }
        }
    )
}
