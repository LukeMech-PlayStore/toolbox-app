package org.lukemech.lmtoolbox.ui.pages.features.measure.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.floor
import org.lukemech.lmtoolbox.R

/**
 * The device's [type] sensor (Sensor.TYPE_*) read while the page is in sight: listened to as it's resumed, let
 * go as it's left or the app goes to the background, so no sensor runs for a page no one sees. [onEvent] and
 * [onAccuracy] on the main thread, the latest ones given; nothing comes without the sensor.
 */
@Composable
fun SensorUpdates(
    type: Int,
    samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI,
    onAccuracy: (Int) -> Unit = {},
    onEvent: (SensorEvent) -> Unit,
) {
    val context = LocalContext.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnAccuracy by rememberUpdatedState(onAccuracy)
    LifecycleResumeEffect(type, samplingPeriodUs) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = currentOnEvent(event)
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = currentOnAccuracy(accuracy)
        }
        sensorManager.getDefaultSensor(type)?.let { sensorManager.registerListener(listener, it, samplingPeriodUs) }
        onPauseOrDispose { sensorManager.unregisterListener(listener) }
    }
}

/**
 * A meter's reading, large, in [unit] (smaller beside it); [color] as it's judged (e.g. red when it clips), the
 * primary one while [held] (see [HoldAction]), as in every meter.
 */
@Composable
fun MeterReading(value: String, unit: String, held: Boolean, color: Color = MaterialTheme.colorScheme.onBackground) {
    val shown by animateColorAsState(if (held) MaterialTheme.colorScheme.primary else color)
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

/**
 * A PageHeading's action holding a meter's readings where they are ([holding]: everything it shows stays put,
 * to read it off), and letting them go on; in the primary color while it holds.
 */
@Composable
fun HoldAction(holding: Boolean, onHoldingChange: (Boolean) -> Unit) {
    val tint by animateColorAsState(if (holding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    IconButton(onClick = { onHoldingChange(!holding) }) {
        Crossfade(holding, label = "hold") { held ->
            Icon(
                if (held) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(if (held) R.string.f_meter_resume else R.string.f_meter_hold),
                tint = tint
            )
        }
    }
}

/**
 * A reading's last moments as a line, the newest at the right: [values] (oldest first, one every [intervalMs]) on a
 * strip of [capacity] of them, so the line grows in from the right as they come. The scale goes from [min] to
 * [max] (widened to hold every value, in [step]s), a line across at every [step] (or every few, widened far),
 * labeled in [unit].
 *
 * Back in time: touched, it holds the meter ([onHoldingChange], as its HoldAction does) and the point under the
 * finger is marked, its value ([label]) and how long ago it was told at the top; sliding along it goes from one
 * to the next. Let go, the meter goes on, unless it was [holding] already (its HoldAction): then the mark stays,
 * gone as it goes on.
 */
@Composable
fun LiveGraph(
    values: List<Float>, capacity: Int, min: Float, max: Float, step: Float, unit: String,
    intervalMs: Long, label: (Float) -> String, holding: Boolean, onHoldingChange: (Boolean) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface
    val tipColor = MaterialTheme.colorScheme.inverseSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tipStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val ago = stringResource(R.string.f_meter_ago)
    val measurer = rememberTextMeasurer()
    val path = remember { Path() }
    val current by rememberUpdatedState(values)
    val isHolding by rememberUpdatedState(holding)
    val setHolding by rememberUpdatedState(onHoldingChange)
    // While touched: the values as they were then, and where the finger is (px)
    var held by remember { mutableStateOf<List<Float>?>(null) }
    var finger by remember { mutableStateOf<Float?>(null) }
    val shown = held ?: values
    LaunchedEffect(holding) { if (!holding) finger = null }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    held = current
                    finger = down.position.x
                    // Held by the touch alone: going on as it's let go
                    val heldBefore = isHolding
                    if (!heldBefore) setHolding(true)
                    // Its own while touched: sliding along it doesn't scroll the page
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        finger = change.position.x
                        change.consume()
                    }
                    held = null
                    if (!heldBefore) setHolding(false)
                }
            }
    ) {
        // Whole steps around what's there, the given range at least
        val low = minOf(min, floor((shown.minOrNull() ?: min) / step) * step)
        val high = maxOf(max, ceil((shown.maxOrNull() ?: max) / step) * step)
        fun y(value: Float) = size.height * (1 - (value - low) / (high - low))
        // Widened a lot (a strong vibration), a line every few steps: never more than MAX_LINES
        var every = step
        while ((high - low) / every > MAX_LINES) every *= 2

        var line = low
        while (line <= high + every / 2) {
            val at = y(line)
            drawLine(gridColor, Offset(0f, at), Offset(size.width, at), strokeWidth = 1.dp.toPx())
            val text = measurer.measure("${formatTick(line)} $unit", labelStyle)
            // Above its line, but the lowest's (else off the graph)
            val top = (at - text.size.height).coerceIn(0f, size.height - text.size.height)
            drawText(text, topLeft = Offset(0f, top))
            line += every
        }

        if (shown.size < 2) return@Canvas
        val dx = size.width / (capacity - 1)
        val start = size.width - dx * (shown.size - 1)
        path.reset()
        shown.forEachIndexed { i, value ->
            if (i == 0) path.moveTo(start, y(value)) else path.lineTo(start + dx * i, y(value))
        }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))

        // The point under the finger, its value and how long ago
        val x = finger ?: return@Canvas
        val i = ((x - start) / dx).roundToInt().coerceIn(0, shown.lastIndex)
        val at = Offset(start + dx * i, y(shown[i]))
        drawLine(cursorColor, Offset(at.x, 0f), Offset(at.x, size.height), strokeWidth = 1.dp.toPx())
        drawCircle(lineColor, radius = 5.dp.toPx(), center = at)
        val seconds = (shown.lastIndex - i) * intervalMs / 1000f
        val tip = measurer.measure("${label(shown[i])} · ${ago.format("%.1f".format(seconds))}", tipStyle)
        val pad = 6.dp.toPx()
        val width = tip.size.width + 2 * pad
        val left = (at.x - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
        drawRoundRect(tipColor, Offset(left, 0f), Size(width, tip.size.height + 2 * pad), CornerRadius(8.dp.toPx()))
        drawText(tip, topLeft = Offset(left + pad, pad))
    }
}

private const val MAX_LINES = 6

/** A grid line's value: whole when it is (40, not 40.0). */
private fun formatTick(value: Float) = if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)
