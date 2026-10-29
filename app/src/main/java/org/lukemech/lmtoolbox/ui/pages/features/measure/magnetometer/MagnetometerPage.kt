package org.lukemech.lmtoolbox.ui.pages.features.measure.magnetometer

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.magnetometer.Baseline
import org.lukemech.lmtoolbox.features.measure.magnetometer.MagnetometerFeature
import org.lukemech.lmtoolbox.features.measure.magnetometer.MagnetometerFeature.Feedback
import org.lukemech.lmtoolbox.features.measure.magnetometer.buzzInterval
import org.lukemech.lmtoolbox.features.measure.magnetometer.deviation
import org.lukemech.lmtoolbox.features.measure.magnetometer.magnitude
import org.lukemech.lmtoolbox.features.measure.magnetometer.strength
import org.lukemech.lmtoolbox.features.measure.magnetometer.toneFrequency
import org.lukemech.lmtoolbox.helpers.ReadingFilter
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.MutedWarning
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SensorWarning
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.HoldAction
import org.lukemech.lmtoolbox.ui.components.ReadingResult
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.SensorUpdates

/** A field the components and strength are shown of, µT (x, y, z, and their strength). */
private class Field(val x: Double, val y: Double, val z: Double) {
    val magnitude = magnitude(x, y, z)
}

@Composable
fun MagnetometerPage() {
    val context = LocalContext.current
    val hasSensor = remember { MagnetometerFeature.hasSensors(context) }
    val canBuzz = remember { MagnetometerFeature.canBuzz(context) }
    var field by remember { mutableStateOf<Field?>(null) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) }
    var detecting by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(Feedback.Tone) }
    val baseline = remember { Baseline() }
    var zero by remember { mutableStateOf<Double?>(null) }
    // Every reading's, for the tone and the buzz (they read it as they go); the page shows it as filtered
    val latestDeviation = remember { doubleArrayOf(0.0) }
    // The numbers steady enough to read: a tenth of a µT, ten times a second
    val filter = remember { ReadingFilter(0.1f) }

    SensorUpdates(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_GAME, onAccuracy = { accuracy = it }) { event ->
        // Taken from every reading too: accuracy callbacks come rarely and late (as the compass does)
        accuracy = event.accuracy
        // Held: the field and its change stay as they were
        if (holding) return@SensorUpdates
        val now = Field(event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble())
        if (detecting) {
            baseline.add(now.magnitude)
            if (zero == null) zero = baseline.value
            latestDeviation[0] = baseline.value?.let { deviation(now.magnitude, it) } ?: 0.0
        }
        if (field == null || filter.accept(now.magnitude.toFloat())) field = now
    }

    fun recalibrate() {
        baseline.restart()
        zero = null
        latestDeviation[0] = 0.0
    }

    // The tone or the buzz only while detecting (not held) on a page in sight
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(detecting, feedback, holding) {
        val on = detecting && !holding
        val jobs = listOfNotNull(
            if (on && feedback.tone) scope.launch { MagnetometerFeature.playTone { toneFrequency(latestDeviation[0]) } } else null,
            if (on && feedback.buzz) scope.launch { MagnetometerFeature.buzz(context) { buzzInterval(latestDeviation[0]) } } else null,
        )
        onPauseOrDispose { jobs.forEach { it.cancel() } }
    }

    val none = stringResource(R.string.f_meter_none)
    val unit = stringResource(R.string.f_magnet_unit)
    fun text(value: Double?) = value?.let { "%.1f %s".format(it, unit) } ?: none

    PageHeading(title = stringResource(R.string.fp_magnetometer), action = { HoldAction(holding) { holding = it } }) {
        Column(verticalArrangement = RowArrangement) {
            // The tone picked, but the media volume is off
            MutedWarning(sound = detecting && feedback.tone)
            SensorWarning(
                hasSensors = hasSensor,
                calibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                calibrationTitle = R.string.f_magnet_calibration_title,
                calibrationMessage = R.string.f_magnet_calibration_message,
                calibrationIcon = Icons.Default.CompassCalibration,
                missingMessage = R.string.f_magnet_missing_sensors_message
            )
            Results(apart = false) {
                ReadingResult(field?.magnitude?.let { "%.1f".format(it) } ?: none, unit, held = holding)
                ResultRow("X", text(field?.x))
                ResultRow("Y", text(field?.y))
                ResultRow("Z", text(field?.z))
                // Detecting: the change from the zero, and its bar, with the other readings
                Reveal(visible = detecting) {
                    val shownDeviation = zero?.let { z -> field?.let { deviation(it.magnitude, z) } }
                    val level by animateFloatAsState(
                        targetValue = shownDeviation?.let { strength(it).toFloat() } ?: 0f,
                        animationSpec = tween(150)
                    )
                    val error = MaterialTheme.colorScheme.error
                    val barColor by animateColorAsState(if (level >= 0.5f) error else MaterialTheme.colorScheme.primary)
                    Column {
                        MainResult(
                            stringResource(R.string.f_magnet_deviation),
                            if (zero == null) stringResource(R.string.f_magnet_zeroing) else text(shownDeviation)
                        )
                        LinearProgressIndicator(
                            progress = { level },
                            color = barColor,
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(8.dp)
                        )
                    }
                }
            }

            SwitchButton(
                title = stringResource(R.string.f_magnet_detector),
                subtitle = stringResource(R.string.f_magnet_detector_desc),
                icon = Icons.Default.Hardware,
                checked = detecting,
                enabled = hasSensor,
                onCheckedChange = {
                    detecting = it
                    if (it) recalibrate()
                }
            )
            RevealSection(visible = detecting) {
                FeatureButton(
                    title = stringResource(R.string.f_magnet_recalibrate),
                    subtitle = stringResource(R.string.f_magnet_recalibrate_desc),
                    icon = Icons.Default.GpsFixed,
                    onClick = ::recalibrate
                )
                OptionSetting(
                    title = stringResource(R.string.f_magnet_feedback),
                    icon = Icons.Default.NotificationsActive,
                    selected = feedback,
                    options = listOfNotNull(
                        Feedback.Off to stringResource(R.string.f_magnet_feedback_off),
                        Feedback.Tone to stringResource(R.string.f_magnet_feedback_tone),
                        if (canBuzz) Feedback.Buzz to stringResource(R.string.f_magnet_feedback_buzz) else null,
                        if (canBuzz) Feedback.ToneAndBuzz to stringResource(R.string.f_magnet_feedback_both) else null
                    ),
                    onSelect = { feedback = it }
                )
                Note(stringResource(R.string.f_magnet_detector_note))
            }
            Note(stringResource(R.string.f_magnet_note))
        }
    }
}
