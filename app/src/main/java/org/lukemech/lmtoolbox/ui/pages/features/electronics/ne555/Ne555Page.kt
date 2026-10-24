package org.lukemech.lmtoolbox.ui.pages.features.electronics.ne555

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatPercent
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Pulse { R1, C, Width }

@Composable
fun Ne555Page() {
    var monostable by rememberSaveable { mutableStateOf(false) }
    var r1Text by rememberSaveable { mutableStateOf("10 k") }
    var r2Text by rememberSaveable { mutableStateOf("47 k") }
    var cText by rememberSaveable { mutableStateOf("100 n") }
    val pulse = rememberLinkedFields(2, Pulse.R1 to "10 k", Pulse.C to "100 n")

    val r1 = parseValue(r1Text)
    val r2 = parseValue(r2Text)
    val c = parseValue(cText)

    CalculatorPage(
        stringResource(R.string.fp_ne555),
        formulas = listOf(
            Formula("""f = \frac{1}{0.693 · (R_{1} + 2R_{2}) · C}""", stringResource(R.string.ec_freq)),
            Formula("""t_{H} = 0.693 · (R_{1} + R_{2}) · C""", stringResource(R.string.ec_t_high)),
            Formula("""t_{L} = 0.693 · R_{2} · C""", stringResource(R.string.ec_t_low)),
            Formula("""D = \frac{t_{H}}{t_{H} + t_{L}}""", stringResource(R.string.ec_duty)),
            Formula("""t = 1.1 · R_{1} · C""", "${stringResource(R.string.ec_monostable)}: ${stringResource(R.string.ec_pulse_width)}"),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.Tune,
            selected = monostable,
            options = listOf(false to stringResource(R.string.ec_astable), true to stringResource(R.string.ec_monostable)),
            onSelect = { monostable = it }
        )
        RevealSection(monostable) {
            // t = 1.1·R1·C, any of them from the other two
            val values = pulse.solve { given ->
                val r = given[Pulse.R1]
                val cap = given[Pulse.C]
                val t = given[Pulse.Width]
                when {
                    r != null && cap != null -> mapOf(Pulse.Width to 1.1 * r * cap)
                    r != null && t != null -> mapOf(Pulse.C to t / (1.1 * r))
                    cap != null && t != null -> mapOf(Pulse.R1 to t / (1.1 * cap))
                    else -> emptyMap()
                }
            }
            LinkedField(pulse, values, Pulse.R1, stringResource(R.string.ec_r1), "Ω")
            LinkedField(pulse, values, Pulse.C, stringResource(R.string.ec_capacitance), "F")
            LinkedField(pulse, values, Pulse.Width, stringResource(R.string.ec_pulse_width), "s")
        }
        RevealSection(!monostable) {
            UnitField(stringResource(R.string.ec_r1), r1Text, { r1Text = it }, "Ω")
            UnitField(stringResource(R.string.ec_r2), r2Text, { r2Text = it }, "Ω")
            UnitField(stringResource(R.string.ec_capacitance), cText, { cText = it }, "F")
            Results {
                if (r1 != null && r2 != null && c != null && r1 > 0 && r2 > 0 && c > 0) {
                    val tHigh = 0.693 * (r1 + r2) * c
                    val tLow = 0.693 * r2 * c
                    val period = tHigh + tLow
                    MainResult(stringResource(R.string.ec_freq), formatValue(1.0 / period, "Hz"))
                    ResultRow(stringResource(R.string.ec_period), formatValue(period, "s"))
                    ResultRow(stringResource(R.string.ec_duty), formatPercent(tHigh / period * 100.0))
                    ResultRow(stringResource(R.string.ec_t_high), formatValue(tHigh, "s"))
                    ResultRow(stringResource(R.string.ec_t_low), formatValue(tLow, "s"))
                }
            }
        }
    }
}
