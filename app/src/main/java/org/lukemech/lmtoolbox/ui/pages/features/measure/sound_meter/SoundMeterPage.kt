package org.lukemech.lmtoolbox.ui.pages.features.measure.sound_meter

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.common.ReadingStats
import org.lukemech.lmtoolbox.features.measure.sound_meter.SoundMeterFeature
import org.lukemech.lmtoolbox.features.measure.sound_meter.calibrationFor
import org.lukemech.lmtoolbox.features.measure.sound_meter.leqDbfs
import org.lukemech.lmtoolbox.features.measure.sound_meter.spl
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.PermissionDialog
import org.lukemech.lmtoolbox.ui.components.PromptBar
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.LiveGraph
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.MeterReading
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.HoldAction

/** The graph's last 30 s, a reading every SoundMeterFeature.BLOCK_MS. */
private const val HISTORY = 30_000 / SoundMeterFeature.BLOCK_MS

/** A step of the calibration's − and + (dB). */
private const val CALIBRATION_STEP = 0.5

@Composable
fun SoundMeterPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(SoundMeterFeature.hasMicPermission(context)) }
    var micDialog by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var aWeighted by remember { mutableStateOf(SoundMeterFeature.aWeighted(context)) }
    var calibration by remember { mutableDoubleStateOf(SoundMeterFeature.calibration(context)) }
    // Tried again by a tap on its bar, when the microphone couldn't be had
    var attempt by remember { mutableIntStateOf(0) }
    var unavailable by remember { mutableStateOf(false) }
    val unprocessed = remember { SoundMeterFeature.hasUnprocessed(context) }

    // In dBFS, so a new calibration applies to all of it at once
    var last by remember { mutableStateOf<SoundMeterFeature.Reading?>(null) }
    var history by remember { mutableStateOf(emptyList<Float>()) }
    var clipped by remember { mutableStateOf(false) }
    val levels = remember { ReadingStats() }
    val meanSquares = remember { ReadingStats() }

    fun reset() {
        levels.reset()
        meanSquares.reset()
        history = emptyList()
        clipped = false
    }

    // Asked for again on return (e.g. from the permission dialog or the settings, the calibration's there)
    LifecycleResumeEffect(Unit) {
        allowed = SoundMeterFeature.hasMicPermission(context)
        calibration = SoundMeterFeature.calibration(context)
        onPauseOrDispose {}
    }
    // The microphone only while the page is in sight: let go as it's left or the app goes to the background
    LifecycleResumeEffect(allowed, aWeighted, attempt) {
        val job = if (!allowed) null else scope.launch {
            unavailable = false
            val recording = SoundMeterFeature.record(context, aWeighted) { reading ->
                // Held: what it shows stays as it was
                if (holding) return@record
                last = reading
                clipped = reading.clipped
                levels.add(reading.level)
                meanSquares.add(reading.meanSquare)
                history = (history + reading.level.toFloat()).takeLast(HISTORY)
            }
            if (!recording) unavailable = true
        }
        onPauseOrDispose { job?.cancel() }
    }

    val fullScale = last?.fullScaleSpl
    fun shown(dbfs: Double?) = if (dbfs == null || fullScale == null) null else spl(dbfs, fullScale, calibration)
    val none = stringResource(R.string.f_meter_none)
    fun text(db: Double?) = db?.let { "%.1f".format(it) } ?: none
    val unit = stringResource(if (aWeighted) R.string.f_sound_unit_a else R.string.f_sound_unit_z)

    PageHeading(title = stringResource(R.string.fp_sound_meter), action = { HoldAction(holding) { holding = it } }) {
        Column(verticalArrangement = RowArrangement) {
            PromptBar(
                visible = !allowed,
                title = stringResource(R.string.f_sound_mic_needed),
                color = MaterialTheme.colorScheme.error,
                icon = Icons.Default.MicOff,
                icon2 = Icons.AutoMirrored.Default.ContactSupport,
                onClick = { micDialog = true }
            )
            PromptBar(
                visible = allowed && unavailable,
                title = stringResource(R.string.f_sound_mic_unavailable),
                color = MaterialTheme.colorScheme.error,
                icon = Icons.Default.MicOff,
                icon2 = Icons.Default.Refresh,
                onClick = { attempt++ }
            )

            val current = shown(last?.level)
            MeterReading(text(current), unit, held = holding, color = if (clipped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
            Note(stringResource(R.string.f_sound_clipped), visible = clipped)
            // Its scale in SPL; the history kept in dBFS, so it follows the calibration too
            LiveGraph(
                values = if (fullScale == null) emptyList() else history.map { (it + fullScale + calibration).toFloat() },
                capacity = HISTORY, min = 30f, max = 110f, step = 20f, unit = unit
            )
            Results {
                ResultRow(stringResource(R.string.f_meter_min), "${text(shown(levels.min))} $unit")
                ResultRow(stringResource(R.string.f_meter_max), "${text(shown(levels.max))} $unit")
                ResultRow(stringResource(R.string.f_sound_leq), "${text(shown(meanSquares.mean?.let(::leqDbfs)))} $unit")
            }

            SwitchButton(
                title = stringResource(R.string.f_sound_a_weighting),
                subtitle = stringResource(R.string.f_sound_a_weighting_desc),
                icon = Icons.Default.Hearing,
                checked = aWeighted,
                onCheckedChange = {
                    aWeighted = it
                    SoundMeterFeature.setAWeighted(context, it)
                    // dB(A) and dB don't mix: the min, max and Leq of one start anew
                    reset()
                }
            )
            Note(
                stringResource(
                    R.string.f_sound_note,
                    stringResource(if (unprocessed) R.string.f_sound_source_unprocessed else R.string.f_sound_source_voice)
                )
            )
        }
    }

    if (micDialog) MicDialog(onDismiss = { micDialog = false })
}

/**
 * Sound level meter setting: the calibration, a step at a time (− and +) or matched to a sound level meter
 * (tapped: [CalibrationDialog], the microphone listened to while it's open).
 */
@Composable
fun CalibrationSetting() {
    val context = LocalContext.current
    var calibration by remember { mutableDoubleStateOf(SoundMeterFeature.calibration(context)) }
    var calibrating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun calibrate(db: Double) {
        calibration = db
        SoundMeterFeature.setCalibration(context, db)
    }

    FeatureButton(
        title = stringResource(R.string.f_sound_calibration, "%+.1f".format(calibration)),
        subtitle = stringResource(R.string.f_sound_calibration_desc),
        icon = Icons.Default.Tune,
        buttonContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { calibrate(calibration - CALIBRATION_STEP) }) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.f_sound_calibration_lower))
                }
                IconButton(onClick = { calibrate(calibration + CALIBRATION_STEP) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.f_sound_calibration_raise))
                }
            }
        },
        onClick = { calibrating = true }
    )
    if (calibrating) {
        // What it reads without the calibration, to match the meter's: the microphone only while it's open
        var uncalibrated by remember { mutableStateOf<Double?>(null) }
        LifecycleResumeEffect(Unit) {
            val job = scope.launch {
                SoundMeterFeature.record(context, SoundMeterFeature.aWeighted(context)) { uncalibrated = spl(it.level, it.fullScaleSpl, 0.0) }
            }
            onPauseOrDispose { job.cancel() }
        }
        CalibrationDialog(uncalibrated = uncalibrated, onCalibrate = ::calibrate, onDismiss = { calibrating = false })
    }
}

