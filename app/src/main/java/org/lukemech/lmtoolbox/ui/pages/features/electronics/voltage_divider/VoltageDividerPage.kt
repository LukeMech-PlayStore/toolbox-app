package org.lukemech.lmtoolbox.ui.pages.features.electronics.voltage_divider

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.fieldText
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.ESeriesSetting
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Nearest
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Divider { Vin, R1, R2, Vout }

@Composable
fun VoltageDividerPage() {
    val fields = rememberLinkedFields(3, Divider.Vin to "5", Divider.R1 to "10 k", Divider.R2 to "10 k")
    var rlText by rememberSaveable { mutableStateOf("100 k") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var series by remember { mutableStateOf(ESeries.E24) }

    // The load is in parallel with R2, so R2 in effect is R2·RL / (R2 + RL), and back
    val rl = parseValue(rlText)?.takeIf { loaded && it > 0 }
    fun withLoad(r2: Double) = if (rl == null) r2 else r2 * rl / (r2 + rl)
    fun withoutLoad(r2e: Double) = if (rl == null) r2e else (r2e * rl / (rl - r2e)).takeIf { r2e < rl } ?: Double.NaN

    // Vout = Vin · R2 / (R1 + R2) with R2 in effect, any of them from the other three
    val values = fields.solve { given ->
        val vin = given[Divider.Vin]
        val r1 = given[Divider.R1]
        val r2e = given[Divider.R2]?.let(::withLoad)
        val vout = given[Divider.Vout]
        when {
            vin != null && r1 != null && r2e != null -> mapOf(Divider.Vout to vin * r2e / (r1 + r2e))
            r1 != null && r2e != null && vout != null -> mapOf(Divider.Vin to vout * (r1 + r2e) / r2e)
            vin != null && r2e != null && vout != null -> mapOf(Divider.R1 to r2e * (vin - vout) / vout)
            vin != null && r1 != null && vout != null -> mapOf(Divider.R2 to withoutLoad(vout * r1 / (vin - vout)))
            else -> emptyMap()
        }.filterValues { it > 0 }
    }

    CalculatorPage(
        stringResource(R.string.fp_voltage_divider),
        formulas = listOf(
            Formula("""U_{out} = U_{in} · \frac{R_{2}}{R_{1} + R_{2}}""", stringResource(R.string.ec_vout)),
            Formula("""R_{2}' = \frac{R_{2} · R_{L}}{R_{2} + R_{L}}""", stringResource(R.string.ec_load)),
            Formula("""I = \frac{U_{in} − U_{out}}{R_{1}}""", stringResource(R.string.ec_current)),
        )
    ) {
        LinkedField(fields, values, Divider.Vin, stringResource(R.string.ec_vin), "V")
        LinkedField(fields, values, Divider.R1, stringResource(R.string.ec_r1), "Ω")
        LinkedField(fields, values, Divider.R2, stringResource(R.string.ec_r2), "Ω")
        ESeriesSetting(
            series,
            listOf(
                Nearest(values[Divider.R1], stringResource(R.string.ec_r1)) { fields.edit(Divider.R1, fieldText(it, "Ω")) },
                Nearest(values[Divider.R2], stringResource(R.string.ec_r2)) { fields.edit(Divider.R2, fieldText(it, "Ω")) },
            )
        ) { series = it }
        LinkedField(fields, values, Divider.Vout, stringResource(R.string.ec_vout), "V")
        SwitchButton(stringResource(R.string.ec_load), Icons.Default.Power, loaded) { loaded = it }
        RevealSection(loaded) { UnitField(stringResource(R.string.ec_rl), rlText, { rlText = it }, "Ω") }

        Results {
            val vin = values[Divider.Vin]
            val r1 = values[Divider.R1]
            val vout = values[Divider.Vout]
            if (vin != null && r1 != null && vout != null) {
                if (vin != 0.0) ResultRow(stringResource(R.string.ec_ratio), String.format(Locale.US, "%.4f", vout / vin))
                ResultRow(stringResource(R.string.ec_current), formatValue((vin - vout) / r1, "A"))
            }
        }
    }
}
