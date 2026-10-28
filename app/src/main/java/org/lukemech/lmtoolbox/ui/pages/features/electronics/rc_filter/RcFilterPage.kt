package org.lukemech.lmtoolbox.ui.pages.features.electronics.rc_filter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.Locale
import kotlin.math.PI
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.features.electronics.rc_filter.firstOrderGainDb
import org.lukemech.lmtoolbox.features.electronics.rc_filter.lcResonance
import org.lukemech.lmtoolbox.features.electronics.rc_filter.rlCutoff
import org.lukemech.lmtoolbox.features.electronics.rc_filter.secondOrderCutoffRatio
import org.lukemech.lmtoolbox.features.electronics.rc_filter.secondOrderGainDb
import org.lukemech.lmtoolbox.features.electronics.rc_filter.secondOrderPeakDb
import org.lukemech.lmtoolbox.features.electronics.rc_filter.seriesQ
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Filter { RC, RL, RLC }

private enum class Rc { R, C, F }

private enum class Rl { R, L, F }

private enum class Lc { L, C, F }

/** Where the response is read, as f / fc (or f / f0): a decade either side, and half-decades. */
private val RATIOS = listOf(0.1, 0.316, 1.0, 3.16, 10.0)

@Composable
fun RcFilterPage() {
    var filter by rememberSaveable { mutableStateOf(Filter.RC) }
    var highPass by rememberSaveable { mutableStateOf(false) }
    val rc = rememberLinkedFields(2, Rc.R to "1 k", Rc.C to "100 n")
    val rl = rememberLinkedFields(2, Rl.R to "1 k", Rl.L to "10 m")
    val lc = rememberLinkedFields(2, Lc.L to "10 m", Lc.C to "100 n")
    var rText by rememberSaveable { mutableStateOf("100") }

    // fc = 1 / (2π·R·C), any of them from the other two
    val rcValues = rc.solve { given ->
        val missing = Rc.entries.first { it !in given }
        mapOf(missing to 1.0 / (2.0 * PI * given.values.reduce(Double::times)))
    }
    // fc = R / (2π·L)
    val rlValues = rl.solve { given ->
        val r = given[Rl.R]
        val l = given[Rl.L]
        val f = given[Rl.F]
        when {
            r != null && l != null -> mapOf(Rl.F to rlCutoff(r, l))
            r != null && f != null -> mapOf(Rl.L to r / (2.0 * PI * f))
            l != null && f != null -> mapOf(Rl.R to 2.0 * PI * f * l)
            else -> emptyMap()
        }
    }
    // f0 = 1 / (2π·√(L·C))
    val lcValues = lc.solve { given ->
        val l = given[Lc.L]
        val c = given[Lc.C]
        val f0 = given[Lc.F]
        when {
            l != null && c != null -> mapOf(Lc.F to lcResonance(l, c))
            l != null && f0 != null -> mapOf(Lc.C to 1.0 / ((2.0 * PI * f0).let { it * it } * l))
            c != null && f0 != null -> mapOf(Lc.L to 1.0 / ((2.0 * PI * f0).let { it * it } * c))
            else -> emptyMap()
        }
    }

    CalculatorPage(
        stringResource(R.string.fp_rc_filter),
        formulas = listOf(
            Formula("""f_{c} = \frac{1}{2πRC}""", "RC"),
            Formula("""f_{c} = \frac{R}{2πL}""", "RL"),
            Formula("""A = \frac{1}{\sqrt{1 + x^{2}}}""", "${stringResource(R.string.ec_low_pass)}, x = f/f_c"),
            Formula("""A = \frac{x}{\sqrt{1 + x^{2}}}""", stringResource(R.string.ec_high_pass)),
            Formula("""f_{0} = \frac{1}{2π\sqrt{LC}}""", "RLC"),
            Formula("""Q = \frac{1}{R} \sqrt{\frac{L}{C}}""", stringResource(R.string.ec_quality)),
            Formula("""B = \frac{f_{0}}{Q}""", stringResource(R.string.ec_bandwidth)),
            Formula("""A = \frac{1}{\sqrt{(1 − x^{2})^{2} + (x/Q)^{2}}}""", "${stringResource(R.string.ec_low_pass)} (C), x = f/f_0"),
            Formula("""A = \frac{x^{2}}{\sqrt{(1 − x^{2})^{2} + (x/Q)^{2}}}""", "${stringResource(R.string.ec_high_pass)} (L)"),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.Tune,
            selected = filter,
            options = Filter.entries.map { it to it.name },
            onSelect = { filter = it }
        )
        OptionSetting(
            title = stringResource(R.string.ec_response),
            icon = Icons.Default.FilterAlt,
            selected = highPass,
            options = listOf(false to stringResource(R.string.ec_low_pass), true to stringResource(R.string.ec_high_pass)),
            onSelect = { highPass = it }
        )
        RevealSection(filter == Filter.RC) {
            LinkedField(rc, rcValues, Rc.R, stringResource(R.string.ec_resistance), "Ω")
            LinkedField(rc, rcValues, Rc.C, stringResource(R.string.ec_capacitance), "F")
            LinkedField(rc, rcValues, Rc.F, stringResource(R.string.ec_cutoff), "Hz")
            FirstOrderResponse(rcValues[Rc.F], highPass)
        }
        RevealSection(filter == Filter.RL) {
            LinkedField(rl, rlValues, Rl.R, stringResource(R.string.ec_resistance), "Ω")
            LinkedField(rl, rlValues, Rl.L, stringResource(R.string.ec_inductance), "H")
            LinkedField(rl, rlValues, Rl.F, stringResource(R.string.ec_cutoff), "Hz")
            FirstOrderResponse(rlValues[Rl.F], highPass)
        }
        RevealSection(filter == Filter.RLC) {
            LinkedField(lc, lcValues, Lc.L, stringResource(R.string.ec_inductance), "H")
            LinkedField(lc, lcValues, Lc.C, stringResource(R.string.ec_capacitance), "F")
            LinkedField(lc, lcValues, Lc.F, stringResource(R.string.ec_resonant_freq), "Hz")
            UnitField(stringResource(R.string.ec_resistance), rText, { rText = it }, "Ω")
            val l = lcValues[Lc.L]
            val c = lcValues[Lc.C]
            val f0 = lcValues[Lc.F]
            val r = parseValue(rText)
            val q = if (l != null && c != null && r != null && l > 0 && c > 0 && r > 0) seriesQ(r, l, c) else null
            Results {
                if (q != null && f0 != null) {
                    MainResult(stringResource(R.string.ec_quality), String.format(Locale.US, "%.3f", q))
                    ResultRow(stringResource(R.string.ec_cutoff_3db), formatValue(f0 * secondOrderCutoffRatio(q, highPass), "Hz"))
                    ResultRow(stringResource(R.string.ec_bandwidth), formatValue(f0 / q, "Hz"))
                    secondOrderPeakDb(q)?.let { ResultRow(stringResource(R.string.ec_peaking), String.format(Locale.US, "+%.2f dB", it)) }
                    ResultRow(stringResource(R.string.ec_damping), String.format(Locale.US, "%.3f", 1.0 / (2.0 * q)))
                }
            }
            if (q != null && f0 != null) {
                Response(f0) { x -> secondOrderGainDb(x, q, highPass) }
            }
        }
    }
}

/** A first-order filter's response around its cutoff [fc]. */
@Composable
private fun FirstOrderResponse(fc: Double?, highPass: Boolean) {
    if (fc != null && fc > 0) Response(fc) { x -> firstOrderGainDb(x, highPass) }
}

/** The gain at [RATIOS] of [f], each frequency with its gain ([gainDb] of the ratio). */
@Composable
private fun Response(f: Double, gainDb: (Double) -> Double) {
    SectionTitle(stringResource(R.string.ec_response))
    Results {
        RATIOS.forEach { ratio ->
            ResultRow(formatValue(f * ratio, "Hz"), String.format(Locale.US, "%.2f dB", gainDb(ratio)))
        }
    }
}

