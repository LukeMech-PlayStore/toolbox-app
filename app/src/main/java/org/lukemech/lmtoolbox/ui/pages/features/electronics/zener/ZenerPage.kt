package org.lukemech.lmtoolbox.ui.pages.features.electronics.zener

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
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Nearest
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Zener { DiodeCurrent, Resistor }

@Composable
fun ZenerPage() {
    var vinText by rememberSaveable { mutableStateOf("12") }
    var vzText by rememberSaveable { mutableStateOf("5.1") }
    var ioutText by rememberSaveable { mutableStateOf("10 m") }
    val fields = rememberLinkedFields(1, Zener.DiodeCurrent to "5 m")
    var series by remember { mutableStateOf(ESeries.E24) }

    val vin = parseValue(vinText)
    val vz = parseValue(vzText)
    val iout = parseValue(ioutText)
    // The resistor carries the diode's and the load's current: R = (Vin − Vz) / (Iz + Iout)
    val drop = if (vin != null && vz != null && vin > vz) vin - vz else null
    val values = fields.solve { given ->
        if (drop == null || iout == null) return@solve emptyMap()
        given[Zener.DiodeCurrent]?.takeIf { it + iout > 0 }?.let { mapOf(Zener.Resistor to drop / (it + iout)) }
            ?: given[Zener.Resistor]?.takeIf { it > 0 }?.let { r -> (drop / r - iout).takeIf { it > 0 }?.let { mapOf(Zener.DiodeCurrent to it) } }
            ?: emptyMap()
    }
    val iz = values[Zener.DiodeCurrent]

    CalculatorPage(
        stringResource(R.string.fp_zener),
        formulas = listOf(
            Formula("""R = \frac{U_{in} − U_{Z}}{I_{Z} + I_{L}}""", stringResource(R.string.ec_resistance)),
            Formula("""P_{R} = (U_{in} − U_{Z}) · (I_{Z} + I_{L})""", stringResource(R.string.ec_resistor_power)),
            Formula("""P_{Z} = U_{Z} · (I_{Z} + I_{L})""", stringResource(R.string.ec_zener_power)),
        )
    ) {
        UnitField(stringResource(R.string.ec_vin), vinText, { vinText = it }, "V")
        UnitField(stringResource(R.string.ec_vz), vzText, { vzText = it }, "V")
        UnitField(stringResource(R.string.ec_iout), ioutText, { ioutText = it }, "A")
        LinkedField(fields, values, Zener.DiodeCurrent, stringResource(R.string.ec_iz), "A")
        LinkedField(fields, values, Zener.Resistor, stringResource(R.string.ec_resistance), "Ω")
        ESeriesSetting(series, listOf(Nearest(values[Zener.Resistor]) { fields.edit(Zener.Resistor, fieldText(it, "Ω")) })) { series = it }

        Results {
            if (drop != null && vz != null && iz != null && iout != null) {
                val current = iz + iout
                ResultRow(stringResource(R.string.ec_resistor_power), formatValue(drop * current, "W"))
                // Without a load, all of it goes through the diode
                ResultRow(stringResource(R.string.ec_zener_power), formatValue(vz * current, "W"))
            }
        }
    }
}
