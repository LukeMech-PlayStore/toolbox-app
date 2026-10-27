package org.lukemech.lmtoolbox.ui.pages.features.electronics.resistor_ratio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.log10
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.features.electronics.resistor_ratio.RatioKind
import org.lukemech.lmtoolbox.features.electronics.resistor_ratio.bestPairs
import org.lukemech.lmtoolbox.features.electronics.resistor_ratio.convertRatio
import org.lukemech.lmtoolbox.features.electronics.resistor_ratio.ratioOf
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField

@Composable
fun ResistorRatioPage() {
    var findPair by rememberSaveable { mutableStateOf(true) }
    var kind by rememberSaveable { mutableStateOf(RatioKind.R2ToR1) }
    var targetText by rememberSaveable { mutableStateOf("2.5") }
    var r1Text by rememberSaveable { mutableStateOf("10 k") }
    var r2Text by rememberSaveable { mutableStateOf("22 k") }
    var series by remember { mutableStateOf(ESeries.E24) }

    CalculatorPage(
        stringResource(R.string.fp_resistor_ratio),
        formulas = listOf(
            Formula("""k = \frac{R_{2}}{R_{1}}""", "R2 / R1"),
            Formula("""k = \frac{R_{2}}{R_{1} + R_{2}}""", "R2 / (R1 + R2)"),
            Formula("""δ = \frac{k − k_{target}}{k_{target}} · 100 %""", stringResource(R.string.ec_error)),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.Tune,
            selected = findPair,
            options = listOf(true to stringResource(R.string.ec_find_pair), false to stringResource(R.string.ec_two_resistors)),
            onSelect = { findPair = it }
        )
        OptionSetting(
            title = stringResource(R.string.ec_ratio),
            icon = Icons.Default.Balance,
            selected = kind,
            options = listOf(RatioKind.R2ToR1 to "R2 / R1", RatioKind.Divider to "R2 / (R1 + R2)"),
            onSelect = {
                // The target as the same ratio of the other kind, or nothing where there's none (a divider's is under 1)
                targetText = parseValue(targetText)?.let { value -> convertRatio(value, kind, it) }?.let(::plain).orEmpty()
                kind = it
            }
        )

        RevealSection(findPair) {
            UnitField(stringResource(R.string.ec_target_ratio), targetText, { targetText = it })
            ESeriesSetting(series) { series = it }
            val target = parseValue(targetText)?.takeIf { it > 0 }
            val pairs = remember(target, kind, series) { target?.let { bestPairs(it, kind, series) }.orEmpty() }
            Results {
                pairs.forEach { pair ->
                    ResultRow(
                        "${formatValue(pair.r1, "Ω")} : ${formatValue(pair.r2, "Ω")}",
                        String.format(Locale.US, "%.4f  (%+.2f %%)", pair.ratio, pair.error * 100)
                    )
                }
            }
            Note(stringResource(R.string.ec_ratio_scale), visible = pairs.isNotEmpty())
            Note(stringResource(R.string.ec_divider_under_one), visible = target != null && pairs.isEmpty())
        }
        RevealSection(!findPair) {
            UnitField(stringResource(R.string.ec_r1), r1Text, { r1Text = it }, "Ω")
            UnitField(stringResource(R.string.ec_r2), r2Text, { r2Text = it }, "Ω")
            val r1 = parseValue(r1Text)
            val r2 = parseValue(r2Text)
            Results {
                if (r1 != null && r2 != null && r1 > 0 && r2 > 0) {
                    val ratio = ratioOf(r1, r2, kind)
                    MainResult(stringResource(R.string.ec_ratio), String.format(Locale.US, "%.5f", ratio))
                    ResultRow(stringResource(R.string.ec_gain_db), String.format(Locale.US, "%.2f dB", 20 * log10(ratio)))
                    ResultRow(stringResource(R.string.ec_total_resistance), formatValue(r1 + r2, "Ω"))
                }
            }
        }
    }
}

/** [value] as a field shows a ratio: five significant figures, no trailing zeros. */
private fun plain(value: Double) = BigDecimal(value).round(MathContext(5)).stripTrailingZeros().toPlainString()
