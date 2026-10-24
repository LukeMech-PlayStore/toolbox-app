package org.lukemech.lmtoolbox.ui.pages.features.electronics.power

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.max
import kotlin.math.sqrt
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Supply { DC, AC1, AC3 }

private enum class Power { U, I, P }

@Composable
fun PowerPage() {
    var supply by rememberSaveable { mutableStateOf(Supply.AC1) }
    var pfText by rememberSaveable { mutableStateOf("1") }
    val fields = rememberLinkedFields(2, Power.U to "230", Power.I to "10")

    val pf = if (supply == Supply.DC) 1.0 else (parseValue(pfText) ?: 1.0).coerceIn(0.0, 1.0)
    // P = k·U·I: √3 for three phases, and the power factor
    val k = (if (supply == Supply.AC3) sqrt(3.0) else 1.0) * pf
    val values = fields.solve { given ->
        val u = given[Power.U]
        val i = given[Power.I]
        val p = given[Power.P]
        when {
            u != null && i != null -> mapOf(Power.P to k * u * i)
            u != null && p != null -> mapOf(Power.I to p / (k * u))
            i != null && p != null -> mapOf(Power.U to p / (k * i))
            else -> emptyMap()
        }
    }

    CalculatorPage(
        stringResource(R.string.fp_power),
        formulas = listOf(
            Formula("""P = U · I""", stringResource(R.string.ec_dc)),
            Formula("""P = U · I · \text{cos} φ""", stringResource(R.string.ec_ac_1phase)),
            Formula("""P = \sqrt{3} · U · I · \text{cos} φ""", stringResource(R.string.ec_ac_3phase)),
            Formula("""S = \frac{P}{\text{cos} φ}""", stringResource(R.string.ec_apparent)),
            Formula("""Q = \sqrt{S^{2} − P^{2}}""", stringResource(R.string.ec_reactive)),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_current_type),
            icon = Icons.Default.ElectricalServices,
            selected = supply,
            options = listOf(
                Supply.DC to stringResource(R.string.ec_dc),
                Supply.AC1 to stringResource(R.string.ec_ac_1phase),
                Supply.AC3 to stringResource(R.string.ec_ac_3phase),
            ),
            onSelect = { supply = it }
        )
        RevealSection(supply != Supply.DC) { UnitField(stringResource(R.string.ec_pf), pfText, { pfText = it }) }
        LinkedField(fields, values, Power.U, stringResource(R.string.ec_voltage), "V")
        LinkedField(fields, values, Power.I, stringResource(R.string.ec_current), "A")
        LinkedField(fields, values, Power.P, stringResource(R.string.ec_active), "W")

        Results {
            val u = values[Power.U]
            val i = values[Power.I]
            if (supply != Supply.DC && u != null && i != null) {
                val apparent = (if (supply == Supply.AC3) sqrt(3.0) else 1.0) * u * i
                ResultRow(stringResource(R.string.ec_apparent), formatValue(apparent, "VA"))
                ResultRow(stringResource(R.string.ec_reactive), formatValue(apparent * sqrt(max(0.0, 1.0 - pf * pf)), "var"))
            }
        }
    }
}
