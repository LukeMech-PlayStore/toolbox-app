package org.lukemech.lmtoolbox.ui.pages.features.measure.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
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

/** A meter's reading, large, in [unit] (smaller beside it); [color] as it's judged (e.g. red when it clips). */
@Composable
fun MeterReading(value: String, unit: String, color: Color = MaterialTheme.colorScheme.onBackground) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
        Text(
            unit,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
        )
    }
}

/** A PageHeading's action starting a meter's min, max and average over. */
@Composable
fun ResetAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.RestartAlt, contentDescription = stringResource(R.string.f_meter_reset))
    }
}

/**
 * A reading's last moments as a line, the newest at the right: [values] (oldest first) on a strip of [capacity]
 * of them, so the line grows in from the right as they come. The scale goes from [min] to [max] (widened to
 * hold every value, in [step]s), a line across at every [step] (or every few, widened far), labelled in [unit].
 */
@Composable
fun LiveGraph(values: List<Float>, capacity: Int, min: Float, max: Float, step: Float, unit: String) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val path = remember { Path() }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        // Whole steps around what's there, the given range at least
        val low = minOf(min, floor((values.minOrNull() ?: min) / step) * step)
        val high = maxOf(max, ceil((values.maxOrNull() ?: max) / step) * step)
        fun y(value: Float) = size.height * (1 - (value - low) / (high - low))
        // Widened a lot (a strong vibration), a line every few steps: never more than MAX_LINES
        var every = step
        while ((high - low) / every > MAX_LINES) every *= 2

        var line = low
        while (line <= high + every / 2) {
            val at = y(line)
            drawLine(gridColor, Offset(0f, at), Offset(size.width, at), strokeWidth = 1.dp.toPx())
            val label = measurer.measure("${formatTick(line)} $unit", labelStyle)
            // Above its line, but the lowest's (else off the graph)
            val top = (at - label.size.height).coerceIn(0f, size.height - label.size.height)
            drawText(label, topLeft = Offset(0f, top))
            line += every
        }

        if (values.size < 2) return@Canvas
        val dx = size.width / (capacity - 1)
        val start = size.width - dx * (values.size - 1)
        path.reset()
        values.forEachIndexed { i, value ->
            if (i == 0) path.moveTo(start, y(value)) else path.lineTo(start + dx * i, y(value))
        }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}

private const val MAX_LINES = 6

/** A grid line's value: whole when it is (40, not 40.0). */
private fun formatTick(value: Float) = if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)
