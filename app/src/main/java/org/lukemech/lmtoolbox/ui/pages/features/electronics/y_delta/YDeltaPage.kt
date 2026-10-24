package org.lukemech.lmtoolbox.ui.pages.features.electronics.y_delta

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.features.electronics.common.parseValue
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.CalculatorPage
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.Formula
import org.lukemech.lmtoolbox.ui.pages.features.electronics.common.UnitField

@Composable
fun YDeltaPage() {
    var toDelta by rememberSaveable { mutableStateOf(true) }
    var aText by rememberSaveable { mutableStateOf("100") }
    var bText by rememberSaveable { mutableStateOf("200") }
    var cText by rememberSaveable { mutableStateOf("300") }

    val a = parseValue(aText)
    val b = parseValue(bText)
    val c = parseValue(cText)

    val inputs = if (toDelta) listOf(R.string.ec_ra, R.string.ec_rb, R.string.ec_rc) else listOf(R.string.ec_rab, R.string.ec_rbc, R.string.ec_rca)

    CalculatorPage(
        stringResource(R.string.fp_y_delta),
        formulas = listOf(
            Formula("""R_{ab} = \frac{R_{a}R_{b} + R_{b}R_{c} + R_{c}R_{a}}{R_{c}}""", stringResource(R.string.ec_to_delta)),
            Formula("""R_{bc} = \frac{R_{a}R_{b} + R_{b}R_{c} + R_{c}R_{a}}{R_{a}}"""),
            Formula("""R_{ca} = \frac{R_{a}R_{b} + R_{b}R_{c} + R_{c}R_{a}}{R_{b}}"""),
            Formula("""R_{a} = \frac{R_{ab} · R_{ca}}{R_{ab} + R_{bc} + R_{ca}}""", stringResource(R.string.ec_to_star)),
            Formula("""R_{b} = \frac{R_{ab} · R_{bc}}{R_{ab} + R_{bc} + R_{ca}}"""),
            Formula("""R_{c} = \frac{R_{bc} · R_{ca}}{R_{ab} + R_{bc} + R_{ca}}"""),
        )
    ) {
        OptionSetting(
            title = stringResource(R.string.ec_mode),
            icon = Icons.Default.SwapHoriz,
            selected = toDelta,
            options = listOf(true to stringResource(R.string.ec_to_delta), false to stringResource(R.string.ec_to_star)),
            onSelect = { toDelta = it }
        )
        UnitField(stringResource(inputs[0]), aText, { aText = it }, "Ω")
        UnitField(stringResource(inputs[1]), bText, { bText = it }, "Ω")
        UnitField(stringResource(inputs[2]), cText, { cText = it }, "Ω")

        Results {
            if (a != null && b != null && c != null && a > 0 && b > 0 && c > 0) {
                if (toDelta) {
                    // Ra, Rb, Rc from the nodes to the center; Rab between nodes a and b...
                    val p = a * b + b * c + c * a
                    MainResult(stringResource(R.string.ec_rab), formatValue(p / c, "Ω"))
                    MainResult(stringResource(R.string.ec_rbc), formatValue(p / a, "Ω"))
                    MainResult(stringResource(R.string.ec_rca), formatValue(p / b, "Ω"))
                } else {
                    val s = a + b + c
                    MainResult(stringResource(R.string.ec_ra), formatValue(c * a / s, "Ω"))
                    MainResult(stringResource(R.string.ec_rb), formatValue(a * b / s, "Ω"))
                    MainResult(stringResource(R.string.ec_rc), formatValue(b * c / s, "Ω"))
                }
            }
        }
    }
}
