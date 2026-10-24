package org.lukemech.lmtoolbox.ui.pages.features.electronics.color_code

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.color_code.significantDigits
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.fieldText
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Nearest
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField

/**
 * A color of the code and what it stands for in each band: a digit, the multiplier's power of ten,
 * a tolerance (%) and a temperature coefficient (ppm/K); null where it can't be that band.
 */
private enum class BandColor(
    @param:StringRes val label: Int,
    val color: Color,
    val digit: Int?,
    val exponent: Int,
    val tolerance: Double?,
    val tempco: Int?,
) {
    Black(R.string.ec_black, Color(0xFF1A1A1A), 0, 0, null, 250),
    Brown(R.string.ec_brown, Color(0xFF8B4513), 1, 1, 1.0, 100),
    Red(R.string.ec_red, Color(0xFFDC143C), 2, 2, 2.0, 50),
    Orange(R.string.ec_orange, Color(0xFFFF8C00), 3, 3, null, 15),
    Yellow(R.string.ec_yellow, Color(0xFFFFD700), 4, 4, null, 25),
    Green(R.string.ec_green, Color(0xFF228B22), 5, 5, 0.5, 20),
    Blue(R.string.ec_blue, Color(0xFF4169E1), 6, 6, 0.25, 10),
    Violet(R.string.ec_violet, Color(0xFF8B008B), 7, 7, 0.1, 5),
    Grey(R.string.ec_grey, Color(0xFF808080), 8, 8, 0.05, 1),
    White(R.string.ec_white, Color(0xFFF5F5F5), 9, 9, null, null),
    Gold(R.string.ec_gold, Color(0xFFDAA520), null, -1, 5.0, null),
    Silver(R.string.ec_silver, Color(0xFFC0C0C0), null, -2, 10.0, null);

    companion object {
        val digits = entries.filter { it.digit != null }
        fun ofDigit(d: Int) = digits[d]
        fun ofExponent(e: Int) = entries.first { it.exponent == e }
    }
}

/** A band of a code: its [color] and what it stands for ([label]); the tolerance band is [wide]. */
private data class Band(val color: BandColor, @param:StringRes val label: Int, val wide: Boolean = false)

private val DIGIT_LABELS = listOf(R.string.ec_band1, R.string.ec_band2, R.string.ec_band3)

/**
 * The [bandCount] bands of [value] (in Ω, or in μH for an [inductor]): 5% with 4 bands, 1% with
 * more, 100 ppm/K as the 6th; empty when it can't be color coded.
 */
private fun bandsOf(value: Double, bandCount: Int, inductor: Boolean): List<Band> {
    val (significant, exponent) = significantDigits(value, if (bandCount == 4) 2 else 3) ?: return emptyList()
    return significant.mapIndexed { i, d -> Band(BandColor.ofDigit(d), DIGIT_LABELS[i]) } +
        Band(BandColor.ofExponent(exponent), R.string.ec_multiplier) +
        Band(if (bandCount == 4 || inductor) BandColor.Gold else BandColor.Brown, R.string.ec_tolerance, wide = true) +
        listOfNotNull(if (bandCount == 6) Band(BandColor.Brown, R.string.ec_tempco) else null)
}

@Composable
private fun BandDots(bands: List<Band>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        bands.forEach {
            Box(Modifier.size(if (it.wide) 36.dp else 28.dp).clip(CircleShape).background(it.color.color))
        }
    }
}

/** A band to pick, of [colors], as the app's settings are. */
@Composable
private fun BandSetting(@StringRes title: Int, selected: BandColor, colors: List<BandColor>, describe: @Composable (BandColor) -> String, onSelect: (BandColor) -> Unit) =
    OptionSetting(
        title = stringResource(title),
        icon = Icons.Default.Palette,
        selected = selected,
        options = colors.map { it to describe(it) },
        onSelect = onSelect
    )

