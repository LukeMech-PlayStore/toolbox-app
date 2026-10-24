package org.lukemech.lmtoolbox.ui.pages.features.electronics.series_parallel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.features.electronics.series_parallel.findPairs
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField

@Composable
fun SeriesParallelPage() {
    var capacitors by rememberSaveable { mutableStateOf(false) }
    var findPair by rememberSaveable { mutableStateOf(false) }
    var aText by rememberSaveable { mutableStateOf("10 k") }
    var bText by rememberSaveable { mutableStateOf("22 k") }
    var targetText by rememberSaveable { mutableStateOf("1.5 k") }
    var series by remember { mutableStateOf(ESeries.E24) }

    val unit = if (capacitors) "F" else "Ω"
    // Resistors in series and capacitors in parallel add up; the other way, their reciprocals do
    fun sum(a: Double, b: Double) = a + b
    fun reciprocal(a: Double, b: Double) = a * b / (a + b)
    val inSeries: (Double, Double) -> Double = if (capacitors) ::reciprocal else ::sum
    val inParallel: (Double, Double) -> Double = if (capacitors) ::sum else ::reciprocal

    CalculatorPage(
        stringResource(R.string.fp_series_parallel),
        formulas = listOf(
            Formula("""R = R_{1} + R_{2}""", "${stringResource(R.string.ec_resistors)}: ${stringResource(R.string.ec_series)}"),
            Formula("""R = \frac{R_{1} · R_{2}}{R_{1} + R_{2}}""", "${stringResource(R.string.ec_resistors)}: ${stringResource(R.string.ec_parallel)}"),
            Formula("""C = \frac{C_{1} · C_{2}}{C_{1} + C_{2}}""", "${stringResource(R.string.ec_capacitors)}: ${stringResource(R.string.ec_series)}"),
            Formula("""C = C_{1} + C_{2}""", "${stringResource(R.string.ec_capacitors)}: ${stringResource(R.string.ec_parallel)}"),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_component),
            icon = Icons.Default.Category,
            selected = capacitors,
            options = listOf(false to stringResource(R.string.ec_resistors), true to stringResource(R.string.ec_capacitors)),
            onSelect = {
                capacitors = it
                aText = if (it) "100 n" else "10 k"
                bText = if (it) "220 n" else "22 k"
                targetText = if (it) "150 n" else "1.5 k"
            }
        )
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.Tune,
            selected = findPair,
            options = listOf(false to stringResource(R.string.ec_two_values), true to stringResource(R.string.ec_find_pair)),
            onSelect = { findPair = it }
        )

        RevealSection(!findPair) {
            UnitField(stringResource(R.string.ec_first), aText, { aText = it }, unit)
            UnitField(stringResource(R.string.ec_second), bText, { bText = it }, unit)
            val a = parseValue(aText)
            val b = parseValue(bText)
            Results {
                if (a != null && b != null && a > 0 && b > 0) {
                    MainResult(stringResource(R.string.ec_series), formatValue(inSeries(a, b), unit))
                    MainResult(stringResource(R.string.ec_parallel), formatValue(inParallel(a, b), unit))
                    ResultRow(stringResource(R.string.ec_ratio), String.format(Locale.US, "%.4f", a / b))
                }
            }
        }
        RevealSection(findPair) {
            UnitField(stringResource(R.string.ec_target), targetText, { targetText = it }, unit)
            ESeriesSetting(series) { series = it }
            val target = parseValue(targetText)
            if (target != null && target > 0) {
                PairsSection(stringResource(R.string.ec_series), "+", findPairs(series, target, inSeries), unit, inSeries)
                PairsSection(stringResource(R.string.ec_parallel), "∥", findPairs(series, target, inParallel), unit, inParallel)
            }
        }
    }
}

@Composable
private fun PairsSection(title: String, symbol: String, pairs: List<Pair<Double, Double>>, unit: String, combine: (Double, Double) -> Double) {
    SectionTitle(title)
    Note(stringResource(R.string.ec_no_combos), visible = pairs.isEmpty())
    Results {
        pairs.forEach { (a, b) ->
            ResultRow("${formatValue(a, unit)} $symbol ${formatValue(b, unit)}", formatValue(combine(a, b), unit))
        }
    }
}
