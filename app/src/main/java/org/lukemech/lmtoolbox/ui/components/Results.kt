package org.lukemech.lmtoolbox.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A calculator's results, together in one card (as its settings are): a [MainResult] or more and
 * [ResultRow]s, one under another, all lined up. Shows nothing while there are none.
 */
@Composable
fun Results(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Apart from the fields above it a little more than rows are from each other
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
        content = content
    )
}

/** A main result in [Results]: its [label], its [value] standing out. */
@Composable
fun MainResult(label: String, value: String) {
    LabelAndValue(
        label = { Text(label, style = MaterialTheme.typography.titleMedium) },
        value = {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/** A further result in [Results]: [label] on the left, [value] on the right. */
@Composable
fun ResultRow(label: String, value: String) {
    LabelAndValue(
        label = { Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        value = { Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * [label] on the left and [value] at the right end, both centered in the row; a value too long for the
 * space beside its label (a float's bits) goes under it instead, at its start, so the label isn't squeezed to a letter a line.
 */
@Composable
private fun LabelAndValue(label: @Composable () -> Unit, value: @Composable () -> Unit, modifier: Modifier) {
    Layout(contents = listOf(label, value), modifier = modifier) { (labelPart, valuePart), constraints ->
        val width = constraints.maxWidth
        val gap = 16.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val labelPlaced = labelPart.first().measure(loose)
        val valueMeasurable = valuePart.first()
        val beside = labelPlaced.width + gap + valueMeasurable.maxIntrinsicWidth(constraints.maxHeight) <= width
        if (beside) {
            val valuePlaced = valueMeasurable.measure(loose.copy(maxWidth = width - labelPlaced.width - gap))
            val height = maxOf(labelPlaced.height, valuePlaced.height)
            layout(width, height) {
                labelPlaced.placeRelative(0, (height - labelPlaced.height) / 2)
                valuePlaced.placeRelative(width - valuePlaced.width, (height - valuePlaced.height) / 2)
            }
        } else {
            val valuePlaced = valueMeasurable.measure(loose)
            val under = labelPlaced.height + 4.dp.roundToPx()
            layout(width, under + valuePlaced.height) {
                // Under the label, lined up with it, whether it fits one line or wraps
                labelPlaced.placeRelative(0, 0)
                valuePlaced.placeRelative(0, under)
            }
        }
    }
}

/** A hint or caveat, quieter; unfolding while [visible], folding away when it isn't. */
@Composable
fun Note(text: String, visible: Boolean = true) = Reveal(visible) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** Fields and settings only some modes have: [content] while [visible], unfolding and folding away ([Reveal]). */
@Composable
fun RevealSection(visible: Boolean, content: @Composable () -> Unit) = Reveal(visible) {
    Column(verticalArrangement = RowArrangement) { content() }
}
