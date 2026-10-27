package org.lukemech.lmtoolbox.ui.pages.features.measure.light_meter

import android.hardware.Sensor
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.common.ReadingStats
import org.lukemech.lmtoolbox.features.measure.light_meter.LIGHT_REFERENCES
import org.lukemech.lmtoolbox.features.measure.light_meter.LightMeterFeature
import org.lukemech.lmtoolbox.features.measure.light_meter.LightReference
import org.lukemech.lmtoolbox.features.measure.light_meter.footCandles
import org.lukemech.lmtoolbox.features.measure.light_meter.referenceFor
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.MeterReading
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.HoldAction
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.SensorUpdates

/**
 * The min, max and average take the reading this often (ms): the sensor reports only when the light changes,
 * so a steady light would otherwise count once, however long it shines.
 */
private const val TICK_MS = 200L

@Composable
fun LightMeterPage() {
    val context = LocalContext.current
    val hasSensor = remember { LightMeterFeature.hasSensors(context) }
    var lux by remember { mutableStateOf<Double?>(null) }
    var holding by remember { mutableStateOf(false) }
    // While held, the reading at the time
    var shown by remember { mutableStateOf<Double?>(null) }
    val stats = remember { ReadingStats() }
    // The stats as last taken (they're no state of their own)
    var summary by remember { mutableStateOf(Triple<Double?, Double?, Double?>(null, null, null)) }

    SensorUpdates(Sensor.TYPE_LIGHT) { event ->
        lux = event.values[0].toDouble()
        if (!holding) shown = lux
    }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(holding) {
        val job = if (holding) null else scope.launch {
            while (true) {
                lux?.let {
                    stats.add(it)
                    summary = Triple(stats.min, stats.max, stats.mean)
                }
                delay(TICK_MS.milliseconds)
            }
        }
        onPauseOrDispose { job?.cancel() }
    }

    val none = stringResource(R.string.f_meter_none)
    val unit = stringResource(R.string.f_light_unit)
    fun text(value: Double?) = value?.let(::formatLux)?.let { "$it $unit" } ?: none

    PageHeading(
        title = stringResource(R.string.fp_light_meter),
        action = {
            HoldAction(holding) {
                holding = it
                if (!it) shown = lux
            }
        }
    ) {
        if (!hasSensor) {
            EmptyText(stringResource(R.string.f_light_missing))
            return@PageHeading
        }
        Column(verticalArrangement = RowArrangement) {
            MeterReading(shown?.let(::formatLux) ?: none, unit, held = holding)
            Results {
                ResultRow(stringResource(R.string.f_light_foot_candles), shown?.let { "%.2f fc".format(footCandles(it)) } ?: none)
                ResultRow(stringResource(R.string.f_meter_min), text(summary.first))
                ResultRow(stringResource(R.string.f_meter_max), text(summary.second))
                ResultRow(stringResource(R.string.f_meter_avg), text(summary.third))
            }
            SectionTitle(stringResource(R.string.f_light_references))
            References(current = shown?.let { referenceFor(it) })
            Note(stringResource(R.string.f_light_note))
        }
    }
}

/** The typical lights (LIGHT_REFERENCES), the one the reading is like ([current]) marked. */
@Composable
private fun References(current: Int?) {
    val unit = stringResource(R.string.f_light_unit)
    Results {
        LIGHT_REFERENCES.forEachIndexed { i, reference ->
            val marked = i == current
            val color by animateColorAsState(if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(reference.name),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (marked) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${range(reference)} $unit",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (marked) FontWeight.Bold else FontWeight.SemiBold,
                    color = color
                )
            }
        }
    }
}

/** [reference]'s lux: its typical value, or from-to. */
private fun range(reference: LightReference) =
    if (reference.lowLux == reference.highLux) formatLux(reference.lowLux)
    else "${formatLux(reference.lowLux)}–${formatLux(reference.highLux)}"

/** [lux] to as many places as tell it apart: 0.05 by moonlight, 320 in an office, 32,000 in the sun. */
private fun formatLux(lux: Double) = when {
    lux < 1 -> "%.2f".format(lux)
    lux < 10 -> "%.1f".format(lux)
    else -> "%,.0f".format(lux)
}
