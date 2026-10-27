package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontStyle
import org.lukemech.lmtoolbox.ui.components.InfoTile
import org.lukemech.lmtoolbox.ui.components.InfoTiles
import org.lukemech.lmtoolbox.ui.components.resultCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Outline
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.fitted
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.edgeFade
import org.lukemech.lmtoolbox.ui.components.ROW_SPACING
import org.lukemech.lmtoolbox.ui.components.RowColors

/** How far apart pins are drawn: as much as the page has, between these. */
private val MIN_PITCH = 16.dp
private val MAX_PITCH = 40.dp

/**
 * A connector drawn from its pins (colored by what they do, or by their wires), a board's specs over it, and under
 * it one card telling about it: what the colors mean, the pin tapped, its notes. The pins are listed under that,
 * all of it scrolling as a page does. A pin tapped, in the drawing or the list, is marked in both and told about in
 * the card. Lazy: only the pins in sight built (a Mega's some 90), the ones [query] leaves out going as it's typed.
 */
@Composable
fun PinoutView(sheet: PinoutSheet, query: String) {
    // By its place in the list: a header's pins needn't have numbers of their own (Arduino's GNDs)
    var selected by rememberSaveable(sheet) { mutableStateOf<Int?>(null) }
    val resources = LocalResources.current
    val q = query.trim()
    val descriptions = remember(sheet, resources) { sheet.pins.map { it.description?.resolve(resources) } }
    fun matches(i: Int): Boolean {
        val pin = sheet.pins[i]
        return q.isEmpty() || pin.number.contains(q, true) || pin.name.contains(q, true) || descriptions[i]?.contains(q, true) == true
    }
    val shown = remember(sheet, q, descriptions) { sheet.pins.indices.filter(::matches) }
    val drawn = remember(sheet) { sheet.pins.fitted() }
    val scroll = rememberLazyListState()
    fun pick(i: Int) {
        selected = i.takeIf { it != selected }
    }

    LazyColumn(
        state = scroll,
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize().edgeFade(scroll)
    ) {
        item(key = "top") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                if (sheet.specs.isNotEmpty()) {
                    InfoTiles(
                        sheet.specs.map { InfoTile(stringResource(it.kind.label), it.value.resolve(resources), it.kind.icon) },
                        Modifier.padding(horizontal = 16.dp)
                    )
                }
                PinoutDrawing(sheet, drawn.pins, selected, q, ::matches, ::pick)
                PinoutCard(sheet, drawn.turned, selected?.let { sheet.pins.getOrNull(it) }) { selected = null }
            }
        }
        items(shown, key = { it }) { i ->
            PinRow(sheet.pins[i], i == selected, Modifier.animateItem()) { pick(i) }
        }
        if (shown.isEmpty()) item(key = "none") { EmptyText(stringResource(R.string.search_no_results)) }
    }
}

/**
 * The one card about a connector, under its drawing: what its colors mean, the pin tapped ([pin]: tapped again, it
 * goes, [onClose]) and its notes, the drawing's own ([turned], [PinoutSheet.inOrder]) last.
 */
