package org.lukemech.lmtoolbox.ui.pages.features.measure.vibration_meter

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storm
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.common.ReadingStats
import org.lukemech.lmtoolbox.features.measure.common.sampleRate
import org.lukemech.lmtoolbox.features.measure.vibration_meter.GravityFilter
import org.lukemech.lmtoolbox.features.measure.vibration_meter.SampleWindow
import org.lukemech.lmtoolbox.features.measure.vibration_meter.VibrationMeterFeature
import org.lukemech.lmtoolbox.features.measure.vibration_meter.dominantFrequency
import org.lukemech.lmtoolbox.features.measure.vibration_meter.peak
import org.lukemech.lmtoolbox.features.measure.vibration_meter.powerOfTwoBelow
import org.lukemech.lmtoolbox.features.measure.vibration_meter.rms
import org.lukemech.lmtoolbox.features.measure.vibration_meter.toG
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SensorWarning
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.LiveGraph
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.MeterReading
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.HoldAction
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.SensorUpdates

/** The trace takes a point this often (ms): the largest motion since the last one, so no shake is missed. */
private const val TRACE_MS = 50L

/** The trace's last 5 s. */
private const val TRACE = (5000 / TRACE_MS).toInt()

/** The RMS, the peak and the frequency are worked out every this many trace points (250 ms). */
private const val ANALYSIS_EVERY = 5

/** Readings kept: two seconds at 1 kHz, more than any phone's accelerometer gives. */
private const val WINDOW = 2048

/** The most readings the frequency is found from: a few seconds' worth, so it follows a change soon. */
private const val FFT_MAX = 1024

/** Below this RMS (m/s²) it's the sensor's noise: no frequency to tell. */
private const val NOISE_RMS = 0.05

/** What the readings tell, as last worked out; nulls before there are enough. */
private class Analysis(val rms: Double?, val peak: Double?, val frequency: Double?, val sampleRate: Double?)

@Composable
fun VibrationMeterPage() {
    val context = LocalContext.current
    val hasSensor = remember { VibrationMeterFeature.hasSensors(context) }
    val sensorType = remember { VibrationMeterFeature.sensorType(context) }
    val window = remember { SampleWindow(WINDOW) }
    val gravity = remember { GravityFilter() }
    // Readings since the trace's last point
    val fresh = remember { intArrayOf(0) }
    var trace by remember { mutableStateOf(emptyList<Float>()) }
    var analysis by remember { mutableStateOf(Analysis(null, null, null, null)) }
    val peaks = remember { ReadingStats() }
    var maxPeak by remember { mutableStateOf<Double?>(null) }
    var holding by remember { mutableStateOf(false) }

    // As fast as the sensor goes: the frequency it can tell is up to half its rate
    SensorUpdates(sensorType, SensorManager.SENSOR_DELAY_FASTEST) { event ->
        val motion = if (sensorType == Sensor.TYPE_LINEAR_ACCELERATION) {
            DoubleArray(3) { event.values[it].toDouble() }
        } else {
            gravity.filter(event.values)
        }
        window.add(motion[0], motion[1], motion[2], event.timestamp)
        fresh[0]++
    }

    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        // Gaps while the page was away would read as a slower sensor
        window.clear()
        fresh[0] = 0
        val job = scope.launch {
            var points = 0
            while (true) {
                delay(TRACE_MS)
                val n = minOf(fresh[0], window.size)
                fresh[0] = 0
                // Held: the trace and the numbers stay as they were (the readings go on coming)
                if (holding) continue
                if (n > 0) trace = (trace + peak(window.axis(0, n), window.axis(1, n), window.axis(2, n)).toFloat()).takeLast(TRACE)
                if (++points % ANALYSIS_EVERY == 0) analysis = analyse(window)?.also { result ->
                    result.peak?.let {
                        peaks.add(it)
                        maxPeak = peaks.max
                    }
                } ?: analysis
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    val none = stringResource(R.string.f_meter_none)
    val unit = stringResource(R.string.f_vibration_unit)
    fun text(ms2: Double?) = ms2?.let { "%.3f %s · %.4f g".format(it, unit, toG(it)) } ?: none

    PageHeading(
        title = stringResource(R.string.fp_vibration_meter),
        action = { HoldAction(holding) { holding = it } }
    ) {
        Column(verticalArrangement = RowArrangement) {
            // Only the missing sensor: the motion's readings have no calibration to ask for (the spirit
            // level's strings stand in, never shown)
            SensorWarning(
                hasSensors = hasSensor, calibrated = true,
                calibrationTitle = R.string.f_level_accuracy_title,
                calibrationMessage = R.string.f_level_accuracy_message,
                calibrationIcon = Icons.Default.Storm,
                missingMessage = R.string.f_vibration_missing_sensors_message
            )
            MeterReading(analysis.rms?.let { "%.3f".format(it) } ?: none, unit, held = holding)
            LiveGraph(values = trace, capacity = TRACE, min = 0f, max = 1f, step = 0.25f, unit = unit)
            Results {
                ResultRow(stringResource(R.string.f_vibration_rms), text(analysis.rms))
                ResultRow(stringResource(R.string.f_vibration_peak), text(analysis.peak))
                ResultRow(stringResource(R.string.f_vibration_max_peak), text(maxPeak))
                ResultRow(
                    stringResource(R.string.f_vibration_frequency),
                    analysis.frequency?.let { "%.1f Hz".format(it) } ?: none
                )
                ResultRow(
                    stringResource(R.string.f_vibration_sample_rate),
                    analysis.sampleRate?.let { "${it.roundToInt()} Hz" } ?: none
                )
            }
            Note(
                stringResource(
                    R.string.f_vibration_note,
                    analysis.sampleRate?.let { it.roundToInt().toString() } ?: none,
                    analysis.sampleRate?.let { (it / 2).roundToInt().toString() } ?: none
                )
            )
        }
    }
}

/**
 * The RMS and the peak of the last second's readings in [window], the frequency of the last few seconds'
 * (when the motion is above the noise) and the sensor's rate; null until there are readings to tell it by.
 */
private fun analyse(window: SampleWindow): Analysis? {
    val rate = sampleRate(window.timestamps(minOf(window.size, FFT_MAX))) ?: return null
    val second = minOf(window.size, rate.roundToInt().coerceAtLeast(2))
    val x = window.axis(0, second)
    val y = window.axis(1, second)
    val z = window.axis(2, second)
    val rms = rms(x, y, z)
    val n = powerOfTwoBelow(minOf(window.size, FFT_MAX))
    val frequency = if (rms < NOISE_RMS) null else dominantFrequency(List(3) { window.axis(it, n) }, rate)
    return Analysis(rms, peak(x, y, z), frequency, rate)
}
