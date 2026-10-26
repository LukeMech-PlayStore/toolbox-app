package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.Reveal

/** How far apart pins are drawn: as much as the page has, between these. */
private val MIN_PITCH = 30.dp
private val MAX_PITCH = 48.dp

/**
 * A connector drawn from its pins (colored by what they do, or by their wires), what the colors mean, the
 * pins listed under it. A pin tapped, there or in the list, is marked in both and told about between them.
 */
@Composable
fun PinoutView(sheet: PinoutSheet, query: String) {
    // By its place in the list: a header's pins needn't have numbers of their own (Arduino's GNDs)
    var selected by rememberSaveable(sheet) { mutableStateOf<Int?>(null) }
    val resources = LocalResources.current
    val q = query.trim()
    fun matches(pin: Pin) = q.isEmpty() || pin.number.contains(q, true) || pin.name.contains(q, true) ||
        pin.description?.resolve(resources)?.contains(q, true) == true
    val pin = selected?.let { sheet.pins.getOrNull(it) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Legend(sheet.pins)
        PinoutDrawing(sheet, selected, q, ::matches) { tapped -> selected = tapped.takeIf { it != selected } }
        Note(stringResource(R.string.rf_drawn_in_order), visible = sheet.inOrder)
        sheet.note?.let { Note(it.resolve(resources)) }
        Reveal(pin != null) {
            // The last one tapped stays in it as it folds away
            val shown = pin ?: return@Reveal
            PinDetails(shown)
        }
        Column {
            sheet.pins.forEachIndexed { i, p ->
                Reveal(matches(p)) {
                    PinRow(p, i == selected) { selected = i.takeIf { it != selected } }
                }
            }
            Reveal(sheet.pins.none(::matches)) { EmptyText(stringResource(R.string.search_no_results)) }
        }
    }
}

/** What the colors stand for: the kinds of pins there are (but where wires color them). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(pins: List<Pin>) {
    val kinds = pins.filter { it.wire == null }.map { it.kind }.distinct().sortedBy { it.ordinal }
    if (kinds.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        kinds.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(kind.color))
                Text(stringResource(kind.label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** The connector: its outline, its pins at their places, numbered; tapping one picks it ([onTap]). */
@Composable
private fun PinoutDrawing(sheet: PinoutSheet, selected: Int?, query: String, matches: (Pin) -> Boolean, onTap: (Int) -> Unit) {
    val pins = sheet.pins
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
                    if (query.isNotEmpty() && !matches(pin)) drawCircle(dimmed, radius + 1f, center)
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

/** The pin tapped: its number and name, what it is, what it does. */
@Composable
private fun PinDetails(pin: Pin) {
    val resources = LocalResources.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp)
    ) {
        PinBadge(pin, 40.dp)
        Column {
            Text(
                "${stringResource(R.string.rf_pin)} ${pin.number} · ${pin.name}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                listOfNotNull(stringResource(pin.kind.label), pin.description?.resolve(resources)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** A pin in the list: its badge, name and what it does; marked while it's the one picked. */
@Composable
private fun PinRow(pin: Pin, selected: Boolean, onClick: () -> Unit) {
    val resources = LocalResources.current
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "pin"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        PinBadge(pin, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(pin.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            pin.description?.let {
                Text(it.resolve(resources), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A pin's number on its color, as in the drawing. */
@Composable
private fun PinBadge(pin: Pin, size: Dp) {
    val fill = pin.wire?.first() ?: pin.kind.color
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        pin.wire?.let { Swatch(it, size) } ?: Box(Modifier.size(size).clip(CircleShape).background(fill))
        Text(
            pin.number,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = if (pin.number.length > 3) 9.sp else 12.sp),
            color = if (fill.luminance() > 0.5f) Color.Black else Color.White,
            maxLines = 1
        )
    }
}
