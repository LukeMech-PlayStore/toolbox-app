package org.lukemech.lmtoolbox.ui.pages.features.electronics.capacitor_charge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.exp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.LinkedField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.rememberLinkedFields

private enum class Rc { R, C, Tau }

@Composable
fun CapacitorChargePage() {
    val fields = rememberLinkedFields(2, Rc.R to "10 k", Rc.C to "100 μ")
    var vText by rememberSaveable { mutableStateOf("5") }
    var tText by rememberSaveable { mutableStateOf("1") }

    // τ = R·C
    val values = fields.solve { given ->
        val r = given[Rc.R]
        val c = given[Rc.C]
        val tau = given[Rc.Tau]
        when {
            r != null && c != null -> mapOf(Rc.Tau to r * c)
            r != null && tau != null -> mapOf(Rc.C to tau / r)
            c != null && tau != null -> mapOf(Rc.R to tau / c)
            else -> emptyMap()
        }
    }
    val r = values[Rc.R]?.takeIf { it > 0 }
    val c = values[Rc.C]?.takeIf { it > 0 }
    val tau = values[Rc.Tau]?.takeIf { it > 0 }
    val v = parseValue(vText)
    val t = parseValue(tText)

    CalculatorPage(
        stringResource(R.string.fp_capacitor_charge),
        formulas = listOf(
            Formula("""τ = R · C""", stringResource(R.string.ec_time_const)),
            Formula("""U_{C} = U · (1 − \text{e}^{−t/τ})""", stringResource(R.string.ec_vcap)),
            Formula("""I = \frac{U}{R} · \text{e}^{−t/τ}""", stringResource(R.string.ec_current)),
            Formula("""Q = C · U_{C}""", stringResource(R.string.ec_charge)),
            Formula("""t_{full} ≈ 5τ""", stringResource(R.string.ec_full_charge)),
        )
    ) {
        LinkedField(fields, values, Rc.R, stringResource(R.string.ec_resistance), "Ω")
        LinkedField(fields, values, Rc.C, stringResource(R.string.ec_capacitance), "F")
        LinkedField(fields, values, Rc.Tau, stringResource(R.string.ec_time_const), "s")
        UnitField(stringResource(R.string.ec_voltage), vText, { vText = it }, "V")
        UnitField(stringResource(R.string.ec_time), tText, { tText = it }, "s")

        Results {
            if (tau != null) ResultRow(stringResource(R.string.ec_full_charge), formatValue(5 * tau, "s"))
            if (r != null && c != null && tau != null && t != null && v != null) {
                val vc = v * (1.0 - exp(-t / tau))
                MainResult(stringResource(R.string.ec_vcap), formatValue(vc, "V"))
                ResultRow(stringResource(R.string.ec_current), formatValue(v / r * exp(-t / tau), "A"))
                ResultRow(stringResource(R.string.ec_charge), formatValue(c * vc, "C"))
            }
        }
    }
}
