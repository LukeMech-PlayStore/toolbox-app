package org.lukemech.lmtoolbox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The card results and readings are shown in ([Results], a meter's graph): as wide as a row, its corners and shade
 * the same everywhere.
 */
@Composable
fun Modifier.resultCard(): Modifier = padding(horizontal = 16.dp)
    .clip(RoundedCornerShape(16.dp))
    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))

/**
 * A calculator's results or a meter's readings, together in one card (as its settings are): a [ReadingResult] (a
 * meter's), a [MainResult] or more and [ResultRow]s, one under another, all lined up. Shows nothing while there are none.
 */
@Composable
fun Results(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Apart from the fields above it a little more than rows are from each other
            .padding(top = 8.dp)
            .animateContentSize()
            .resultCard(),
        content = content
    )
}

/**
 * A meter's reading on top of its [Results], large, in [unit] (smaller beside it); [color] as it's judged (e.g. red
 * when it clips), the primary one while [held] (the meter's HoldAction), as in every meter.
 */
@Composable
fun ReadingResult(value: String, unit: String, held: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    val shown by animateColorAsState(if (held) MaterialTheme.colorScheme.primary else color, label = "reading")
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold), color = shown)
        Text(
            unit,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
        )
    }
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

/**
 * A further result in [Results]: [label] on the left, [value] on the right. [marked]: the one that applies now
 * (the light a reading is like), both in the primary color, bold.
 */
@Composable
fun ResultRow(label: String, value: String, marked: Boolean = false) {
    val labelColor by animateColorAsState(
        if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "resultLabel"
    )
    val valueColor by animateColorAsState(
        if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, label = "resultValue"
    )
    LabelAndValue(
        label = {
            Text(
                label, style = MaterialTheme.typography.bodyLarge, color = labelColor,
                fontWeight = if (marked) FontWeight.Bold else FontWeight.Normal
            )
        },
        value = {
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (marked) FontWeight.Bold else FontWeight.SemiBold), color = valueColor)
        },
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

/**
 * A hint or caveat, quieter; unfolding while [visible], folding away when it isn't. [inset]: as far in as a row's
 * text; without, where it's put (in a column already in from the edges, under a field or chips).
 */
@Composable
fun Note(text: String, visible: Boolean = true, inset: Boolean = true) = Reveal(visible) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().then(if (inset) Modifier.padding(horizontal = 16.dp, vertical = 4.dp) else Modifier)
    )
}

/** Fields and settings only some modes have: [content] while [visible], unfolding and folding away ([Reveal]). */
@Composable
fun RevealSection(visible: Boolean, content: @Composable () -> Unit) = Reveal(visible) {
    Column(verticalArrangement = RowArrangement) { content() }
}