@Composable
fun ColorCodePage() {
    var inductor by rememberSaveable { mutableStateOf(false) }
    var toBands by rememberSaveable { mutableStateOf(true) }
    var bandCount by rememberSaveable { mutableIntStateOf(4) }
    var series by remember { mutableStateOf(ESeries.E24) }
    var valueText by rememberSaveable { mutableStateOf("4.7 k") }
    val digits = remember { mutableStateListOf(BandColor.Yellow, BandColor.Violet, BandColor.Black) }
    var multiplier by rememberSaveable { mutableStateOf(BandColor.Red) }
    var tolerance by rememberSaveable { mutableStateOf(BandColor.Gold) }
    var tempco by rememberSaveable { mutableStateOf(BandColor.Brown) }

    if (inductor && bandCount == 6) bandCount = 5
    val unit = if (inductor) "H" else "Ω"

    CalculatorPage(
        stringResource(R.string.fp_color_code),
        formulas = listOf(
            Formula("""R = (10d_{1} + d_{2}) · 10^{m}""", "${stringResource(R.string.ec_bands)}: 4"),
            Formula("""R = (100d_{1} + 10d_{2} + d_{3}) · 10^{m}""", "${stringResource(R.string.ec_bands)}: 5, 6"),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_component),
            icon = Icons.Default.Category,
            selected = inductor,
            options = listOf(false to stringResource(R.string.ec_resistor), true to stringResource(R.string.ec_inductor)),
            onSelect = {
                // Each its own typical value: the prefixes of one (kΩ) aren't the other's (μH)
                if (it != inductor) valueText = if (it) "10 μ" else "4.7 k"
                inductor = it
            }
        )
        OptionSetting(
            title = stringResource(R.string.ec_direction),
            icon = Icons.Default.SwapHoriz,
            selected = toBands,
            options = listOf(true to stringResource(R.string.ec_value_to_bands), false to stringResource(R.string.ec_bands_to_value)),
            onSelect = { toBands = it }
        )
        OptionSetting(
            title = stringResource(R.string.ec_bands),
            icon = Icons.Default.ViewWeek,
            selected = bandCount,
            options = (if (inductor) listOf(4, 5) else listOf(4, 5, 6)).map { it to it.toString() },
            onSelect = { bandCount = it }
        )

        RevealSection(toBands) {
            val typed = parseValue(valueText)?.takeIf { it > 0 }
            // A resistor's bands are its nearest standard value's
            UnitField(stringResource(R.string.ec_value), valueText, { valueText = it }, unit)
            RevealSection(!inductor) {
                ESeriesSetting(series, listOf(Nearest(typed) { valueText = fieldText(it, unit) })) { series = it }
            }
            val value = typed?.let { if (inductor) it else ESeries.nearest(series, it) }
            val bands = value?.let { bandsOf(if (inductor) it * 1e6 else it, bandCount, inductor) }.orEmpty()
            Results {
                if (bands.isNotEmpty()) {
                    BandDots(bands)
                    bands.forEach { ResultRow(stringResource(it.label), stringResource(it.color.label)) }
                }
            }
            Note(stringResource(R.string.ec_no_color_code), visible = value != null && bands.isEmpty())
        }
        RevealSection(!toBands) {
            val digitCount = if (bandCount == 4) 2 else 3
            val tolerances = if (inductor) listOf(BandColor.Gold, BandColor.Silver) else BandColor.entries.filter { it.tolerance != null }
            if (tolerance !in tolerances) tolerance = BandColor.Gold

            repeat(digitCount) { i ->
                BandSetting(DIGIT_LABELS[i], digits[i], BandColor.digits, { "${stringResource(it.label)} (${it.digit})" }) { digits[i] = it }
            }
            BandSetting(R.string.ec_multiplier, multiplier, BandColor.entries, { "${stringResource(it.label)} (×${formatValue(10.0.pow(it.exponent), "")})" }) { multiplier = it }
            BandSetting(R.string.ec_tolerance, tolerance, tolerances, { "${stringResource(it.label)} (±${formatValue(it.tolerance!!, "")}%)" }) { tolerance = it }
            RevealSection(bandCount == 6) {
                BandSetting(R.string.ec_tempco, tempco, BandColor.entries.filter { it.tempco != null }, { "${stringResource(it.label)} (${it.tempco} ppm/K)" }) { tempco = it }
            }

            val picked = (0 until digitCount).map { Band(digits[it], DIGIT_LABELS[it]) } +
                Band(multiplier, R.string.ec_multiplier) + Band(tolerance, R.string.ec_tolerance, wide = true) +
                listOfNotNull(if (bandCount == 6) Band(tempco, R.string.ec_tempco) else null)
            val significant = (0 until digitCount).fold(0) { acc, i -> acc * 10 + digits[i].digit!! }
            val value = significant * 10.0.pow(multiplier.exponent) * (if (inductor) 1e-6 else 1.0)
            Results {
                MainResult(stringResource(R.string.ec_value), formatValue(value, unit))
                BandDots(picked)
                ResultRow(stringResource(R.string.ec_tolerance), "±${formatValue(tolerance.tolerance!!, "")}%")
                if (bandCount == 6) ResultRow(stringResource(R.string.ec_tempco), "${tempco.tempco} ppm/K")
            }
        }
    }
}
