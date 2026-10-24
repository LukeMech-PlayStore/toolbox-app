package org.lukemech.lmtoolbox.ui.pages.features.electronics.led_resistor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Nearest
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Led { Current, Resistor }

@Composable
fun LedResistorPage() {
    var vccText by rememberSaveable { mutableStateOf("5") }
    var vfText by rememberSaveable { mutableStateOf("2") }
    var count by rememberSaveable { mutableIntStateOf(1) }
    val fields = rememberLinkedFields(1, Led.Current to "20 m")
    var series by remember { mutableStateOf(ESeries.E24) }

    val vcc = parseValue(vccText)
    val vf = parseValue(vfText)
    // What the resistor takes: R = drop / I
    val drop = if (vcc != null && vf != null) vcc - vf * count else null
    val values = fields.solve { given ->
        if (drop == null || drop <= 0) return@solve emptyMap()
        given[Led.Current]?.takeIf { it > 0 }?.let { mapOf(Led.Resistor to drop / it) }
            ?: given[Led.Resistor]?.takeIf { it > 0 }?.let { mapOf(Led.Current to drop / it) }
            ?: emptyMap()
    }
    val current = values[Led.Current]

    CalculatorPage(
        stringResource(R.string.fp_led_resistor),
        formulas = listOf(
            Formula("""R = \frac{U_{CC} − n · U_{F}}{I_{F}}""", stringResource(R.string.ec_resistance)),
            Formula("""P = (U_{CC} − n · U_{F}) · I_{F}""", stringResource(R.string.ec_resistor_power)),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_led_count),
            icon = Icons.Default.Lightbulb,
            selected = count,
            options = (1..6).map { it to it.toString() },
            onSelect = { count = it }
        )
        UnitField(stringResource(R.string.ec_vcc), vccText, { vccText = it }, "V")
        UnitField(stringResource(R.string.ec_vf), vfText, { vfText = it }, "V")
        LinkedField(fields, values, Led.Current, stringResource(R.string.ec_if), "A")
        LinkedField(fields, values, Led.Resistor, stringResource(R.string.ec_resistance), "Ω")
        ESeriesSetting(series, listOf(Nearest(values[Led.Resistor]) { fields.edit(Led.Resistor, fieldText(it, "Ω")) })) { series = it }

        Note(stringResource(R.string.ec_led_too_many), visible = drop != null && drop <= 0)
        Results {
            if (drop != null && drop > 0 && current != null) ResultRow(stringResource(R.string.ec_resistor_power), formatValue(drop * current, "W"))
        }
    }
}
