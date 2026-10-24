package org.lukemech.lmtoolbox.ui.pages.features.electronics.adc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.floor
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Reading { Code, Voltage }

@Composable
fun AdcPage() {
    var bits by rememberSaveable { mutableIntStateOf(10) }
    var vrefText by rememberSaveable { mutableStateOf("3.3") }
    val fields = rememberLinkedFields(1, Reading.Code to "512")

    val vref = parseValue(vrefText)?.takeIf { it > 0 }
    val steps = 1L shl bits
    val values = fields.solve { given ->
        if (vref == null) return@solve emptyMap()
        val lsb = vref / steps
        given[Reading.Code]?.takeIf { it >= 0 && it < steps && it == floor(it) }?.let { mapOf(Reading.Voltage to it * lsb) }
            ?: given[Reading.Voltage]?.let { mapOf(Reading.Code to floor(it / lsb).coerceIn(0.0, steps - 1.0)) }
            ?: emptyMap()
    }

    CalculatorPage(
        stringResource(R.string.fp_adc),
        formulas = listOf(
            Formula("""U = \frac{N}{2^{n}} · U_{ref}""", stringResource(R.string.ec_voltage)),
            Formula("""N = \frac{U}{U_{ref}} · 2^{n}""", stringResource(R.string.ec_code)),
            Formula("""\text{LSB} = \frac{U_{ref}}{2^{n}}""", stringResource(R.string.ec_lsb)),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_bits),
            icon = Icons.Default.DataArray,
            selected = bits,
            options = listOf(8, 10, 12, 14, 16, 24).map { it to "$it bit" },
            onSelect = { bits = it }
        )
        UnitField(stringResource(R.string.ec_vref), vrefText, { vrefText = it }, "V")
        // Every digit of a code
        LinkedField(fields, values, Reading.Code, stringResource(R.string.ec_code), sigFigs = 10)
        LinkedField(fields, values, Reading.Voltage, stringResource(R.string.ec_voltage), "V")

        Results {
            if (vref != null) {
                ResultRow(stringResource(R.string.ec_lsb), formatValue(vref / steps, "V"))
                ResultRow(stringResource(R.string.ec_levels), steps.toString())
            }
        }
    }
}