/**
 * The calibration from a sound level meter beside the phone: what it shows, typed in, against the phone's
 * [uncalibrated] reading now; or back to none.
 */
@Composable
private fun CalibrationDialog(uncalibrated: Double?, onCalibrate: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val reference = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
        title = { Text(stringResource(R.string.f_sound_calibrate_title)) },
        text = {
            Column(verticalArrangement = RowArrangement) {
                Text(stringResource(R.string.f_sound_calibrate_message))
                InputField(
                    value = text,
                    // Digits alone (and a sign, a separator): nothing else can get into it
                    onValueChange = { typed -> text = typed.filter { it.isDigit() || it in ".,-" } },
                    label = { Text(stringResource(R.string.f_sound_calibrate_field)) },
                    singleLine = true,
                    enabled = uncalibrated != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Note(stringResource(R.string.f_sound_calibrate_waiting), visible = uncalibrated == null)
            }
        },
        confirmButton = {
            TextButton(
                enabled = reference != null && uncalibrated != null,
                onClick = {
                    if (reference != null && uncalibrated != null) onCalibrate(calibrationFor(uncalibrated, reference))
                    onDismiss()
                }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onCalibrate(0.0)
                    onDismiss()
                }) { Text(stringResource(R.string.f_sound_calibration_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    )
}

/**
 * Why the meter needs the microphone, and asking for it (or opening the app's settings once Android stops
 * asking). The page checks the permission again on return.
 */
@Composable
private fun MicDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    PermissionDialog(
        icon = Icons.Default.Mic,
        title = stringResource(R.string.f_sound_mic_title),
        message = stringResource(R.string.f_sound_mic_message),
        allowLabel = stringResource(R.string.f_sound_mic_allow),
        laterLabel = stringResource(R.string.f_sound_mic_later),
        permissions = arrayOf(Manifest.permission.RECORD_AUDIO),
        askedKey = SoundMeterFeature.S_MIC_ASKED,
        settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        onDismiss = onDismiss
    )
}
