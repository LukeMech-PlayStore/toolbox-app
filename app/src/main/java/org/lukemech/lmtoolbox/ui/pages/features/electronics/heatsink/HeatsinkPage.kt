package org.lukemech.lmtoolbox.ui.pages.features.electronics.heatsink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField

@Composable
fun HeatsinkPage() {
    var pText by rememberSaveable { mutableStateOf("1") }
    var tAmbText by rememberSaveable { mutableStateOf("25") }
    var tMaxText by rememberSaveable { mutableStateOf("125") }
    var rthJaText by rememberSaveable { mutableStateOf("62") }

    val p = parseValue(pText)
    val tAmb = parseValue(tAmbText)
    val tMax = parseValue(tMaxText)
    val rthJa = parseValue(rthJaText)

    CalculatorPage(
        stringResource(R.string.fp_heatsink),
        formulas = listOf(
            Formula("""T_{j} = T_{a} + P · R_{thJA}""", stringResource(R.string.ec_t_junction)),
            Formula("""P_{max} = \frac{T_{max} − T_{a}}{R_{thJA}}""", stringResource(R.string.ec_p_max)),
            Formula("""R_{thJC} + R_{thCS} + R_{thSA} ≤ \frac{T_{max} − T_{a}}{P}""", stringResource(R.string.ec_rth_needed)),
        )
    ) {
        UnitField(stringResource(R.string.ec_power), pText, { pText = it }, "W")
        UnitField(stringResource(R.string.ec_t_amb), tAmbText, { tAmbText = it }, "°C")
        UnitField(stringResource(R.string.ec_t_max), tMaxText, { tMaxText = it }, "°C")
        UnitField(stringResource(R.string.ec_rth_ja), rthJaText, { rthJaText = it }, "°C/W")

        val tJunction = if (p != null && tAmb != null && rthJa != null && rthJa > 0) tAmb + p * rthJa else null
        Results {
            if (tJunction != null && p != null && tAmb != null && tMax != null && rthJa != null) {
                MainResult(stringResource(R.string.ec_t_junction), String.format(Locale.US, "%.1f °C", tJunction))
                ResultRow(stringResource(R.string.ec_p_max), formatValue((tMax - tAmb) / rthJa, "W"))
                // What junction-case, case-heatsink and the heatsink may add up to together
                if (tJunction > tMax && p > 0) MainResult(stringResource(R.string.ec_rth_needed), String.format(Locale.US, "%.1f °C/W", (tMax - tAmb) / p))
            }
        }
        Note(stringResource(R.string.ec_no_heatsink), visible = tJunction != null && tMax != null && tJunction <= tMax)
    }
}
