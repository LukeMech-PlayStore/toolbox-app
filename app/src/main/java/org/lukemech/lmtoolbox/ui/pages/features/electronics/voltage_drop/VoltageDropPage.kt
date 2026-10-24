package org.lukemech.lmtoolbox.ui.pages.features.electronics.voltage_drop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.PI
import kotlin.math.pow
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatPercent
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitChoiceField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField

@Composable
fun VoltageDropPage() {
    var aluminium by rememberSaveable { mutableStateOf(false) }
    var awg by rememberSaveable { mutableStateOf(false) }
    var sizeText by rememberSaveable { mutableStateOf("1.5") }
    var lenText by rememberSaveable { mutableStateOf("10") }
    var vText by rememberSaveable { mutableStateOf("230") }
    var iText by rememberSaveable { mutableStateOf("10") }

    val size = parseValue(sizeText)
    val len = parseValue(lenText)
    val v = parseValue(vText)
    val current = parseValue(iText)

    // Resistivity (Ω·m), and the cross-section (m²): AWG's diameter is 0.127 mm · 92^((36 − AWG) / 39)
    val rho = if (aluminium) 2.82e-8 else 1.68e-8
    val area = when {
        size == null -> null
        awg -> PI * (0.127 * 92.0.pow((36 - size) / 39.0) / 2000.0).pow(2)
        size > 0 -> size * 1e-6
        else -> null
    }
    // There and back
    val resistance = if (area != null && len != null) 2 * rho * len / area else null

    CalculatorPage(
        stringResource(R.string.fp_voltage_drop),
        formulas = listOf(
            Formula("""R = \frac{2 · ρ · l}{A}""", stringResource(R.string.ec_resistance)),
            Formula("""ΔU = I · R""", stringResource(R.string.ec_drop)),
            Formula("""\frac{ΔU}{U} · 100 %""", stringResource(R.string.ec_drop_pct)),
            Formula("""P = I · ΔU""", stringResource(R.string.ec_loss)),
            Formula("""ρ = 1.68 · 10^{−8} \text{ Ω·m}""", stringResource(R.string.ec_copper)),
            Formula("""ρ = 2.82 · 10^{−8} \text{ Ω·m}""", stringResource(R.string.ec_aluminium)),
            Formula("""d = 0.127 \text{ mm} · 92^{(36 − n)/39}""", "AWG"),
            Formula("""A = \frac{π · d^{2}}{4}"""),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_material),
            icon = Icons.Default.Layers,
            selected = aluminium,
            options = listOf(false to stringResource(R.string.ec_copper), true to stringResource(R.string.ec_aluminium)),
            onSelect = { aluminium = it }
        )
        UnitChoiceField(stringResource(R.string.ec_wire_size), sizeText, { sizeText = it }, if (awg) "AWG" else "mm²", listOf("mm²", "AWG")) {
            if ((it == "AWG") != awg) {
                awg = it == "AWG"
                sizeText = if (awg) "14" else "1.5"
            }
        }
        UnitField(stringResource(R.string.ec_length), lenText, { lenText = it }, "m")
        UnitField(stringResource(R.string.ec_voltage), vText, { vText = it }, "V")
        UnitField(stringResource(R.string.ec_current), iText, { iText = it }, "A")

        Results {
            if (resistance != null && current != null) {
                val drop = resistance * current
                MainResult(stringResource(R.string.ec_drop), formatValue(drop, "V"))
                if (v != null && v > 0) {
                    ResultRow(stringResource(R.string.ec_drop_pct), formatPercent(drop / v * 100.0))
                    ResultRow(stringResource(R.string.ec_v_load), formatValue(v - drop, "V"))
                }
                ResultRow(stringResource(R.string.ec_resistance), formatValue(resistance, "Ω"))
                ResultRow(stringResource(R.string.ec_loss), formatValue(drop * current, "W"))
            }
        }
    }
}
