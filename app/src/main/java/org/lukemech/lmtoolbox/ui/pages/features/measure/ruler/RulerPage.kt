package org.lukemech.lmtoolbox.ui.pages.features.measure.ruler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.HoldAction

/** The ruler's part of the window's height: the rest for the title and its readings below. */
private const val RULER_PART = 0.55f

private const val MM_PER_INCH = 25.4f

@Composable
fun RulerPage() {
    // Along the screen's height: its vertical DPI, the real one
    val pixelsPerMm = LocalResources.current.displayMetrics.ydpi / MM_PER_INCH
    // The marker's distance from the ruler's 0 (px)
    var marker by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    val mm = marker / pixelsPerMm

    PageHeading(title = stringResource(R.string.fp_ruler), action = { HoldAction(holding) { holding = it } }) {
        Column(verticalArrangement = RowArrangement) {
            val height = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * RULER_PART).toDp() }
            Ruler(height, pixelsPerMm, marker, movable = !holding) { marker = it }
            Results {
                MainResult(stringResource(R.string.f_ruler_length), "%.1f cm".format(mm / 10))
                ResultRow(stringResource(R.string.f_ruler_millimeters), "%.0f mm".format(mm))
                ResultRow(stringResource(R.string.f_ruler_inches), "%.2f in".format(mm / MM_PER_INCH))
            }
            Note(stringResource(R.string.f_ruler_note))
        }
    }
}

/**
 * The ruler down the screen's left edge, [height] tall: 0 at its top, a line every millimeter, numbered every
 * centimeter; the [marker] across it (px from 0), set where it's tapped or dragged to while [movable].
 */
@Composable
private fun Ruler(height: Dp, pixelsPerMm: Float, marker: Float, movable: Boolean, onMarker: (Float) -> Unit) {
    val stripColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = lineColor)
    val measurer = rememberTextMeasurer()
    val canMove by rememberUpdatedState(movable)
    val setMarker by rememberUpdatedState(onMarker)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { if (canMove) setMarker(it.y.coerceIn(0f, size.height.toFloat())) }
            }
            .pointerInput(Unit) {
                // Its own: the page doesn't scroll under the finger
                detectDragGestures { change, _ ->
                    change.consume()
                    if (canMove) setMarker(change.position.y.coerceIn(0f, size.height.toFloat()))
                }
            }
    ) {
        val strip = 72.dp.toPx()
        val corner = CornerRadius(12.dp.toPx())
        drawRoundRect(stripColor, size = Size(strip, size.height), cornerRadius = corner)
        // What's measured, up to the marker
        drawRoundRect(markerColor.copy(alpha = 0.2f), size = Size(strip, marker), cornerRadius = corner)

        var mm = 0
        while (mm * pixelsPerMm <= size.height) {
            val y = mm * pixelsPerMm
            val length = when {
                mm % 10 == 0 -> strip * 0.5f
                mm % 5 == 0 -> strip * 0.35f
                else -> strip * 0.2f
            }
            drawLine(lineColor, Offset(0f, y), Offset(length, y), strokeWidth = if (mm % 10 == 0) 2.dp.toPx() else 1.dp.toPx())
            if (mm % 10 == 0 && mm > 0) {
                val label = measurer.measure("${mm / 10}", labelStyle)
                drawText(label, topLeft = Offset(length + 4.dp.toPx(), y - label.size.height / 2f))
            }
            mm++
        }

        // Across the whole width, so it's easy to line up with what's measured
        drawLine(markerColor, Offset(0f, marker), Offset(size.width, marker), strokeWidth = 2.dp.toPx())
        drawCircle(markerColor, radius = 8.dp.toPx(), center = Offset(size.width - 24.dp.toPx(), marker))
    }
}
