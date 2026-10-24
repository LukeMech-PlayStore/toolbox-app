package org.lukemech.lmtoolbox.ui.pages.features.electronics.decibel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.log10
import kotlin.math.pow
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Gain { Db, Ratio }

@Composable
fun DecibelPage() {
    var voltage by rememberSaveable { mutableStateOf(false) }
    val fields = rememberLinkedFields(1, Gain.Db to "3")

    // A power ratio is 10·log10, a voltage (or current) ratio 20·log10
    val factor = if (voltage) 20.0 else 10.0
    val values = fields.solve { given ->
        given[Gain.Db]?.let { mapOf(Gain.Ratio to 10.0.pow(it / factor)) }
            ?: given[Gain.Ratio]?.takeIf { it > 0 }?.let { mapOf(Gain.Db to factor * log10(it)) }
            ?: emptyMap()
    }

    CalculatorPage(
        stringResource(R.string.fp_decibel),
        formulas = listOf(
            Formula("""G = 10 · \text{log}_{10} \frac{P_{2}}{P_{1}}""", stringResource(R.string.ec_power)),
            Formula("""\frac{P_{2}}{P_{1}} = 10^{G/10}"""),
            Formula("""G = 20 · \text{log}_{10} \frac{U_{2}}{U_{1}}""", stringResource(R.string.ec_voltage)),
            Formula("""\frac{U_{2}}{U_{1}} = 10^{G/20}"""),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_quantity),
            icon = Icons.Default.Tune,
            selected = voltage,
            options = listOf(false to stringResource(R.string.ec_power), true to stringResource(R.string.ec_voltage)),
            onSelect = { voltage = it }
        )
        LinkedField(fields, values, Gain.Db, stringResource(R.string.ec_db), "dB")
        LinkedField(fields, values, Gain.Ratio, stringResource(R.string.ec_ratio))
    }
}
