package org.lukemech.lmtoolbox.ui.pages.features.electronics.regulator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.fieldText
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Nearest
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

/** The LM317's reference voltage, between its output and adjust pins. */
private const val VREF = 1.25

private enum class Adjust { Vout, R1, R2 }

@Composable
fun RegulatorPage() {
    var vinText by rememberSaveable { mutableStateOf("12") }
    var ioutText by rememberSaveable { mutableStateOf("500 m") }
    val fields = rememberLinkedFields(2, Adjust.Vout to "5", Adjust.R1 to "240")
    var series by remember { mutableStateOf(ESeries.E24) }

    val vin = parseValue(vinText)
    val iout = parseValue(ioutText)
    // Vout = Vref · (1 + R2/R1)
    val values = fields.solve { given ->
        val vout = given[Adjust.Vout]
        val r1 = given[Adjust.R1]
        val r2 = given[Adjust.R2]
        when {
            r1 != null && r2 != null -> mapOf(Adjust.Vout to VREF * (1 + r2 / r1))
            vout != null && r1 != null -> mapOf(Adjust.R2 to r1 * (vout / VREF - 1.0))
            vout != null && r2 != null -> mapOf(Adjust.R1 to r2 / (vout / VREF - 1.0))
            else -> emptyMap()
        }.filterValues { it > 0 }
    }
    val vout = values[Adjust.Vout]

    CalculatorPage(
        stringResource(R.string.fp_regulator),
        formulas = listOf(
            Formula("""U_{out} = 1.25 \text{ V} · (1 + \frac{R_{2}}{R_{1}})""", stringResource(R.string.ec_vout)),
            Formula("""R_{2} = R_{1} · (\frac{U_{out}}{1.25 \text{ V}} − 1)""", stringResource(R.string.ec_r2)),
            Formula("""P = (U_{in} − U_{out}) · I_{out}""", stringResource(R.string.ec_dissipation)),
        )
    ) {
        UnitField(stringResource(R.string.ec_vin), vinText, { vinText = it }, "V")
        LinkedField(fields, values, Adjust.Vout, stringResource(R.string.ec_vout), "V")
        UnitField(stringResource(R.string.ec_iout), ioutText, { ioutText = it }, "A")
        LinkedField(fields, values, Adjust.R1, stringResource(R.string.ec_r1), "Ω")
        LinkedField(fields, values, Adjust.R2, stringResource(R.string.ec_r2), "Ω")
        ESeriesSetting(
            series,
            listOf(
                Nearest(values[Adjust.R1], stringResource(R.string.ec_r1)) { fields.edit(Adjust.R1, fieldText(it, "Ω")) },
                Nearest(values[Adjust.R2], stringResource(R.string.ec_r2)) { fields.edit(Adjust.R2, fieldText(it, "Ω")) },
            )
        ) { series = it }

        Results {
            if (vin != null && vout != null && iout != null) ResultRow(stringResource(R.string.ec_dissipation), formatValue((vin - vout) * iout, "W"))
        }
        Note(stringResource(R.string.ec_dropout_note), visible = vin != null && vout != null && vin - vout < 3)
    }
}
