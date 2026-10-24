package org.lukemech.lmtoolbox.ui.pages.features.electronics.pt100

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.pt100.resistanceAt
import org.lukemech.lmtoolbox.features.electronics.pt100.temperatureAt
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Sensor { Temperature, Resistance }

@Composable
fun Pt100Page() {
    var r0 by rememberSaveable { mutableDoubleStateOf(100.0) }
    val fields = rememberLinkedFields(1, Sensor.Temperature to "25")

    // Within the standard's range: −200–850 °C
    val values = fields.solve { given ->
        given[Sensor.Temperature]?.takeIf { it in -200.0..850.0 }?.let { mapOf(Sensor.Resistance to resistanceAt(it, r0)) }
            ?: given[Sensor.Resistance]?.takeIf { it > 0.18 * r0 && it < 4 * r0 }?.let { mapOf(Sensor.Temperature to temperatureAt(it, r0)) }
            ?: emptyMap()
    }

    CalculatorPage(
        stringResource(R.string.fp_pt100),
        formulas = listOf(
            Formula("""R(t) = R_{0} · (1 + At + Bt^{2} + C(t − 100)t^{3})""", "Callendar–Van Dusen"),
            Formula("""A = 3.9083 · 10^{−3}"""),
            Formula("""B = −5.775 · 10^{−7}"""),
            Formula("""C = −4.183 · 10^{−12} \text{  (t < 0 °C)}"""),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_sensor),
            icon = Icons.Default.Sensors,
            selected = r0,
            options = listOf(100.0 to "PT100", 500.0 to "PT500", 1000.0 to "PT1000"),
            onSelect = { r0 = it }
        )
        LinkedField(fields, values, Sensor.Temperature, stringResource(R.string.ec_temp), "°C", sigFigs = 5)
        LinkedField(fields, values, Sensor.Resistance, stringResource(R.string.ec_resistance), "Ω", sigFigs = 5)
    }
}
