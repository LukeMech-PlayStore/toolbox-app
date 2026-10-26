package org.lukemech.lmtoolbox.ui.pages.features.electronics.range_converter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatPercent
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.features.electronics.range_converter.RangePreset
import org.lukemech.lmtoolbox.features.electronics.range_converter.mapRange
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Signal { In, Out }

/** A range as picked ([preset]) and typed ([lo], [hi]): a preset's numbers, or Custom once they're changed. */
private class RangeState(preset: RangePreset) {
    var preset by mutableStateOf(preset)
    var lo by mutableStateOf(plain(preset.range!!.lo))
    var hi by mutableStateOf(plain(preset.range!!.hi))
    /** The last preset's: a custom range is most often one changed a little (3.8–20.5 mA). */
    var unit by mutableStateOf(preset.range!!.unit)

    fun pick(picked: RangePreset) {
        preset = picked
        picked.range?.let {
            lo = plain(it.lo)
            hi = plain(it.hi)
            unit = it.unit
        }
    }
}

@Composable
fun RangeConverterPage() {
    val input = rememberSaveable(saver = rangeSaver) { RangeState(RangePreset.Current4To20) }
    val output = rememberSaveable(saver = rangeSaver) { RangeState(RangePreset.Percent) }
    val signal = rememberLinkedFields(1, Signal.In to "12")

    val inLo = parseValue(input.lo)
    val inHi = parseValue(input.hi)
    val outLo = parseValue(output.lo)
    val outHi = parseValue(output.hi)
    val values = signal.solve { given ->
        if (inLo == null || inHi == null || outLo == null || outHi == null) return@solve emptyMap()
        given[Signal.In]?.let { x -> mapRange(x, inLo, inHi, outLo, outHi)?.let { mapOf(Signal.Out to it) } }
            ?: given[Signal.Out]?.let { y -> mapRange(y, outLo, outHi, inLo, inHi)?.let { mapOf(Signal.In to it) } }
            ?: emptyMap()
    }
    val x = values[Signal.In]

    CalculatorPage(
        stringResource(R.string.fp_range_converter),
        formulas = listOf(
            Formula("""y = y_{0} + (x − x_{0}) · \frac{y_{1} − y_{0}}{x_{1} − x_{0}}"""),
            Formula("""p = \frac{x − x_{0}}{x_{1} − x_{0}} · 100 %""", stringResource(R.string.ec_span)),
        )
    ) {
        RangeSettings(input, stringResource(R.string.ec_input_range), Icons.AutoMirrored.Default.Input, stringResource(R.string.ec_input_start), stringResource(R.string.ec_input_end))
        RangeSettings(output, stringResource(R.string.ec_output_range), Icons.AutoMirrored.Default.Logout, stringResource(R.string.ec_output_start), stringResource(R.string.ec_output_end))
        LinkedField(signal, values, Signal.In, stringResource(R.string.ec_input), input.unit, sigFigs = 6)
        LinkedField(signal, values, Signal.Out, stringResource(R.string.ec_output), output.unit, sigFigs = 6)
        val span = if (x != null && inLo != null && inHi != null) mapRange(x, inLo, inHi, 0.0, 100.0) else null
        Results {
            if (span != null) ResultRow(stringResource(R.string.ec_span), formatPercent(span))
        }
        Note(stringResource(R.string.ec_out_of_range), visible = span != null && (span < 0 || span > 100))
    }
}

/** A range's preset and its two ends. */
@Composable
private fun RangeSettings(range: RangeState, title: String, icon: ImageVector, startLabel: String, endLabel: String) {
    OptionSetting(
        title = title,
        icon = icon,
        selected = range.preset,
        options = RangePreset.entries.map { preset ->
            preset to (preset.range?.let { "${plain(it.lo)}–${plain(it.hi)} ${it.unit}" } ?: stringResource(R.string.ec_custom))
        },
        onSelect = range::pick
    )
    UnitField(startLabel, range.lo, {
        range.lo = it
        range.preset = RangePreset.Custom
    }, range.unit)
    UnitField(endLabel, range.hi, {
        range.hi = it
        range.preset = RangePreset.Custom
    }, range.unit)
}

/** Kept as the page is turned: the preset's name, the two ends and the unit. */
private val rangeSaver = listSaver<RangeState, String>(
    save = { listOf(it.preset.name, it.lo, it.hi, it.unit) },
    restore = { saved ->
        RangeState(RangePreset.Current4To20).apply {
            preset = RangePreset.valueOf(saved[0])
            lo = saved[1]
            hi = saved[2]
            unit = saved[3]
        }
    }
)

private fun plain(value: Double) = BigDecimal(value).stripTrailingZeros().toPlainString()