@Composable
private fun PinoutCard(sheet: PinoutSheet, turned: Boolean, pin: Pin?, onClose: () -> Unit) {
    val resources = LocalResources.current
    Column(Modifier.fillMaxWidth().resultCard().animateContentSize().padding(vertical = 8.dp)) {
        Legend(sheet.pins)
        // The last one tapped stays in it as it folds away
        val last = remember { arrayOfNulls<Pin>(1) }
        if (pin != null) last[0] = pin
        Reveal(pin != null) {
            val shown = last[0] ?: return@Reveal
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    PinBadge(shown, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${stringResource(R.string.rf_pin)} ${shown.number} · ${shown.name}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(stringResource(shown.kind.label), shown.description?.resolve(resources)).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        val notes = listOfNotNull(
            sheet.note?.resolve(resources),
            stringResource(R.string.rf_drawn_in_order).takeIf { sheet.inOrder },
            stringResource(R.string.rf_drawn_turned).takeIf { turned },
        )
        if (notes.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
            notes.forEach { Note(it) }
        }
    }
}

/** What the colors stand for: the kinds of pins there are (but where wires color them). */
@Composable
private fun Legend(pins: List<Pin>) {
    val kinds = pins.filter { it.wire == null }.map { it.kind }.distinct().sortedBy { it.ordinal }
    if (kinds.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        kinds.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(kind.color))
                Text(stringResource(kind.label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * The connector: its outline, its pins at their places ([places]: [sheet]'s, maybe turned), numbered; tapping
 * one picks it ([onTap]). As wide as the page, its pins as far apart as that leaves; wider (a Mega's), it scrolls.
 */
@Composable
private fun PinoutDrawing(sheet: PinoutSheet, places: List<Pin>, selected: Int?, query: String, matches: (Int) -> Boolean, onTap: (Int) -> Unit) {
    val pins = places
    val columns = (pins.maxOf { it.x } + 1f)
    val rows = (pins.maxOf { it.y } + 1f)
    val measurer = rememberTextMeasurer()
    val outlineColor = MaterialTheme.colorScheme.outline
    val bodyColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    val ringColor = MaterialTheme.colorScheme.primary
    val dimmed = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val description = stringResource(R.string.rf_tap_pin)

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        // A margin of half a pitch and more around the pins, for the outline
        val pitch: Dp = (maxWidth / (columns + 1.2f)).coerceIn(MIN_PITCH, MAX_PITCH)
        val width = pitch * (columns + 1.2f)
        val height = pitch * (rows + 1.2f)
        Box(Modifier.widthIn(max = maxWidth).horizontalScroll(rememberScrollState())) {
            Canvas(
                modifier = Modifier
                    .size(width, height)
                    .semantics { contentDescription = description }
                    .pointerInput(pins) {
                        detectTapGestures { at ->
                            val p = pitch.toPx()
                            fun distance(pin: Pin) = hypot(at.x - (pin.x + 1.1f) * p, at.y - (pin.y + 1.1f) * p)
                            val nearest = pins.indices.minBy { distance(pins[it]) }
                            if (distance(pins[nearest]) < p * 0.6f) onTap(nearest)
                        }
                    }
            ) {
                val p = pitch.toPx()
                drawOutline(sheet.outline, Size(size.width, size.height), p, bodyColor, outlineColor)
                pins.forEachIndexed { i, pin ->
                    val center = Offset((pin.x + 1.1f) * p, (pin.y + 1.1f) * p)
                    val radius = p * 0.38f
                    drawPin(pin, center, radius)
                    // The number, shrunk to fit the pin (Arduino's "AREF")
                    val label = measurer.measure(pin.number, TextStyle(fontSize = (p * 0.3f).toSp(), fontWeight = FontWeight.Bold))
                    val scale = (radius * 1.7f / label.size.width).coerceAtMost(1f)
                    val text = if (scale < 1f) {
                        measurer.measure(pin.number, TextStyle(fontSize = (p * 0.3f * scale).toSp(), fontWeight = FontWeight.Bold))
                    } else label
                    val fill = pin.wire?.first() ?: pin.kind.color
                    drawText(
                        text,
                        color = if (fill.luminance() > 0.5f) Color.Black else Color.White,
                        topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f)
                    )
                    if (query.isNotEmpty() && !matches(i)) drawCircle(dimmed, radius + 1f, center)
                    if (i == selected) drawCircle(ringColor, radius + 3.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.drawOutline(outline: Outline, size: Size, pitch: Float, body: Color, edge: Color) {
    val inset = pitch * 0.25f
    when (outline) {
        Outline.Box -> {
            val corner = CornerRadius(pitch * 0.3f)
            drawRoundRect(body, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), corner)
            drawRoundRect(edge, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), corner, style = Stroke(1.5.dp.toPx()))
        }
        Outline.Trapezoid -> {
            // Wider at the top, as a D-sub's shell
            val slant = pitch * 0.45f
            val path = Path().apply {
                moveTo(inset, inset)
                lineTo(size.width - inset, inset)
                lineTo(size.width - inset - slant, size.height - inset)
                lineTo(inset + slant, size.height - inset)
                close()
            }
            drawPath(path, body)
            drawPath(path, edge, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** A pin: its kind's color, or its wire's (a striped one as halves: the base color and the stripe's). */
private fun DrawScope.drawPin(pin: Pin, center: Offset, radius: Float) {
    val wire = pin.wire
    if (pin.kind == PinKind.Unused && wire == null) {
        drawCircle(PinKind.Unused.color, radius, center, style = Stroke(2.dp.toPx()))
        return
    }
    drawCircle(wire?.first() ?: pin.kind.color, radius, center)
    if (wire != null && wire.size > 1) {
        val half = Path().apply { addRect(androidx.compose.ui.geometry.Rect(center.x, center.y - radius, center.x + radius, center.y + radius)) }
        clipPath(half) { drawCircle(wire[1], radius, center) }
    }
    drawCircle(Color.Black.copy(alpha = 0.25f), radius, center, style = Stroke(1.dp.toPx()))
}

/** A pin in the list, as the app's rows are: its badge where their icon is, its name and what it does; marked while it's the one picked. */
@Composable
private fun PinRow(pin: Pin, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FeatureButton(
        modifier = modifier,
        title = pin.name,
        subtitle = pin.description?.resolve(LocalResources.current),
        leading = { PinBadge(pin, 40.dp) },
        colors = if (selected) RowColors.highlighted else RowColors.default,
        onClick = onClick
    )
}

/** A pin's number on its color, as in the drawing. */
@Composable
private fun PinBadge(pin: Pin, size: Dp) {
    val fill = pin.wire?.first() ?: pin.kind.color
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        pin.wire?.let { Swatch(it, size) } ?: Box(Modifier.size(size).clip(CircleShape).background(fill))
        // Shrunk to fit inside the circle, as in the drawing ("IOREF", "3V3(OUT)")
        Text(
            pin.number,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (fill.luminance() > 0.5f) Color.Black else Color.White,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 12.sp),
            modifier = Modifier.padding(horizontal = size * 0.12f)
        )
    }
}
