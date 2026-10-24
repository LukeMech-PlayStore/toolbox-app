package org.lukemech.lmtoolbox.ui.pages.features.electronics.frequency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.PI
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private const val SPEED_OF_LIGHT = 299_792_458.0

private enum class Wave { F, T, Lambda }

@Composable
fun FrequencyPage() {
    var factorText by rememberSaveable { mutableStateOf("1") }
    val fields = rememberLinkedFields(1, Wave.F to "100 M")

    val velocityFactor = parseValue(factorText)?.takeIf { it > 0 }
    // Any of them gives the frequency, the frequency the others
    val values = fields.solve { given ->
        val f = given[Wave.F] ?: given[Wave.T]?.let { 1.0 / it }
            ?: given[Wave.Lambda]?.let { l -> velocityFactor?.let { SPEED_OF_LIGHT * it / l } }
            ?: return@solve emptyMap()
        mapOf(Wave.F to f, Wave.T to 1.0 / f) + listOfNotNull(velocityFactor?.let { Wave.Lambda to SPEED_OF_LIGHT * it / f })
    }
    val f = values[Wave.F]?.takeIf { it > 0 }
    val wavelength = values[Wave.Lambda]

    CalculatorPage(
        stringResource(R.string.fp_frequency),
        formulas = listOf(
            Formula("""T = \frac{1}{f}""", stringResource(R.string.ec_period)),
            Formula("""ω = 2πf""", stringResource(R.string.ec_angular)),
            Formula("""λ = \frac{c · v_{f}}{f}""", stringResource(R.string.ec_wavelength)),
            Formula("""\frac{λ}{4}""", stringResource(R.string.ec_quarter_wave)),
            Formula("""c = 299 792 458 \text{ m/s}"""),
        )
    ) {
        LinkedField(fields, values, Wave.F, stringResource(R.string.ec_freq), "Hz")
        LinkedField(fields, values, Wave.T, stringResource(R.string.ec_period), "s")
        UnitField(stringResource(R.string.ec_velocity_factor), factorText, { factorText = it })
        LinkedField(fields, values, Wave.Lambda, stringResource(R.string.ec_wavelength), "m")
        Results {
            if (f != null) ResultRow(stringResource(R.string.ec_angular), formatValue(2 * PI * f, "rad/s"))
            if (wavelength != null) ResultRow(stringResource(R.string.ec_quarter_wave), formatValue(wavelength / 4, "m"))
        }
    }
}
