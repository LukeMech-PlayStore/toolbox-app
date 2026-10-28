package org.lukemech.lmtoolbox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A few choices as chips under their [label] (if any), wrapping as they don't fit in a row, a [note] on them under
 * them (what the one picked does). In a column already in from the page's edges, as a form's fields are.
 */
@Composable
fun ChipGroup(label: String?, note: String? = null, chips: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        // As tall as they look: a chip's touch padding would part wrapped rows twice as far as chips side by side
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = chips)
        }
        note?.let { Note(it, inset = false) }
    }
}

/** A chip in a [ChipGroup]: [label], marked while [selected]. */
@Composable
fun Chip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) =
    FilterChip(selected = selected, enabled = enabled, onClick = onClick, label = { Text(label) })

/** One of [values] (value to label) picked as a [ChipGroup]'s chips. */
@Composable
fun <T> ChipChoice(
    label: String?,
    values: List<Pair<T, String>>,
    selected: T,
    note: String? = null,
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) = ChipGroup(label, note) {
    values.forEach { (value, text) -> Chip(text, value == selected, enabled) { onSelect(value) } }
}
