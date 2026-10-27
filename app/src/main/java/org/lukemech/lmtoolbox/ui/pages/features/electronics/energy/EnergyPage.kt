package org.lukemech.lmtoolbox.ui.pages.features.electronics.energy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import java.math.MathContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatNumber
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.features.electronics.energy.EnergyUnit
import org.lukemech.lmtoolbox.features.electronics.energy.convertEnergy
import org.lukemech.lmtoolbox.features.electronics.energy.wattHours
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitChoiceField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Battery { Charge, Voltage, Energy }

@Composable
fun EnergyPage() {
    var valueText by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(EnergyUnit.KWh) }
    val battery = rememberLinkedFields(2, Battery.Charge to "2000", Battery.Voltage to "3.7")

    val value = parseValue(valueText)
    // Wh = mAh · V / 1000, any of them from the other two
    val batteryValues = battery.solve { given ->
        val charge = given[Battery.Charge]
        val voltage = given[Battery.Voltage]
        val energy = given[Battery.Energy]
        when {
            charge != null && voltage != null -> mapOf(Battery.Energy to wattHours(charge, voltage))
            energy != null && voltage != null -> mapOf(Battery.Charge to energy * 1000.0 / voltage)
            energy != null && charge != null -> mapOf(Battery.Voltage to energy * 1000.0 / charge)
            else -> emptyMap()
        }
    }

    CalculatorPage(
        stringResource(R.string.fp_energy),
        formulas = listOf(
            Formula("""1 \text{ Wh} = 3600 \text{ J}"""),
            Formula("""1 \text{ cal} = 4.184 \text{ J}""", stringResource(R.string.ec_cal_thermochemical)),
            Formula("""1 \text{ BTU} = 1055.06 \text{ J}""", stringResource(R.string.ec_btu_it)),
            Formula("""1 \text{ eV} = 1.602176634 · 10^{−19} \text{ J}"""),
            Formula("""E = \frac{Q · U}{1000}""", stringResource(R.string.ec_battery_energy)),
        )
    ) {
        UnitChoiceField(stringResource(R.string.ec_energy), valueText, { valueText = it }, unit.symbol, EnergyUnit.entries.map { it.symbol }) { picked ->
            val to = EnergyUnit.entries.first { it.symbol == picked }
            // The same energy in the new unit, as a plain number
            valueText = parseValue(valueText)?.let { plain(convertEnergy(it, unit, to)) }.orEmpty()
            unit = to
        }
        Results {
            if (value != null) {
                EnergyUnit.entries.filter { it != unit }.forEach { other ->
                    ResultRow(other.symbol, formatNumber(convertEnergy(value, unit, other), sigFigs = 6))
                }
            }
        }

        SectionTitle(stringResource(R.string.ec_battery_energy))
        LinkedField(battery, batteryValues, Battery.Charge, stringResource(R.string.ec_charge_mah), "mAh", sigFigs = 5)
        LinkedField(battery, batteryValues, Battery.Voltage, stringResource(R.string.ec_nominal_voltage), "V")
        LinkedField(battery, batteryValues, Battery.Energy, stringResource(R.string.ec_energy), "Wh", sigFigs = 5)
    }
}

/** [value] to six significant figures, with no exponent: a field holds digits alone. */
private fun plain(value: Double) = BigDecimal(value).round(MathContext(6)).stripTrailingZeros().toPlainString()
