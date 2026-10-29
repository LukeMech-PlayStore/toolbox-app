package org.lukemech.lmtoolbox.ui.pages.features.measure.stroboscope

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.stroboscope.MAX_HZ
import org.lukemech.lmtoolbox.features.measure.stroboscope.MIN_HZ
import org.lukemech.lmtoolbox.features.measure.stroboscope.StroboscopeFeature
import org.lukemech.lmtoolbox.features.measure.stroboscope.clampRate
import org.lukemech.lmtoolbox.features.measure.stroboscope.fieldText
import org.lukemech.lmtoolbox.features.measure.stroboscope.hzOfRpm
import org.lukemech.lmtoolbox.features.measure.stroboscope.rpm
import org.lukemech.lmtoolbox.features.measure.stroboscope.tapTempo
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.PromptBar
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SensorWarning
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitChoiceField

private const val HZ = "Hz"
private const val RPM = "RPM"

/** The measured rate this far below the set one (a part of it): the torch can't keep up, said so. */
private const val BEHIND = 0.05

@Composable
fun StroboscopePage() {
    val context = LocalContext.current
    val cameraId = remember { StroboscopeFeature.torchCamera(context) }
    var unit by rememberSaveable { mutableStateOf(HZ) }
    var text by rememberSaveable { mutableStateOf("5") }
    var flashing by remember { mutableStateOf(false) }
    // The torch taken (another app using the camera): said so on top, tapped to try again
    var busy by remember { mutableStateOf(false) }
    var measured by remember { mutableStateOf<Double?>(null) }
    val taps = remember { mutableListOf<Long>() }

    val typed = text.replace(',', '.').toDoubleOrNull()?.let { if (unit == RPM) hzOfRpm(it) else it }
    val hz = typed?.takeIf { it > 0 }?.let(::clampRate)
    // Past what the torch flashes at (either way): it flashes at the nearest it can, the field set to it as it's left
    val outside = typed != null && typed !in MIN_HZ..MAX_HZ
    fun inUnit(value: Double) = fieldText(if (unit == RPM) rpm(value) else value)
    fun setHz(value: Double) {
        val clamped = clampRate(value)
        text = fieldText(if (unit == RPM) rpm(clamped) else clamped)
    }

    val strobe = remember(cameraId) {
        cameraId?.let { id ->
            StroboscopeFeature.Strobe(context, id) {
                flashing = false
                busy = true
            }
        }
    }
    strobe?.hz = hz ?: MIN_HZ
    // No rate (the field emptied): nothing to flash at
    LaunchedEffect(hz) { if (hz == null) flashing = false }
    // Flashing only while the page is in sight: off as it's left or the app goes to the background, always
    LifecycleResumeEffect(flashing, strobe) {
        if (flashing && hz != null) strobe?.start()
        onPauseOrDispose {
            strobe?.stop()
            measured = null
        }
    }
    // How fast it really flashes, read twice a second
    if (flashing && strobe != null) {
        LaunchedEffect(strobe) {
            while (true) {
                measured = strobe.measuredHz
                delay(500.milliseconds)
            }
        }
    }

    PageHeading(title = stringResource(R.string.fp_stroboscope)) {
        Column(verticalArrangement = RowArrangement) {
            SensorWarning(hasSensors = cameraId != null, missingMessage = R.string.f_strobe_no_torch)
            PromptBar(
                visible = cameraId != null && busy,
                title = stringResource(R.string.f_strobe_torch_busy),
                color = MaterialTheme.colorScheme.error,
                icon = Icons.Default.FlashOff,
                icon2 = Icons.Default.Refresh,
                onClick = {
                    busy = false
                    flashing = hz != null
                }
            )
            Results(apart = false) {
                MainResult(
                    stringResource(R.string.f_strobe_rate),
                    hz?.let { stringResource(R.string.f_strobe_rate_value, "%.2f".format(it), "%.0f".format(rpm(it))) }
                        ?: stringResource(R.string.f_meter_none)
                )
                // The last one, while it folds away
                val lastMeasured = remember { doubleArrayOf(0.0) }
                measured?.let { lastMeasured[0] = it }
                Reveal(visible = measured != null) {
                    val rate = lastMeasured[0]
                    ResultRow(stringResource(R.string.f_strobe_measured), stringResource(R.string.f_strobe_rate_value, "%.2f".format(rate), "%.0f".format(rpm(rate))))
                }
            }
            Note(
                stringResource(R.string.f_strobe_behind),
                visible = flashing && hz != null && (measured ?: hz) < hz * (1 - BEHIND)
            )
            UnitChoiceField(
                label = stringResource(R.string.f_strobe_rate_field, inUnit(MIN_HZ), inUnit(MAX_HZ), unit),
                value = text,
                // Digits alone (and a separator): no letters can get into it
                onValueChange = { typed -> text = typed.filter { it.isDigit() || it in ".," } },
                unit = unit,
                units = listOf(HZ, RPM),
                onUnitChange = { picked ->
                    if (picked == unit) return@UnitChoiceField
                    // The same rate in the other unit, never a number of the old one
                    val value = text.replace(',', '.').toDoubleOrNull()
                    unit = picked
                    text = value?.let { fieldText(if (picked == RPM) rpm(it) else hzOfRpm(it)) }.orEmpty()
                },
                isError = outside,
                onLeave = { if (outside) setHz(typed) }
            )
            Note(
                stringResource(R.string.f_strobe_limited, inUnit(clampRate(typed ?: MIN_HZ)), unit),
                visible = outside
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                // Fine: a tenth of a Hz, or a turn a minute
                val nudge = if (unit == RPM) hzOfRpm(1.0) else 0.1
                StepButton("÷2") { hz?.let { setHz(it / 2) } }
                StepButton("−") { hz?.let { setHz(it - nudge) } }
                StepButton("+") { hz?.let { setHz(it + nudge) } }
                StepButton("×2") { hz?.let { setHz(it * 2) } }
            }
            FeatureButton(
                title = stringResource(R.string.f_strobe_tap),
                subtitle = stringResource(R.string.f_strobe_tap_desc),
                icon = Icons.Default.TouchApp,
                onClick = {
                    taps += SystemClock.uptimeMillis()
                    tapTempo(taps)?.let(::setHz)
                }
            )
            SwitchButton(
                title = stringResource(R.string.f_strobe_flash),
                subtitle = stringResource(R.string.f_strobe_flash_desc),
                icon = Icons.Default.FlashOn,
                checked = flashing,
                enabled = cameraId != null && hz != null,
                onCheckedChange = {
                    busy = false
                    flashing = it
                }
            )
            Note(stringResource(R.string.f_strobe_note, fieldText(MAX_HZ), fieldText(rpm(MAX_HZ))))
            Note(stringResource(R.string.f_strobe_warning))
        }
    }
}

/** One of the rate's fine-adjusting buttons, sharing the row. */
@Composable
private fun RowScope.StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
}
