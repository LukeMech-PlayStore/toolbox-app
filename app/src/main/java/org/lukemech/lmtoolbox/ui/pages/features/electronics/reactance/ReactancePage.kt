package org.lukemech.lmtoolbox.ui.pages.features.electronics.reactance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sqrt
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Reactance { Capacitive, Inductive, Resonance }

/** Frequency, the part (C or L) and its reactance (or the resonant frequency). */
private enum class Q { F, Part, X }

@Composable
fun ReactancePage() {
    var mode by rememberSaveable { mutableStateOf(Reactance.Capacitive) }
    val capacitive = rememberLinkedFields(2, Q.F to "1 k", Q.Part to "100 n")
    val inductive = rememberLinkedFields(2, Q.F to "1 k", Q.Part to "10 m")
    // For resonance: F is L, Part is C, X is f0
    val resonance = rememberLinkedFields(2, Q.F to "10 m", Q.Part to "100 n")
    var rText by rememberSaveable { mutableStateOf("100") }

    CalculatorPage(
        stringResource(R.string.fp_reactance),
        formulas = listOf(
            Formula("""X_{C} = \frac{1}{2πfC}""", stringResource(R.string.ec_cap_reactance)),
            Formula("""X_{L} = 2πfL""", stringResource(R.string.ec_ind_reactance)),
            Formula("""f_{0} = \frac{1}{2π\sqrt{LC}}""", stringResource(R.string.ec_resonant_freq)),
            Formula("""Q = \frac{1}{R} \sqrt{\frac{L}{C}}""", stringResource(R.string.ec_quality)),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.Tune,
            selected = mode,
            options = listOf(
                Reactance.Capacitive to stringResource(R.string.ec_cap_reactance),
                Reactance.Inductive to stringResource(R.string.ec_ind_reactance),
                Reactance.Resonance to stringResource(R.string.ec_resonance),
            ),
            onSelect = { mode = it }
        )
        RevealSection(mode == Reactance.Capacitive) {
            // X = 1 / (2π·f·C), so any of them is 1 / (2π·the other two)
            val values = capacitive.solve { given ->
                mapOf(Q.entries.first { it !in given } to 1.0 / (2.0 * PI * given.values.reduce(Double::times)))
            }
            LinkedField(capacitive, values, Q.F, stringResource(R.string.ec_freq), "Hz")
            LinkedField(capacitive, values, Q.Part, stringResource(R.string.ec_capacitance), "F")
            LinkedField(capacitive, values, Q.X, stringResource(R.string.ec_cap_reactance), "Ω")
        }
        RevealSection(mode == Reactance.Inductive) {
            val values = inductive.solve { given ->
                val f = given[Q.F]
                val l = given[Q.Part]
                val x = given[Q.X]
                when {
                    f != null && l != null -> mapOf(Q.X to 2.0 * PI * f * l)
                    f != null && x != null -> mapOf(Q.Part to x / (2.0 * PI * f))
                    l != null && x != null -> mapOf(Q.F to x / (2.0 * PI * l))
                    else -> emptyMap()
                }
            }
            LinkedField(inductive, values, Q.F, stringResource(R.string.ec_freq), "Hz")
            LinkedField(inductive, values, Q.Part, stringResource(R.string.ec_inductance), "H")
            LinkedField(inductive, values, Q.X, stringResource(R.string.ec_ind_reactance), "Ω")
        }
        RevealSection(mode == Reactance.Resonance) {
            val values = resonance.solve { given ->
                val l = given[Q.F]
                val c = given[Q.Part]
                val f0 = given[Q.X]
                when {
                    l != null && c != null -> mapOf(Q.X to 1.0 / (2.0 * PI * sqrt(l * c)))
                    l != null && f0 != null -> mapOf(Q.Part to 1.0 / ((2.0 * PI * f0).let { it * it } * l))
                    c != null && f0 != null -> mapOf(Q.F to 1.0 / ((2.0 * PI * f0).let { it * it } * c))
                    else -> emptyMap()
                }
            }
            LinkedField(resonance, values, Q.F, stringResource(R.string.ec_inductance), "H")
            LinkedField(resonance, values, Q.Part, stringResource(R.string.ec_capacitance), "F")
            LinkedField(resonance, values, Q.X, stringResource(R.string.ec_resonant_freq), "Hz")
            UnitField(stringResource(R.string.ec_resistance), rText, { rText = it }, "Ω")
            Results {
                val l = values[Q.F]
                val c = values[Q.Part]
                val r = parseValue(rText)
                if (l != null && c != null && l > 0 && c > 0 && r != null && r > 0) {
                    ResultRow(stringResource(R.string.ec_quality), String.format(Locale.US, "%.3f", sqrt(l / c) / r))
                }
            }
        }
    }
}
